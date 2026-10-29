import crypto from 'node:crypto';
import argon2 from 'argon2';
import { env } from '../config/env';
import { pool, tx } from '../database/pool';
import { conflict, unauthorized } from '../utils/errors';
import { hashToken, newRefreshToken, signAccessToken } from '../utils/tokens';
import * as users from '../repositories/users';
import * as sessions from '../repositories/sessions';

const ARGON = { type: argon2.argon2id, memoryCost: 19456, timeCost: 2, parallelism: 1 } as const;
let dummyHash: Promise<string> | undefined;

async function issue(userId: string, familyId = crypto.randomUUID(), label?: string) {
  const refreshToken = newRefreshToken();
  const row = await sessions.create(pool, userId, familyId, hashToken(refreshToken), env.REFRESH_TOKEN_TTL_DAYS, label);
  return { accessToken: signAccessToken(userId, row.id), refreshToken, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS, sessionId: row.id };
}

export async function register(username: string, password: string, displayName: string) {
  if (await users.findByUsername(username)) throw conflict('Username already taken');
  let user;
  try { user = await users.insertUser(username, displayName, await argon2.hash(password, ARGON)); }
  catch (e: any) { if (e?.code === '23505') throw conflict('Username already taken'); throw e; }
  await pool.query(
    'INSERT INTO conversation_members (conversation_id, user_id) VALUES ($1,$2) ON CONFLICT DO NOTHING',
    ['00000000-0000-0000-0000-000000000001', user.id]);
  const { sessionId: _s, ...tokens } = await issue(user.id);
  return { user: users.publicUser(user), ...tokens };
}

export async function login(username: string, password: string) {
  const user = await users.findByUsername(username);
  dummyHash ??= argon2.hash('dummy-password-for-timing', ARGON);
  const ok = await argon2.verify(user?.password_hash ?? (await dummyHash), password).catch(() => false);
  if (!user || !ok) throw unauthorized('Invalid username or password');
  const { sessionId: _s, ...tokens } = await issue(user.id);
  return { user: users.publicUser(user), ...tokens };
}

/** Rotates the refresh token. Presenting an already-used token revokes the whole token family. */
export async function refresh(refreshToken: string) {
  const hash = hashToken(refreshToken);
  const result = await tx(async c => {
    const row = await sessions.findByHash(c, hash);
    if (!row) return { reuse: false as const, tokens: null };
    if (row.revoked_at) { await sessions.revokeFamily(c, row.family_id); return { reuse: true as const, tokens: null }; }
    if (row.expires_at.getTime() < Date.now()) return { reuse: false as const, tokens: null };
    const next = newRefreshToken();
    const created = await sessions.create(c, row.user_id, row.family_id, hashToken(next), env.REFRESH_TOKEN_TTL_DAYS);
    await sessions.revoke(c, row.id, created.id);
    return { reuse: false as const, tokens: { accessToken: signAccessToken(row.user_id, created.id), refreshToken: next, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS } };
  });
  if (!result.tokens) throw unauthorized('Invalid refresh token');
  return result.tokens;
}

export async function logout(refreshToken: string) {
  await tx(async c => {
    const row = await sessions.findByHash(c, hashToken(refreshToken));
    if (row) await sessions.revokeFamily(c, row.family_id);
  });
}
