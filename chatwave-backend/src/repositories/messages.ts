import { pool, tx } from '../database/pool';

export interface MessageRow { id: string; seq: string; conversation_id: string; sender_id: string; client_message_id: string; body: string; created_at: Date }

export const toDto = (m: MessageRow, receipts: { userId: string; deliveredAt: Date | null; readAt: Date | null }[] = []) => ({
  id: m.id, seq: Number(m.seq), conversationId: m.conversation_id, senderId: m.sender_id,
  clientMessageId: m.client_message_id, body: m.body, createdAt: m.created_at,
  status: receipts.length && receipts.every(r => r.readAt) ? 'read' : receipts.length && receipts.every(r => r.deliveredAt) ? 'delivered' : 'sent',
});

const PRIVATE_LIMIT_BYTES = 10 * 1024 * 1024;

async function trimPrivateConversation(c: import('pg').PoolClient, convId: string) {
  await c.query(
    `WITH ordered AS (
       SELECT id, sum(octet_length(body)) OVER (ORDER BY seq DESC) AS newest_bytes
       FROM messages WHERE conversation_id=$1 AND deleted_at IS NULL
     )
     DELETE FROM messages WHERE conversation_id=$1
       AND id IN (SELECT id FROM ordered WHERE newest_bytes > $2)`,
    [convId, PRIVATE_LIMIT_BYTES]);
}

export async function purgeExpired() {
  await pool.query(
    `DELETE FROM messages WHERE conversation_id IN
       (SELECT id FROM conversations WHERE type IN ('global','group'))
       AND created_at < now() - interval '24 hours'`);
  const { rows } = await pool.query<{ id: string }>(
    `SELECT id FROM conversations WHERE type='direct'`);
  for (const row of rows) {
    const c = await pool.connect();
    try { await c.query('BEGIN'); await trimPrivateConversation(c, row.id); await c.query('COMMIT'); }
    catch (e) { await c.query('ROLLBACK'); throw e; }
    finally { c.release(); }
  }
}

/** Inserts the message and recipient receipts in one transaction. Idempotent per (sender, clientMessageId). */
export async function insertIdempotent(convId: string, senderId: string, clientMessageId: string, body: string, recipients: string[]) {
  return tx(async c => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [convId]);
    const kind = (await c.query<{ type: string }>('SELECT type FROM conversations WHERE id=$1', [convId])).rows[0]?.type;
    if (kind === 'global' || kind === 'group')
      await c.query(`DELETE FROM messages WHERE conversation_id=$1 AND created_at < now() - interval '24 hours'`, [convId]);
    if (kind === 'direct') await trimPrivateConversation(c, convId);
    const ins = await c.query<MessageRow>(
      `INSERT INTO messages (conversation_id, sender_id, client_message_id, body) VALUES ($1,$2,$3,$4)
       ON CONFLICT (sender_id, client_message_id) DO NOTHING RETURNING *`, [convId, senderId, clientMessageId, body]);
    if (ins.rows[0]) {
      if (kind === 'direct') await trimPrivateConversation(c, convId);
      for (const r of recipients)
        await c.query('INSERT INTO message_receipts (message_id, user_id) VALUES ($1,$2)', [ins.rows[0].id, r]);
      return { message: ins.rows[0], duplicate: false };
    }
    const existing = (await c.query<MessageRow>(
      'SELECT * FROM messages WHERE sender_id=$1 AND client_message_id=$2', [senderId, clientMessageId])).rows[0];
    return { message: existing, duplicate: true };
  });
}

export async function history(convId: string, beforeSeq: number | null, limit: number) {
  const { rows } = await pool.query<MessageRow>(
    `SELECT * FROM messages WHERE conversation_id=$1 AND deleted_at IS NULL AND ($2::bigint IS NULL OR seq < $2)
     ORDER BY seq DESC LIMIT $3`, [convId, beforeSeq, limit + 1]);
  const page = rows.slice(0, limit);
  const ids = page.map(m => m.id);
  const rec = ids.length
    ? (await pool.query('SELECT message_id, user_id, delivered_at, read_at FROM message_receipts WHERE message_id = ANY($1::uuid[])', [ids])).rows
    : [];
  const items = page.map(m => toDto(m, rec.filter(r => r.message_id === m.id)
    .map(r => ({ userId: r.user_id, deliveredAt: r.delivered_at, readAt: r.read_at }))));
  return { items, nextBefore: rows.length > limit ? Number(page[page.length - 1].seq) : null };
}

export async function markDelivered(userId: string, messageIds: string[]) {
  const { rows } = await pool.query<{ message_id: string; sender_id: string; conversation_id: string }>(
    `UPDATE message_receipts r SET delivered_at = COALESCE(r.delivered_at, now())
       FROM messages m WHERE r.message_id = m.id AND r.user_id = $1 AND r.message_id = ANY($2::uuid[])
     RETURNING r.message_id, m.sender_id, m.conversation_id`, [userId, messageIds]);
  return rows;
}

/** Marks everything in the conversation up to upToSeq as read (and delivered) for this user. */
export async function markRead(userId: string, convId: string, upToSeq: number) {
  const { rows } = await pool.query<{ message_id: string; sender_id: string }>(
    `UPDATE message_receipts r SET read_at = COALESCE(r.read_at, now()), delivered_at = COALESCE(r.delivered_at, now())
       FROM messages m WHERE r.message_id = m.id AND r.user_id = $1 AND m.conversation_id = $2
        AND m.seq <= $3 AND r.read_at IS NULL
     RETURNING r.message_id, m.sender_id`, [userId, convId, upToSeq]);
  return rows;
}
