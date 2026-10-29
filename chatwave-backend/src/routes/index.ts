import { NextFunction, Request, RequestHandler, Response, Router } from 'express';
import { requireAuth } from '../middleware/auth';
import { authLimiter, registerLimiter, searchLimiter } from '../middleware/rateLimit';
import * as auth from '../controllers/auth';
import * as users from '../controllers/users';
import * as chats from '../controllers/chats';

const wrap = (fn: (req: Request, res: Response) => Promise<unknown>): RequestHandler =>
  (req: Request, res: Response, next: NextFunction) => { fn(req, res).catch(next); };

export const api = Router();
api.post('/auth/register', registerLimiter, wrap(auth.register));
api.post('/auth/login', authLimiter, wrap(auth.login));
api.post('/auth/refresh', authLimiter, wrap(auth.refresh));
api.post('/auth/logout', authLimiter, wrap(auth.logout));

api.use(requireAuth);
api.get('/users/me', wrap(users.me));
api.patch('/users/me', wrap(users.updateMe));
api.get('/users/search', searchLimiter, wrap(users.search));
api.get('/users/:id', wrap(users.get));
api.get('/chats', wrap(chats.list));
api.post('/chats/direct', wrap(chats.direct));
api.post('/chats/group', wrap(chats.group));
api.post('/chats/:id/members', wrap(chats.addMember));
api.delete('/chats/:id/members', wrap(chats.removeMember));
api.get('/chats/:id/messages', wrap(chats.messages));
