import {mkdirSync,copyFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {join} from 'node:path';
const root=fileURLToPath(new URL('../',import.meta.url));
for(const relative of ['dist/normalization','dist/src/normalization']){
 const directory=join(root,relative);mkdirSync(directory,{recursive:true});
 copyFileSync(join(root,'src/normalization/target-schema.sql'),join(directory,'target-schema.sql'));
}
