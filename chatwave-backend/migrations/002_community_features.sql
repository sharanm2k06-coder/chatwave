ALTER TABLE conversations DROP CONSTRAINT IF EXISTS conversations_type_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_type_check CHECK (type IN ('direct','group','global'));

CREATE UNIQUE INDEX IF NOT EXISTS conversations_global_uq
  ON conversations (type) WHERE type = 'global';

INSERT INTO conversations (id, type, name)
VALUES ('00000000-0000-0000-0000-000000000001', 'global', 'Global Chat')
ON CONFLICT (id) DO NOTHING;

INSERT INTO conversation_members (conversation_id, user_id, role)
SELECT '00000000-0000-0000-0000-000000000001', id, 'member'
FROM users
ON CONFLICT DO NOTHING;
