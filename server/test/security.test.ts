import test from 'node:test';
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword, createToken, hashToken } from '../src/core/security.js';

test('密码使用随机盐散列并能正确校验',()=>{
  const first=hashPassword('demo12345'),second=hashPassword('demo12345');
  assert.notEqual(first,second);
  assert.equal(verifyPassword('demo12345',first),true);
  assert.equal(verifyPassword('wrong-password',first),false);
});

test('会话只保存令牌摘要',()=>{
  const token=createToken();
  assert.equal(token.length,64);
  assert.notEqual(hashToken(token),token);
  assert.equal(hashToken(token),hashToken(token));
});
