import { Request, Response } from 'express';
import * as v from '../validators/schemas';
import * as auth from '../services/auth';

export const register = async (req: Request, res: Response) => {
  const b = v.register.parse(req.body);
  res.status(201).json(await auth.register(b.username, b.password, b.displayName));
};
export const login = async (req: Request, res: Response) => {
  const b = v.login.parse(req.body);
  res.json(await auth.login(b.username, b.password));
};
export const refresh = async (req: Request, res: Response) => {
  res.json(await auth.refresh(v.refresh.parse(req.body).refreshToken));
};
export const logout = async (req: Request, res: Response) => {
  await auth.logout(v.refresh.parse(req.body).refreshToken);
  res.status(204).end();
};
