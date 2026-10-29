import { randomUUID } from 'node:crypto';
import http from 'node:http';
import { AddressInfo } from 'node:net';
import { io as client, Socket } from 'socket.io-client';
import request from 'supertest';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { migrate } from '../src/database/migrate';
import { attachSockets } from '../src/sockets';
import { app, authed, signup } from './helpers';

let server: http.Server; let url: string;
beforeAll(async () => {
  await migrate();
  server = http.createServer(app); attachSockets(server);
  await new Promise<void>(r => server.listen(0, r));
  url = `http://localhost:${(server.address() as AddressInfo).port}`;
});
afterAll(() => { server.close(); });

const connect = (token: string) => new Promise<Socket>((res, rej) => {
  const s = client(url, { auth: { token }, transports: ['websocket'], reconnection: false });
  s.on('connect', () => res(s)); s.on('connect_error', rej);
});
const emitAck = (s: Socket, evt: string, payload: unknown) => new Promise<any>(r => s.emit(evt, payload, r));
const once = (s: Socket, evt: string) => new Promise<any>(r => s.once(evt, r));

async function pair() {
  const a = await signup(); const b = await signup();
  const c = await request(app).post('/api/v1/chats/direct').set(authed(a.accessToken)).send({ userId: b.user.id });
  return { a, b, convId: c.body.id as string };
}

describe('direct chat', () => {
  it('finds users and prevents duplicate direct conversations', async () => {
    const { a, b, convId } = await pair();
    const s = await request(app).get('/api/v1/users/search').query({ q: b.username.slice(0, 6) }).set(authed(a.accessToken));
    expect(s.body.items.some((u: any) => u.id === b.user.id)).toBe(true);
    const again = await request(app).post('/api/v1/chats/direct').set(authed(b.accessToken)).send({ userId: a.user.id });
    expect(again.body.id).toBe(convId);
  });
  it('rejects sockets without a valid token', async () => {
    await expect(connect('garbage')).rejects.toBeTruthy();
  });
  it('persists, delivers, dedupes and tracks receipts', async () => {
    const { a, b, convId } = await pair();
    const sa = await connect(a.accessToken); const sb = await connect(b.accessToken);
    const incoming = once(sb, 'message:new');
    const clientMessageId = randomUUID();
    const ack = await emitAck(sa, 'message:send', { conversationId: convId, clientMessageId, body: 'hello' });
    expect(ack.ok).toBe(true); expect(ack.duplicate).toBe(false);
    expect((await incoming).body).toBe('hello');
    const retry = await emitAck(sa, 'message:send', { conversationId: convId, clientMessageId, body: 'hello' });
    expect(retry.duplicate).toBe(true); expect(retry.message.id).toBe(ack.message.id);

    const delivered = once(sa, 'message:delivered');
    await emitAck(sb, 'message:delivered', { messageIds: [ack.message.id] });
    await delivered;
    const read = once(sa, 'message:read');
    await emitAck(sb, 'message:read', { conversationId: convId, upToSeq: ack.message.seq });
    await read;

    const hist = await request(app).get(`/api/v1/chats/${convId}/messages`).set(authed(a.accessToken));
    expect(hist.body.items).toHaveLength(1);
    expect(hist.body.items[0].status).toBe('read');
    sa.close(); sb.close();
  });
  it('paginates history with a cursor', async () => {
    const { a, convId } = await pair();
    const sa = await connect(a.accessToken);
    for (let i = 0; i < 5; i++) await emitAck(sa, 'message:send', { conversationId: convId, clientMessageId: randomUUID(), body: `m${i}` });
    const p1 = await request(app).get(`/api/v1/chats/${convId}/messages`).query({ limit: 3 }).set(authed(a.accessToken));
    expect(p1.body.items.map((m: any) => m.body)).toEqual(['m4', 'm3', 'm2']);
    const p2 = await request(app).get(`/api/v1/chats/${convId}/messages`).query({ limit: 3, before: p1.body.nextBefore }).set(authed(a.accessToken));
    expect(p2.body.items.map((m: any) => m.body)).toEqual(['m1', 'm0']);
    expect(p2.body.nextBefore).toBeNull();
    sa.close();
  });
  it('blocks non-members from history and from sending', async () => {
    const { a, convId } = await pair();
    const outsider = await signup();
    const h = await request(app).get(`/api/v1/chats/${convId}/messages`).set(authed(outsider.accessToken));
    expect(h.status).toBe(403);
    const so = await connect(outsider.accessToken);
    const ack = await emitAck(so, 'message:send', { conversationId: convId, clientMessageId: randomUUID(), body: 'sneaky' });
    expect(ack.ok).toBe(false);
    so.close(); void a;
  });
});
