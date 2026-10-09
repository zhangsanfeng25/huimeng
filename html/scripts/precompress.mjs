import { readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { gzipSync } from 'node:zlib';
import { fileURLToPath } from 'node:url';
const assets=fileURLToPath(new URL('../dist/assets/',import.meta.url));
for(const file of readdirSync(assets)){
  if(/\.(js|css)$/.test(file))writeFileSync(assets+file+'.gz',gzipSync(readFileSync(assets+file),{level:9}));
}
