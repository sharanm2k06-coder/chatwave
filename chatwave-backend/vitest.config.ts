import { defineConfig } from 'vitest/config';
export default defineConfig({
  test: {
    include: ['tests/**/*.test.ts'], fileParallelism: false, testTimeout: 20000,
    env: { NODE_ENV: 'test', JWT_ACCESS_SECRET: 'test-secret-test-secret-test-secret-1234',
           DATABASE_URL: process.env.TEST_DATABASE_URL ?? 'postgres://chatwave:chatwave@localhost:5432/chatwave_test' },
  },
});
