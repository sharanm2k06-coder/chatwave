import { PoolClient } from 'pg';
import { pool } from '../database/pool';

export interface TokenRow { id: string; user_id: string; family_id: string; expires_at: Date; revoked_at: Date | null }

export const create = async (c: PoolClient | typeof pool, userId: string, familyId: string, hash: string, ttlDays: number, label?: string) =>
  (await c.query<TokenRow>(
    `INSERT INTO refresh_tokens (user_id, family_id, token_hash, expires_at, device_label)
     VALUES ($1,$2,$3, now() + make_interval(days => $4), $5) RETURNING *`,
    [userId, familyId, hash, ttlDays, label ?? null])).rows[0];

export const findByHash = async (c: PoolClient, hash: string) =>
  (await c.query<TokenRow>('SELECT * FROM refresh_tokens WHERE token_hash = $1 FOR UPDATE', [hash])).rows[0];
export const revoke = (c: PoolClient | typeof pool, id: string, replacedBy?: string) =>
  c.query('UPDATE refresh_tokens SET revoked_at = now(), replaced_by = $2 WHERE id = $1 AND revoked_at IS NULL', [id, replacedBy ?? null]);
export const revokeFamily = (c: PoolClient | typeof pool, familyId: string) =>
  c.query('UPDATE refresh_tokens SET revoked_at = now() WHERE family_id = $1 AND revoked_at IS NULL', [familyId]);
export const isActive = async (id: string, userId: string) =>
  (await pool.query('SELECT 1 FROM refresh_tokens WHERE id = $1 AND user_id = $2 AND revoked_at IS NULL AND expires_at > now()', [id, userId])).rowCount === 1;
