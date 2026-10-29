import { Request, Response } from 'express';
import * as v from '../validators/schemas';
import { AuthedRequest } from '../middleware/auth';
import * as users from '../repositories/users';
import { conflict, notFound } from '../utils/errors';

export const search = async (req: Request, res: Response) => {
  const { q } = v.search.parse(req.query);
  res.json({ items: (await users.search(q, (req as AuthedRequest).userId)).map(users.publicUser) });
};
export const get = async (req: Request, res: Response) => {
  const u = await users.findById(v.uuid.parse(req.params.id));
  if (!u) throw notFound('User not found');
  res.json(users.publicUser(u));
};
export const me = async (req: Request, res: Response) => {
  const u = await users.findById((req as AuthedRequest).userId);
  if (!u) throw notFound('User not found');
  res.json(users.publicUser(u));
};
export const updateMe = async (req: Request, res: Response) => {
  const b = v.profileUpdate.parse(req.body);
  try {
    const u = await users.updateProfile((req as AuthedRequest).userId, b.username, b.displayName);
    res.json(users.publicUser(u));
  } catch (e: any) {
    if (e?.code === '23505') throw conflict('Username already taken');
    throw e;
  }
};
