import request from 'supertest';
import { beforeAll, describe, expect, it } from 'vitest';
import { migrate } from '../src/database/migrate';
import { app, authed, signup, uname } from './helpers';

beforeAll(async () => { await migrate(); });

describe('auth', () => {
  it('rejects invalid registration input', async () => {
    const r = await request(app).post('/api/v1/auth/register').send({ username: 'A!', password: 'short', displayName: '' });
    expect(r.status).toBe(400);
    expect(r.body.error.code).toBe('VALIDATION_ERROR');
  });
  it('registers, rejects duplicate usernames, logs in', async () => {
    const a = await signup();
    expect(a.status).toBe(201);
    expect((await signup(a.username)).status).toBe(409);
    const l = await request(app).post('/api/v1/auth/login').send({ username: a.username, password: a.password });
    expect(l.status).toBe(200);
    expect(l.body.user.password_hash).toBeUndefined();
  });
  it('gives a generic error for wrong password and unknown user', async () => {
    const a = await signup();
    const w = await request(app).post('/api/v1/auth/login').send({ username: a.username, password: 'wrong-password-x' });
    const u = await request(app).post('/api/v1/auth/login').send({ username: uname(), password: 'wrong-password-x' });
    expect(w.status).toBe(401); expect(u.status).toBe(401);
    expect(w.body.error.message).toBe(u.body.error.message);
  });
  it('requires a token', async () => {
    expect((await request(app).get('/api/v1/chats')).status).toBe(401);
  });
  it('rotates refresh tokens and revokes the family on reuse', async () => {
    const a = await signup();
    const r1 = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: a.refreshToken });
    expect(r1.status).toBe(200);
    const reuse = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: a.refreshToken });
    expect(reuse.status).toBe(401);
    const afterReuse = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: r1.body.refreshToken });
    expect(afterReuse.status).toBe(401);
    expect((await request(app).get('/api/v1/chats').set(authed(r1.body.accessToken))).status).toBe(401);
  });
  it('logout revokes the session immediately', async () => {
    const a = await signup();
    expect((await request(app).get('/api/v1/chats').set(authed(a.accessToken))).status).toBe(200);
    await request(app).post('/api/v1/auth/logout').send({ refreshToken: a.refreshToken });
    expect((await request(app).get('/api/v1/chats').set(authed(a.accessToken))).status).toBe(401);
  });
});
