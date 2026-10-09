// Explicit one-time maintenance: preview by default, --apply writes after backup.
import { mkdirSync, writeFileSync } from 'node:fs';
import { resolve, join } from 'node:path';
import { db, now } from '../dist/core/db.js';
import { defaultAvatars } from '../dist/core/default-avatars.js';
import { config } from '../dist/config.js';
import { audit } from '../dist/core/http.js';

try {
  const users = db.prepare("SELECT u.id,u.username,u.nickname,u.avatar_url previousAvatar FROM users u WHERE EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='USER') AND NOT EXISTS(SELECT 1 FROM user_roles r WHERE r.user_id=u.id AND r.role='ADMIN') ORDER BY u.id").all();
  const assignments = users.map((user, i) => ({ ...user, avatarUrl: defaultAvatars[i % defaultAvatars.length].avatarUrl }));
  if (!process.argv.includes('--apply')) { console.log(JSON.stringify({ preview: true, assignments }, null, 2)); }
  else {
    const stamp = now(), backupDir = resolve(config.databasePath, '..', 'backups', 'default-avatars-' + stamp.replace(/[:.]/g, '-'));
    mkdirSync(backupDir, { recursive: true });
    await db.backup(join(backupDir, 'huimeng.db'));
    db.transaction(() => {
      const update = db.prepare('UPDATE users SET avatar_url=?,updated_at=? WHERE id=?');
      for (const user of assignments) update.run(user.avatarUrl, stamp, user.id);
      audit('A0001', '平台管理员', '账户', '设置默认头像', '用户及商家', '成功', `按用户授权为 ${assignments.length} 个账户分配五种默认头像；已保存数据库快照`);
    })();
    const result = { appliedAt: stamp, backupDir, assignments };
    writeFileSync(join(backupDir, 'avatar-assignments.json'), JSON.stringify(result, null, 2));
    writeFileSync(resolve('../output/default-avatars-20261003/assignments.json'), JSON.stringify(result, null, 2));
    console.log(JSON.stringify(result, null, 2));
  }
} finally { db.close(); }
