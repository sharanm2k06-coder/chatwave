import { pool } from '../database/pool';

export interface UserRow { id: string; username: string; display_name: string; password_hash: string; photo_url: string | null }
export const publicUser = (u: UserRow) => ({ id: u.id, username: u.username, displayName: u.display_name, photoUrl: u.photo_url });

export const findByUsername = async (username: string) =>
  (await pool.query<UserRow>('SELECT * FROM users WHERE username = $1', [username])).rows[0];
export const findById = async (id: string) =>
  (await pool.query<UserRow>('SELECT * FROM users WHERE id = $1', [id])).rows[0];
export const insertUser = async (username: string, displayName: string, hash: string) =>
  (await pool.query<UserRow>(
    'INSERT INTO users (username, display_name, password_hash) VALUES ($1,$2,$3) RETURNING *',
    [username, displayName, hash])).rows[0];

export async function search(q: string, selfId: string, limit = 20) {
  const like = q.toLowerCase().replace(/[\\%_]/g, m => '\\' + m) + '%';
  return (await pool.query<UserRow>(
    `SELECT * FROM users WHERE id <> $1 AND (username LIKE $2 OR lower(display_name) LIKE $2)
     ORDER BY username LIMIT $3`, [selfId, like, limit])).rows;
}
