import { Request, Response } from 'express';
import * as v from '../validators/schemas';
import { AuthedRequest } from '../middleware/auth';
import * as chats from '../repositories/chats';
import * as chat from '../services/chat';

export const list = async (req: Request, res: Response) => {
  res.json({ items: await chats.listFor((req as AuthedRequest).userId) });
};
export const direct = async (req: Request, res: Response) => {
  res.json(await chat.startDirect((req as AuthedRequest).userId, v.direct.parse(req.body).userId));
};
export const messages = async (req: Request, res: Response) => {
  const q = v.history.parse(req.query);
  res.json(await chat.getHistory((req as AuthedRequest).userId, v.uuid.parse(req.params.id), q.before ?? null, q.limit));
};
