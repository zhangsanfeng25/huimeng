import { useEffect, useState, useSyncExternalStore } from 'react';
export type SyncState = {
    state: 'connecting' | 'live' | 'reconnecting' | 'offline';
    lastUpdated: string;
};
let snapshot: SyncState = { state: navigator.onLine ? 'connecting' : 'offline', lastUpdated: '' };
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
const getSnapshot = () => snapshot;
export function updateSync(value: Partial<SyncState>) { snapshot = { ...snapshot, ...value }; listeners.forEach(fn => fn()); }
export function SyncStatus() {
    const [clock,setClock]=useState(()=>new Date());
    useEffect(()=>{const timer=window.setInterval(()=>setClock(new Date()),1000);return()=>window.clearInterval(timer);},[]);
    const value = useSyncExternalStore(subscribe, getSnapshot);
    const labels = { connecting: '正在连接', live: '实时连接正常', reconnecting: '连接中断，正在重连', offline: '网络已断开' };
    return <div className={`sync-status ${value.state}`} title={value.lastUpdated ? `最近成功读取数据：${new Date(value.lastUpdated).toLocaleString('zh-CN')}` : '尚未成功读取数据'}><span><i />{value.state==='live'?'连接正常':''} <time>{clock.toLocaleTimeString('zh-CN',{hour12:false})}</time></span>{value.state!=='live'?<small role="status">{labels[value.state]}</small>:null}</div>;
}
