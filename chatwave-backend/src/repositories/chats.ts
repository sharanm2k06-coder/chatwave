import { pool } from '../database/pool';

export const isMember = async (convId: string, userId: string) =>
  (await pool.query('SELECT 1 FROM conversation_members WHERE conversation_id=$1 AND user_id=$2', [convId, userId])).rowCount === 1;

export const isDirectConversation = async (convId: string) =>
  (await pool.query("SELECT 1 FROM conversations WHERE id=$1 AND type='direct'", [convId])).rowCount === 1;

export const memberIds = async (convId: string) =>
  (await pool.query<{ user_id: string }>('SELECT user_id FROM conversation_members WHERE conversation_id=$1', [convId])).rows.map(r => r.user_id);

export const conversationIdsFor = async (userId: string) =>
  (await pool.query<{ conversation_id: string }>('SELECT conversation_id FROM conversation_members WHERE user_id=$1', [userId])).rows.map(r => r.conversation_id);

export async function getOrCreateDirect(a: string, b: string) {
  const key = [a, b].sort().join(':');
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const ins = await c.query<{ id: string }>(
      `INSERT INTO conversations (type, direct_key, created_by) VALUES ('direct',$1,$2)
       ON CONFLICT (direct_key) DO NOTHING RETURNING id`, [key, a]);
    let id = ins.rows[0]?.id;
    if (id) {
      await c.query(`INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1,$2),($1,$3)`, [id, a, b]);
    } else {
      id = (await c.query<{ id: string }>('SELECT id FROM conversations WHERE direct_key=$1', [key])).rows[0].id;
    }

    await c.query('COMMIT');
    return id;
  } catch (e) { await c.query('ROLLBACK'); throw e; } finally { c.release(); }
}

export async function createGroup(ownerId: string, name: string, memberIds: string[]) {
  const c = await pool.connect();
  try {
    await c.query('BEGIN');
    const id = (await c.query<{ id: string }>(
      `INSERT INTO conversations (type, name, created_by) VALUES ('group',$1,$2) RETURNING id`,
      [name, ownerId])).rows[0].id;
    const ids = [...new Set([ownerId, ...memberIds])];
    for (const userId of ids)
      await c.query(
        'INSERT INTO conversation_members (conversation_id,user_id,role) VALUES ($1,$2,$3)',
        [id, userId, userId === ownerId ? 'admin' : 'member']);
    await c.query('COMMIT');
    return id;
  } catch (e) { await c.query('ROLLBACK'); throw e; } finally { c.release(); }
}

export const memberRole = async (convId: string, userId: string) =>
  (await pool.query<{ role: string }>(
    'SELECT role FROM conversation_members WHERE conversation_id=$1 AND user_id=$2', [convId, userId])).rows[0]?.role;

export const addMember = (convId: string, userId: string) =>
  pool.query('INSERT INTO conversation_members (conversation_id,user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING', [convId, userId]);

export const removeMember = (convId: string, userId: string) =>
  pool.query('DELETE FROM conversation_members WHERE conversation_id=$1 AND user_id=$2', [convId, userId]);

export async function listFor(userId: string) {
  const { rows } = await pool.query(
    `SELECT cv.id, cv.type, cv.name,
        peer.id AS peer_id, peer.username AS peer_username, peer.display_name AS peer_display_name, peer.photo_url AS peer_photo,
        lm.id AS lm_id, lm.seq AS lm_seq, lm.body AS lm_body, lm.sender_id AS lm_sender, lm.created_at AS lm_created,
        (SELECT count(*) FROM messages m
           LEFT JOIN message_receipts r ON r.message_id = m.id AND r.user_id = $1
          WHERE m.conversation_id = cv.id AND m.sender_id <> $1 AND m.deleted_at IS NULL AND r.read_at IS NULL)::int AS unread
     FROM conversation_members me
     JOIN conversations cv ON cv.id = me.conversation_id
     LEFT JOIN LATERAL (SELECT u.* FROM conversation_members cm JOIN users u ON u.id = cm.user_id
                        WHERE cv.type='direct' AND cm.conversation_id = cv.id AND cm.user_id <> $1 LIMIT 1) peer ON true
     LEFT JOIN LATERAL (SELECT * FROM messages m WHERE m.conversation_id = cv.id AND m.deleted_at IS NULL
                        ORDER BY seq DESC LIMIT 1) lm ON true
     WHERE me.user_id = $1
     ORDER BY COALESCE(lm.created_at, cv.created_at) DESC`, [userId]);
  return rows.map(r => ({
    id: r.id, type: r.type, name: r.name,
    peer: r.peer_id ? { id: r.peer_id, username: r.peer_username, displayName: r.peer_display_name, photoUrl: r.peer_photo } : null,
    lastMessage: r.lm_id ? { id: r.lm_id, seq: Number(r.lm_seq), body: r.lm_body, senderId: r.lm_sender, createdAt: r.lm_created } : null,
    unreadCount: r.unread,
  }));
}
