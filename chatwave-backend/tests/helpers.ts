import { randomUUID } from 'node:crypto';
import request from 'supertest';
import { createApp } from '../src/app';

export const app = createApp();
export const uname = () => 'u' + randomUUID().replace(/-/g, '').slice(0, 12);

export async function signup(name = uname(), password = 'correct-horse-battery') {
  const r = await request(app).post('/api/v1/auth/register').send({ username: name, password, displayName: name });
  return { ...r.body, username: name, password, status: r.status } as any;
}
export const authed = (t: string) => ({ Authorization: `Bearer ${t}` });
