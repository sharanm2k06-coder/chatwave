import cors from 'cors';
import express from 'express';
import helmet from 'helmet';
import { env } from './config/env';
import { pool } from './database/pool';
import { errorHandler } from './middleware/error';
import { api } from './routes';

export function createApp() {
  const app = express();
  app.set('trust proxy', 1); // Render terminates TLS at its proxy
  app.use(helmet());
  app.use(cors({ origin: env.CORS_ORIGINS ? env.CORS_ORIGINS.split(',') : false }));
  app.use(express.json({ limit: '32kb' }));

  app.get('/health', (_req, res) => res.json({ status: 'ok' }));
  app.get('/ready', async (_req, res) => {
    try { await pool.query('SELECT 1'); res.json({ status: 'ready' }); }
    catch { res.status(503).json({ status: 'database_unavailable' }); }
  });
  app.use('/api/v1', api);
  app.use((_req, res) => res.status(404).json({ error: { code: 'NOT_FOUND', message: 'Not found' } }));
  app.use(errorHandler);
  return app;
}
