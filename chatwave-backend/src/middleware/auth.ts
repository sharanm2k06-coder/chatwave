import { NextFunction, Request, Response } from 'express';
import { unauthorized } from '../utils/errors';
import { verifyAccessToken } from '../utils/tokens';
import * as sessions from '../repositories/sessions';

export interface AuthedRequest extends Request { userId: string; sessionId: string }

export async function authenticate(token: string | undefined) {
  if (!token) throw unauthorized();
  let claims;
  try { claims = verifyAccessToken(token); } catch { throw unauthorized('Invalid or expired token'); }
  if (!(await sessions.isActive(claims.sid, claims.sub))) throw unauthorized('Session revoked');
  return { userId: claims.sub, sessionId: claims.sid };
}

export async function requireAuth(req: Request, _res: Response, next: NextFunction) {
  try {
    const h = req.headers.authorization;
    const { userId, sessionId } = await authenticate(h?.startsWith('Bearer ') ? h.slice(7) : undefined);
    Object.assign(req, { userId, sessionId });
    next();
  } catch (e) { next(e); }
}
