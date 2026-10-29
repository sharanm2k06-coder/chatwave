import { forbidden, notFound, badRequest } from '../utils/errors';
import * as users from '../repositories/users';
import * as chats from '../repositories/chats';
import * as messages from '../repositories/messages';

export async function startDirect(selfId: string, otherId: string) {
  if (selfId === otherId) throw badRequest('Cannot start a conversation with yourself');
  const other = await users.findById(otherId);
  if (!other) throw notFound('User not found');
  const id = await chats.getOrCreateDirect(selfId, otherId);
  return { id, type: 'direct', peer: users.publicUser(other) };
}

export async function startGroup(ownerId: string, name: string, memberIds: string[]) {
  const ids = [...new Set(memberIds)];
  for (const id of ids) if (!(await users.findById(id))) throw notFound('Group member not found');
  return { id: await chats.createGroup(ownerId, name, ids), type: 'group', name };
}

export async function changeMember(actorId: string, convId: string, memberId: string, add: boolean) {
  if ((await chats.memberRole(convId, actorId)) !== 'admin') throw forbidden('Only group admins can manage members');
  if (add) {
    if (!(await users.findById(memberId))) throw notFound('User not found');
    await chats.addMember(convId, memberId);
  } else {
    if (actorId === memberId) throw badRequest('Group admins cannot remove themselves');
    await chats.removeMember(convId, memberId);
  }
}

export async function assertMember(convId: string, userId: string) {
  if (!(await chats.isMember(convId, userId))) throw forbidden('Not a member of this conversation');
}

export async function getHistory(userId: string, convId: string, before: number | null, limit: number) {
  await assertMember(convId, userId);
  return messages.history(convId, before, limit);
}

export async function sendMessage(senderId: string, convId: string, clientMessageId: string, body: string) {
  await assertMember(convId, senderId);
  const recipients = (await chats.memberIds(convId)).filter(id => id !== senderId);
  const { message, duplicate } = await messages.insertIdempotent(convId, senderId, clientMessageId, body, recipients);
  return { message: messages.toDto(message), duplicate, recipients };
}
