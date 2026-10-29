import { pool } from './pool';
import { purgeExpired } from '../repositories/messages';

purgeExpired()
  .then(() => pool.end())
  .catch(async error => {
    console.error(error);
    await pool.end();
    process.exitCode = 1;
  });
