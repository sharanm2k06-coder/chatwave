import { z } from 'zod';

export const register = z.object({
  username: z.string().trim().toLowerCase().regex(/^[a-z0-9_]{3,30}$/, '3-30 chars: a-z, 0-9, underscore'),
  password: z.string().min(8).max(128),
  displayName: z.string().trim().min(1).max(50),
});
export const login = z.object({ username: z.string().trim().toLowerCase().max(30), password: z.string().min(1).max(128) });
export const refresh = z.object({ refreshToken: z.string().min(20).max(200) });
export const search = z.object({ q: z.string().trim().min(2).max(30) });
export const uuid = z.string().uuid();
export const direct = z.object({ userId: z.string().uuid() });
export const history = z.object({
  before: z.coerce.number().int().positive().optional(),
  limit: z.coerce.number().int().min(1).max(100).default(30),
});
export const sendMessage = z.object({
  conversationId: z.string().uuid(), clientMessageId: z.string().uuid(), body: z.string().trim().min(1).max(4000),
});
export const ids = z.object({ messageIds: z.array(z.string().uuid()).min(1).max(200) });
export const readUpTo = z.object({ conversationId: z.string().uuid(), upToSeq: z.number().int().positive() });
export const typing = z.object({ conversationId: z.string().uuid() });
export const profileUpdate = z.object({
  username: z.string().trim().toLowerCase().regex(/^[a-z0-9_]{3,30}$/),
  displayName: z.string().trim().min(1).max(50),
});
export const groupCreate = z.object({
  name: z.string().trim().min(1).max(80),
  userIds: z.array(z.string().uuid()).max(100).default([]),
});
export const memberChange = z.object({ userId: z.string().uuid() });
