import { resolve, join, dirname, relative } from 'node:path';
import { fileURLToPath } from 'node:url';
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';

const launcherDir = resolve(fileURLToPath(new URL('.', import.meta.url)));
const projectRoot = resolve(launcherDir, '../..');
function fingerprint(root, paths) {
  const hash = createHash('sha256').update(process.version);
  function visit(path) {
    if (!existsSync(path)) { hash.update('missing:' + relative(root,path)); return; }
    const entries = (() => { try { return readdirSync(path, { withFileTypes:true }); } catch (error) { if (error.code === 'ENOTDIR') return null; throw error; } })();
    if (entries) {
      for (const entry of entries.sort((a,b) => a.name.localeCompare(b.name))) {
        if (entry.name === 'launcher-build.json') continue;
        if (entry.isSymbolicLink()) throw new Error('Build inputs cannot contain symbolic links');
        visit(join(path,entry.name));
      }
    } else hash.update(relative(root,path)).update('\0').update(readFileSync(path)).update('\0');
  }
  for (const path of paths) visit(resolve(root,path));
  return hash.digest('hex');
}
export function ensureBuild(root, workspace, build) {
  if (!['html','server'].includes(workspace)) throw new Error('Unknown build workspace');
  const inputs = ['package.json','package-lock.json', `${workspace}/package.json`, `${workspace}/tsconfig.json`, `${workspace}/src`];
  if (workspace === 'server') inputs.push('server/scripts/migrate.ts','server/scripts/seed.ts');
  if (workspace === 'html') inputs.push('html/index.html','html/vite.config.ts','html/scripts','html/public','html/.env','html/.env.local','html/.env.production','html/.env.production.local');
  const outputDir = resolve(root,workspace,'dist');
  const marker = join(outputDir,'launcher-build.json');
  const inputHash = fingerprint(root,inputs);
  try {
    const cached = JSON.parse(readFileSync(marker,'utf8'));
    if (cached.inputHash === inputHash && cached.outputHash === fingerprint(root,[`${workspace}/dist`])) {
      console.log(`[${new Date().toISOString()}] ${workspace}: unchanged, reusing verified build.`);
      return false;
    }
  } catch { /* Missing or invalid cache always rebuilds. */ }
  if (build) build();
  else {
    const npmCli = resolve(dirname(process.env.NPM_CMD || process.execPath),'node_modules/npm/bin/npm-cli.js');
    if (!existsSync(npmCli)) throw new Error('npm CLI missing: ' + npmCli);
    const result = spawnSync(process.execPath,[npmCli,'run','build',`--workspace=${workspace}`],{cwd:root,stdio:'inherit',windowsHide:true});
    if (result.error || result.status !== 0) throw result.error || new Error(`${workspace} build failed (${result.status})`);
  }
  const entry = workspace === 'html' ? 'index.html' : 'main.js';
  if (!existsSync(join(outputDir,entry))) throw new Error('Build output missing: ' + entry);
  writeFileSync(marker, JSON.stringify({ inputHash, outputHash:fingerprint(root,[`${workspace}/dist`]) }) + '\n');
  return true;
}
if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  if (process.argv[2] === '--build') ensureBuild(projectRoot,process.argv[3]);
  else {
    process.chdir(resolve(projectRoot,'server'));
    await import('../../server/dist/scripts/migrate.js');
    await import('../../server/dist/scripts/seed.js');
  }
}
