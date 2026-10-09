import { createHash, randomBytes, scryptSync, timingSafeEqual } from 'node:crypto';

export const hashToken = (token: string) => createHash('sha256').update(token).digest('hex');
export const createToken = () => randomBytes(32).toString('hex');
export function hashPassword(password: string) {
  const salt = randomBytes(16).toString('hex');
  return `scrypt-v1:${salt}:${scryptSync(password, salt, 64).toString('hex')}`;
}
export function verifyPassword(password: string, stored: string) {
  const [, salt, hex] = stored.split(':');
  if (!salt || !hex) return false;
  const actual = scryptSync(password, salt, 64);
  const expected = Buffer.from(hex, 'hex');
  return actual.length === expected.length && timingSafeEqual(actual, expected);
}
