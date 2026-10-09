import { updateSync } from './components/SyncStatus';
export type Json = Record<string, unknown>;
const KEY = 'huimeng_admin_session';
export const session = {
    get: () => sessionStorage.getItem(KEY) || localStorage.getItem(KEY) || '',
    set: (token: string, remember = false) => { localStorage.removeItem(KEY); sessionStorage.removeItem(KEY); (remember ? localStorage : sessionStorage).setItem(KEY, token); },
    clear: () => { localStorage.removeItem(KEY); sessionStorage.removeItem(KEY); }
};
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> { const headers = new Headers(options.headers); if (options.body && !headers.has('Content-Type'))
    headers.set('Content-Type', 'application/json'); const token = session.get(); if (token)
    headers.set('Authorization', `Bearer ${token}`); const response = await fetch(`/api/v1${path}`, { ...options, headers }); const data = await response.json().catch(() => ({ message: '服务器返回异常' })); if (response.status === 401) {
    session.clear();
    if (location.pathname != '/login')
        location.href = '/login';
} if (!response.ok)
    throw new Error(data.message || '请求失败'); updateSync({ lastUpdated: new Date().toISOString() }); return data as T; }
const subscribers = new Set<(resource: string) => void>();
let stream: EventSource | null = null, fallback: number | undefined;
let lastHeartbeat=0;
const refresh = () => { if (!document.hidden)
    subscribers.forEach(callback => callback('*')); };
function connect() {
    stream?.close();
    if (!navigator.onLine) {
        stream = null;
        updateSync({ state: 'offline' });
        return;
    }
    updateSync({ state: 'connecting' });
    stream = new EventSource(`/api/v1/events?token=${encodeURIComponent(session.get())}`);
    stream.addEventListener('ready', () => { lastHeartbeat=Date.now();updateSync({ state: 'live' }); refresh(); });
    stream.addEventListener('heartbeat',()=>{lastHeartbeat=Date.now();updateSync({state:'live'});});
    stream.addEventListener('error', () => updateSync({ state: navigator.onLine ? 'reconnecting' : 'offline' }));
    stream.addEventListener('update', event => { try {
        lastHeartbeat=Date.now();const resource = JSON.parse((event as MessageEvent).data).resource;
        subscribers.forEach(callback => callback(resource));
    }
    catch { } });
}
const online = () => { connect(); refresh(); };
const offline = () => { stream?.close(); stream = null; updateSync({ state: 'offline' }); };
export function realtime(onUpdate: (resource: string) => void) {
    subscribers.add(onUpdate);
    if (subscribers.size === 1) {
        connect();
        document.addEventListener('visibilitychange', refresh);
        window.addEventListener('online', online);
        window.addEventListener('offline', offline);
        fallback = window.setInterval(() => { if(stream?.readyState===EventSource.OPEN&&Date.now()-lastHeartbeat>30000){updateSync({state:'reconnecting'});connect();} if (stream?.readyState !== EventSource.OPEN) {
            if (navigator.onLine && (!stream || stream.readyState === EventSource.CLOSED))
                connect();
            refresh();
        } }, 15000);
    }
    return () => { subscribers.delete(onUpdate); if (!subscribers.size) {
        stream?.close();
        stream = null;
        window.clearInterval(fallback);
        document.removeEventListener('visibilitychange', refresh);
        window.removeEventListener('online', online);
        window.removeEventListener('offline', offline);
    } };
}
