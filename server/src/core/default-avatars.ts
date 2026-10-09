import { db } from './db.js';

export const defaultAvatars = [
  { id: 'sprout', name: '书页新芽', avatarUrl: '/public/default-avatars/sprout.png' },
  { id: 'whale', name: '星月鲸鱼', avatarUrl: '/public/default-avatars/whale.png' },
  { id: 'house', name: '阅读小屋', avatarUrl: '/public/default-avatars/house.png' },
  { id: 'fox', name: '狐狸读者', avatarUrl: '/public/default-avatars/fox.png' },
  { id: 'cat', name: '眼镜小猫', avatarUrl: '/public/default-avatars/cat.png' }
] as const;

export function isDefaultAvatar(value: string) {
  return defaultAvatars.some(avatar => avatar.avatarUrl === value);
}

export function leastUsedDefaultAvatar() {
  const counts = db.prepare('SELECT avatar_url,COUNT(*) count FROM users GROUP BY avatar_url').all() as Array<{ avatar_url: string; count: number }>;
  return [...defaultAvatars].sort((a, b) =>
    (counts.find(row => row.avatar_url === a.avatarUrl)?.count || 0) -
    (counts.find(row => row.avatar_url === b.avatarUrl)?.count || 0)
  )[0].avatarUrl;
}
