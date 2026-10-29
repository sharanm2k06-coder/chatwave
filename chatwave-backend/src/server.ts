import http from 'node:http';
import { createApp } from './app';
import { env } from './config/env';
import { migrate } from './database/migrate';
import { pool } from './database/pool';
import { attachSockets } from './sockets';
import { logger } from './utils/logger';
import { purgeExpired } from './repositories/messages';

async function main() {
  await migrate();
  const cleanup = setInterval(() => purgeExpired().catch(e => logger.error({ err: e }, 'message cleanup failed')), 60 * 60 * 1000);
  cleanup.unref();
  const server = http.createServer(createApp());
  const io = attachSockets(server);
  server.listen(env.PORT, '0.0.0.0', () => logger.info({ port: env.PORT }, 'chatwave backend listening'));

  const shutdown = (sig: string) => {
    logger.info({ sig }, 'shutting down');
    const t = setTimeout(() => process.exit(1), 10_000); t.unref();
    clearInterval(cleanup);
    io.close(() => server.close(async () => { await pool.end(); process.exit(0); }));
  };
  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT', () => shutdown('SIGINT'));
}
main().catch(e => { logger.fatal({ err: e }, 'startup failed'); process.exit(1); });
