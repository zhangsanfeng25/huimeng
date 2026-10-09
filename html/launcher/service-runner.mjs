import { spawn } from 'node:child_process';
import { appendFileSync, mkdirSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const launcherDir = resolve(fileURLToPath(new URL('.', import.meta.url)));
const serverDir = resolve(launcherDir, '../../server');
const logDir = resolve(launcherDir, '../server/data/logs');
mkdirSync(logDir, { recursive: true });

function logFile(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  return join(logDir, `service-${year}${month}${day}-${hour}.log`);
}

let cleanup;
function cleanLogs() {
  if (cleanup) return;
  cleanup = spawn('powershell.exe', ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', join(launcherDir, 'clean-logs.ps1')], { windowsHide:true, stdio:['ignore','pipe','pipe'] });
  let detail='';
  for(const stream of [cleanup.stdout,cleanup.stderr])stream.on('data',chunk=>{ detail=(detail+chunk).slice(-4096); });
  cleanup.on('error',error=>write('ERROR','日志清理: '+error.message));
  cleanup.on('close',code=>{ if(code!==0||detail.trim())write('ERROR','日志清理: '+detail); cleanup=null; });
}

function write(level, message) {
  const time = new Date();
  appendFileSync(logFile(time), `[${time.toISOString()}] [${level}] ${message}\n`, 'utf8');
}

function capture(stream, fallbackLevel) {
  let pending = '';
  stream.setEncoding('utf8');
  stream.on('data', chunk => {
    pending += chunk;
    let newline;
    while ((newline = pending.indexOf('\n')) >= 0) {
      const line = pending.slice(0, newline).replace(/\r$/, '');
      pending = pending.slice(newline + 1);
      const level = /\bDEBUG\b/i.test(line) ? 'DEBUG' : /\b(ERROR|FATAL)\b/i.test(line) ? 'ERROR' : fallbackLevel;
      write(level, line);
    }
    if (pending.length > 65536) {
      write(fallbackLevel, pending.slice(0, 65536) + '…');
      pending = pending.slice(65536);
    }
  });
  stream.on('end', () => { if (pending) write(fallbackLevel, pending); });
}

const child = spawn(process.execPath, ['dist/main.js'], {
  cwd: serverDir,
  env: process.env,
  stdio: ['ignore', 'pipe', 'pipe'],
  windowsHide: true
});
write('INFO', `正在启动网站与 App 共用服务，PID=${child.pid || '未知'}`);
capture(child.stdout, 'INFO');
capture(child.stderr, 'ERROR');
cleanLogs();
const cleanupTimer = setInterval(cleanLogs, 60 * 60 * 1000);
cleanupTimer.unref();
for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => child.kill(signal));
}
child.on('error', error => { write('ERROR', error.stack || String(error)); process.exitCode = 1; });
child.on('close', (code, signal) => {
  write(code === 0 ? 'INFO' : 'ERROR', `服务退出：code=${code}, signal=${signal || '无'}`);
  process.exitCode = code ?? 1;
});
