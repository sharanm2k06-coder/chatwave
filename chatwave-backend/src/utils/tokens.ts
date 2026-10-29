import crypto from 'node:crypto';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';

export interface AccessClaims { sub: string; sid: string }

export const signAccessToken = (userId: string, sessionId: string) =>
  jwt.sign({ sid: sessionId }, env.JWT_ACCESS_SECRET, {
    subject: userId, expiresIn: env.ACCESS_TOKEN_TTL_SECONDS, algorithm: 'HS256',
  });

export function verifyAccessToken(token: string): AccessClaims {
  const p = jwt.verify(token, env.JWT_ACCESS_SECRET, { algorithms: ['HS256'] }) as jwt.JwtPayload;
  if (typeof p.sub !== 'string' || typeof p.sid !== 'string') throw new Error('bad claims');
  return { sub: p.sub, sid: p.sid };
}

export const newRefreshToken = () => crypto.randomBytes(48).toString('base64url');
export const hashToken = (t: string) => crypto.createHash('sha256').update(t).digest('hex');
