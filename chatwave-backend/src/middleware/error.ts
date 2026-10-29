import { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';
import { AppError } from '../utils/errors';
import { logger } from '../utils/logger';

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  if (err instanceof ZodError)
    return res.status(400).json({ error: { code: 'VALIDATION_ERROR', message: 'Invalid request',
      details: err.issues.map(i => ({ path: i.path.join('.'), message: i.message })) } });
  if (err instanceof AppError)
    return res.status(err.status).json({ error: { code: err.code, message: err.message, details: err.details } });
  logger.error({ err }, 'unhandled error');
  res.status(500).json({ error: { code: 'INTERNAL', message: 'Internal server error' } });
}
