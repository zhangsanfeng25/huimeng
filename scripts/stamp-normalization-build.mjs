import {fileURLToPath} from 'node:url';
import {codeFingerprint} from '../server/dist/normalization/fingerprint.js';
import {stampBuild} from '../server/dist/normalization/build-artifacts.js';
const root=fileURLToPath(new URL('../',import.meta.url));
stampBuild(root,codeFingerprint(root));console.log('已绑定规范化维护所需的源码与实际构建产物');
