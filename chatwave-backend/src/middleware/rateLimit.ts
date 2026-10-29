import rateLimit from 'express-rate-limit';
import { env } from '../config/env';

const mk = (windowMs: number, limit: number) => rateLimit({
  windowMs, limit, standardHeaders: true, legacyHeaders: false,
  skip: () => env.NODE_ENV === 'test',
  message: { error: { code: 'RATE_LIMITED', message: 'Too many requests, try again later' } },
});
export const authLimiter = mk(15 * 60_000, 30);
export const registerLimiter = mk(60 * 60_000, 10);
export const searchLimiter = mk(60_000, 30);
