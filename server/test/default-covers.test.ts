import test from 'node:test';
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';

test('twenty bundled demo covers are readable PNGs matching the existing public URLs',()=>{
 for(let n=1;n<=20;n++){
  const file=`p${String(n).padStart(4,'0')}.png`;
  const bytes=readFileSync(fileURLToPath(new URL(`../assets/books/${file}`,import.meta.url)));
  assert.equal(bytes.subarray(0,8).toString('hex'),'89504e470d0a1a0a',file);
  assert.equal(bytes.readUInt32BE(16),400,file);
  assert.equal(bytes.readUInt32BE(20),520,file);
 }
});
