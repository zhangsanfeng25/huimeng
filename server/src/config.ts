import 'dotenv/config';
import { resolve } from 'node:path';

export const config = {
  port: Number(process.env.PORT || 3000),
  host: process.env.HOST || '127.0.0.1',
  databasePath: resolve(process.cwd(), process.env.DATABASE_PATH || './data/huimeng.db'),
  uploadDir: resolve(process.cwd(), process.env.UPLOAD_DIR || './uploads'),
  sessionTtlHours: Number(process.env.SESSION_TTL_HOURS || 12),
  orderTimeoutMinutes: Number(process.env.ORDER_TIMEOUT_MINUTES || 15),
  demoMode: process.env.DEMO_MODE !== 'false'
};
