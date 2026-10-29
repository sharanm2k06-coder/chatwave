import { Server as HttpServer } from 'node:http';
import { Server, Socket } from 'socket.io';
import { env } from '../config/env';
import { authenticate } from '../middleware/auth';
import * as v from '../validators/schemas';
import * as chat from '../services/chat';
import * as chats from '../repositories/chats';
import * as messages from '../repositories/messages';
import { logger } from '../utils/logger';

type Ack = (r: unknown) => void;
const userRoom = (id: string) => `user:${id}`;

export function attachSockets(http: HttpServer) {
  const io = new Server(http, {
    cors: { origin: env.CORS_ORIGINS ? env.CORS_ORIGINS.split(',') : false },
    transports: ['websocket', 'polling'],
    maxHttpBufferSize: 64 * 1024,
  });
  const connections = new Map<string, number>(); // userId -> live connection count

  io.use(async (socket, next) => {
    try {
      const t = socket.handshake.auth?.token;
      const a = await authenticate(typeof t === 'string' ? t : undefined);
      socket.data.userId = a.userId;
      next();
    } catch { next(new Error('unauthorized')); }
  });

  const guard = <T>(socket: Socket, schema: { parse(x: unknown): T }, ack: Ack | undefined,
    fn: (userId: string, p: T) => Promise<unknown>) => async (raw: unknown, maybeAck?: Ack) => {
    const reply = typeof maybeAck === 'function' ? maybeAck : ack;
    try {
      const out = await fn(socket.data.userId, schema.parse(raw));
      reply?.({ ok: true, ...(out as object ?? {}) });
    } catch (e: any) {
      reply?.({ ok: false, error: { code: e?.code ?? 'ERROR', message: e?.status ? e.message : 'Invalid request' } });
      if (!e?.status && e?.name !== 'ZodError') logger.error({ err: e }, 'socket handler error');
    }
  };

  io.on('connection', async socket => {
    const uid: string = socket.data.userId;
    socket.join(userRoom(uid));
    const n = (connections.get(uid) ?? 0) + 1;
    connections.set(uid, n);
    if (n === 1) io.emit('presence:update', { userId: uid, online: true });

    socket.on('message:send', guard(socket, v.sendMessage, undefined, async (userId, p) => {
      const { message, duplicate, recipients } = await chat.sendMessage(userId, p.conversationId, p.clientMessageId, p.body);
      if (!duplicate) {
        for (const r of recipients) io.to(userRoom(r)).emit('message:new', message);
        io.to(userRoom(userId)).except(socket.id).emit('message:new', message);
      }
      // ack is sent only after the DB transaction committed
      return { message, duplicate };
    }));

    socket.on('message:delivered', guard(socket, v.ids, undefined, async (userId, p) => {
      const rows = await messages.markDelivered(userId, p.messageIds);
      for (const r of rows) io.to(userRoom(r.sender_id)).emit('message:delivered', { messageId: r.message_id, conversationId: r.conversation_id, userId });
      return {};
    }));

    socket.on('message:read', guard(socket, v.readUpTo, undefined, async (userId, p) => {
      await chat.assertMember(p.conversationId, userId);
      const rows = await messages.markRead(userId, p.conversationId, p.upToSeq);
      for (const r of rows) io.to(userRoom(r.sender_id)).emit('message:read', { messageId: r.message_id, conversationId: p.conversationId, userId });
      return {};
    }));

    for (const evt of ['typing:start', 'typing:stop'] as const) {
      socket.on(evt, guard(socket, v.typing, undefined, async (userId, p) => {
        await chat.assertMember(p.conversationId, userId);
        for (const m of await chats.memberIds(p.conversationId))
          if (m !== userId) io.to(userRoom(m)).emit(evt, { conversationId: p.conversationId, userId });
        return {};
      }));
    }

    socket.on('disconnect', () => {
      const left = (connections.get(uid) ?? 1) - 1;
      if (left <= 0) { connections.delete(uid); io.emit('presence:update', { userId: uid, online: false }); }
      else connections.set(uid, left);
    });
  });
  return io;
}
