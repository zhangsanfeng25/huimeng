import { MerchantApplicationReview } from '../components/MerchantApplicationReview';
import { useSearchParams } from 'react-router-dom';
import { AccountContent } from '../components/AccountContent';
import { OrderActions } from '../components/OrderActions';
import { ProductDetails } from '../components/ProductDetails';
import { MediaImage } from '../components/Media';
import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Pencil, RotateCcw, Search, Trash2 } from 'lucide-react';
import { api, realtime } from '../api';
import { pageConfigs, RecordRow } from '../data';
import { Modal, PageHeader, StatusTag, Toast } from '../components/Ui';
export function ManagementPage({ type }: {
    type: keyof typeof pageConfigs;
}) {
    const config = pageConfigs[type];
    const [searchParams]=useSearchParams(),target=searchParams.get('record'),openedTarget=useRef('');
    const [categoryOptions,setCategoryOptions]=useState<Record<string,string[]>>({});
    useEffect(()=>{if(type==='categories')api<{data:Record<string,string[]>}>('/category-options').then(r=>setCategoryOptions(r.data)).catch(e=>setError(e.message));},[type]);
    const [rows, setRows] = useState<RecordRow[]>([]), [query, setQuery] = useState(''), [statusFilter, setStatusFilter] = useState(''), [shopFilter, setShopFilter] = useState(''), [page, setPage] = useState(1), [loading, setLoading] = useState(true), [error, setError] = useState(''), [updated, setUpdated] = useState(''), [toast, setToast] = useState('');
    const [selected, setSelected] = useState<RecordRow | null>(null), [form, setForm] = useState<RecordRow>({}), [mode, setMode] = useState<'view' | 'edit' | 'create' | 'delete' | null>(null), [detail, setDetail] = useState<Record<string, unknown> | null>(null), [detailBusy, setDetailBusy] = useState(false), [saving, setSaving] = useState(false);
    const detailRequest = useRef(0);
    const closeMode = () => { if (detailBusy || saving)
        return; detailRequest.current += 1; setMode(null); };
    const liveView = useRef({ mode, selected });
    liveView.current = { mode, selected };
    const load = useCallback(async () => { try {
        setLoading(true);
        const r = await api<{
            data: RecordRow[];
            updatedAt?: string;
        }>(`/${type}`);
        setRows(r.data);
        if (liveView.current.mode === 'view')
            setSelected(current => r.data.find(row => String(row[config.idKey]) === String(current?.[config.idKey])) || null);
        setUpdated(r.updatedAt ? new Date(r.updatedAt).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : '刚刚');
        setError('');
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setLoading(false);
    } }, [type]);
    useEffect(() => {
        let active = true;
        load();
        const unsubscribe = realtime(resource => {
            if (resource !== '*' && resource !== type && !(type === 'products' && resource === 'reviews'))
                return;
            load();
            const current = liveView.current;
            if (current.mode === 'view' && current.selected && ['users', 'merchants', 'products', 'reviews', 'orders'].includes(type)) {
                const id = String(current.selected[config.idKey]);
                const request = ++detailRequest.current;
                api<{
                    data: Record<string, unknown>;
                }>(`/${type}/${encodeURIComponent(id)}/details`).then(result => {
                    if (active && request === detailRequest.current && liveView.current.mode === 'view' && String(liveView.current.selected?.[config.idKey]) === id)
                        setDetail(result.data);
                }).catch(reason => { if (active)
                    setError(reason.message); });
            }
        });
        return () => { active = false; unsubscribe(); };
    }, [load, type, config.idKey]);
    const statuses = useMemo(() => [...new Set(rows.map(row => String(row.status || '')))].filter(Boolean), [rows]);
    const shops = useMemo(() => [...new Set(rows.map(row => String(row.shop || row.merchant || '')))].filter(Boolean), [rows]);
    const filtered = useMemo(() => {
        const keyword = query.toLowerCase();
        return rows.filter(row => (!query || Object.values(row).some(v => String(v).toLowerCase().includes(keyword))) && (!statusFilter || row.status === statusFilter) && (!shopFilter || (row.shop || row.merchant) === shopFilter));
    }, [rows, query, statusFilter, shopFilter]);
    useEffect(() => setPage(1), [query, statusFilter, shopFilter, type]);
    const pageCount = Math.max(1, Math.ceil(filtered.length / 25)), safePage = Math.min(page, pageCount), visibleRows = filtered.slice((safePage - 1) * 25, safePage * 25);
    const requestDetail = (id: string) => { setError(''); const request = ++detailRequest.current; api<{
        data: Record<string, unknown>;
    }>(`/${type}/${encodeURIComponent(id)}/details`).then(r => { if (request === detailRequest.current && liveView.current.mode === 'view' && String(liveView.current.selected?.[config.idKey]) === id)
        setDetail(r.data); }).catch(e => { if (request === detailRequest.current)
        setError(e.message); }); };
    const refreshSelected = () => { load(); if (selected)
        requestDetail(String(selected[config.idKey])); };
    const open = (next: typeof mode, row?: RecordRow) => { if (type === 'reviews' && next === 'edit')
        next = 'view'; setError(''); setSelected(row || null); setForm({ ...row }); setDetail(null); setMode(next); if (next === 'view' && row && ['users', 'merchants', 'products', 'reviews', 'orders'].includes(type))
        requestDetail(String(row[config.idKey])); };
    useEffect(()=>{if(!target||loading||openedTarget.current===type+target)return;const row=rows.find(v=>String(v[config.idKey])===target);if(row){openedTarget.current=type+target;open('view',row);}else setError('目标记录不存在或已删除');},[target,rows,loading,type]);
    const activeFields = config.fields.map(f=>type==='categories'&&f.key==='parent'?{...f,type:'select' as const,label:'分类类型',options:Object.keys(categoryOptions)}:type==='categories'&&f.key==='name'?{...f,type:'select' as const,options:[...new Set([...(categoryOptions[String(form.parent||Object.keys(categoryOptions)[0])]||[]),...(form.name&&form.name!=='自定义名称'?[String(form.name)]:[]),'自定义名称'])]}:f).filter(f => !(type === 'users' && mode === 'edit' && (f.key === 'account' || f.key === 'password')) && !(type === 'notices' && mode === 'edit' && (f.key === 'audience' || f.key === 'account')));
    const save = async (event?: FormEvent) => { event?.preventDefault(); if (saving)
        return; setSaving(true); try {
        for (const f of activeFields)
            if ((f.required || (type === 'users' && mode === 'create' && f.key === 'password')) && !String(form[f.key] ?? '').trim())
                throw new Error(`请填写${f.label}`);
        const body = Object.fromEntries(activeFields.map(f => [f.key, f.type === 'number' ? Number(form[f.key] || 0) : String(form[f.key] ?? f.options?.[0] ?? '')]));
        if(type==='merchants'&&selected?.updatedAt)body.expectedUpdatedAt=String(selected.updatedAt);
        if(type==='categories'&&body.name==='自定义名称')body.name=String(form.customName||'').trim();
        if (mode === 'create')
            await api(`/${type}`, { method: 'POST', body: JSON.stringify(body) });
        else
            await api(`/${type}/${encodeURIComponent(String(selected?.[config.idKey]))}`, { method: 'PUT', body: JSON.stringify(body) });
        detailRequest.current += 1;
        setMode(null);
        setToast(mode === 'create' ? '新增成功，已写入数据库。' : '修改成功，已同步到数据库。');
        await load();
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setSaving(false);
    } };
    const remove = async () => { if (saving)
        return; setSaving(true); try {
        await api(`/${type}/${encodeURIComponent(String(selected?.[config.idKey]))}`, { method: 'DELETE' });
        detailRequest.current += 1;
        setMode(null);
        setToast(type === 'users' ? '账户已停用，历史业务记录已保留。' : '记录已删除。');
        await load();
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setSaving(false);
    } };
    const value = (row: RecordRow, key: string, format?: string, showAvatar = true) => { const v = row[key] ?? ''; if (showAvatar && type === 'users' && key === 'nickname') return <div className="product-cell"><MediaImage src={row.avatarUrl} alt={String(v)+'的头像'} className="account-avatar"/><span>{String(v)}</span></div>; if (key === 'name' && type === 'products')
        return <div className="product-cell"><MediaImage src={row.coverUrl} alt={String(v)} className="community-thumb"/><span>{String(v)}</span></div>; if (format === 'status')
        return <StatusTag value={v}/>; if (key.endsWith('At') && v) {
        const date = new Date(String(v));
        return Number.isNaN(date.getTime()) ? String(v) : date.toLocaleString('zh-CN');
    } ; if (format === 'money')
        return `¥${Number(v).toFixed(2)}`; if (format === 'stars')
        return `${v} 星`; return String(v); };
    return <><PageHeader title={config.title} description={config.description} action={config.action || undefined} onAction={() => open('create')}/><section className="panel search-panel"><div className="search-field"><Search size={18}/><input aria-label="搜索" placeholder="请输入关键词、编号或名称" value={query} onChange={e => setQuery(e.target.value)}/></div>{statuses.length ? <select aria-label="状态筛选" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="">全部状态</option>{statuses.map(status => <option key={status}>{status}</option>)}</select> : null}{shops.length ? <select aria-label="商家筛选" value={shopFilter} onChange={e => setShopFilter(e.target.value)}><option value="">全部商家</option>{shops.map(shop => <option key={shop}>{shop}</option>)}</select> : null}<button className="secondary" onClick={() => { setQuery(''); setStatusFilter(''); setShopFilter(''); load(); }}><RotateCcw size={17}/>刷新</button></section>{error ? <p className="page-error">{error}<button onClick={() => setError('')}>关闭</button></p> : null}<section className="panel data-panel"><header><div><h2>{config.title.endsWith('列表') ? config.title : `${config.title}列表`}</h2><p>{loading ? '正在读取本地数据库…' : `共 ${filtered.length} 条记录`}</p></div><span className="data-note">数据更新时间 {updated || '--:--'}</span></header><div className="table-scroll"><table><thead><tr>{config.columns.map(c => <th key={c.key}>{c.label}</th>)}<th className="sticky-col">操作</th></tr></thead><tbody>{visibleRows.map(row => <tr key={String(row[config.idKey])}>{config.columns.map(c => <td key={c.key}>{value(row, c.key, c.format)}</td>)}<td className="sticky-col"><div className="row-actions"><button className="link" onClick={() => open('view', row)}>查看</button>{!config.readOnly && type !== 'reviews' ? <><button className="link" onClick={() => open('edit', row)}><Pencil size={12}/>编辑</button>{config.canDelete !== false ? <button className="link danger-link" onClick={() => open('delete', row)}><Trash2 size={12}/>{type === 'users' ? '停用' : '删除'}</button> : null}</> : null}</div></td></tr>)}{!loading && !filtered.length ? <tr><td colSpan={config.columns.length + 1}><div className="empty"><Search />没有找到匹配的数据<button onClick={() => setQuery('')}>清除筛选</button></div></td></tr> : null}</tbody></table></div><footer className="pagination"><span>第 {safePage} / {pageCount} 页 · 每页 25 条</span><button className="secondary" disabled={safePage <= 1} onClick={() => setPage(safePage - 1)}>上一页</button><button className="secondary" disabled={safePage >= pageCount} onClick={() => setPage(safePage + 1)}>下一页</button></footer></section>{mode === 'view' && selected ? <Modal title={`${config.title}详情`} busy={detailBusy} onClose={() => closeMode()} onConfirm={() => closeMode()} confirmText="关闭" hideCancel><>{error?<p className="page-error" role="alert">{error}<button className="secondary" onClick={refreshSelected}>重试读取</button></p>:null}</>{detail && (type === 'users' || type === 'merchants') ? <MediaImage src={detail.avatarUrl} alt="账户头像" className="account-avatar account-avatar-detail"/> : null}<dl className="detail-list">{config.columns.map(c => <div key={c.key}><dt>{c.label}</dt><dd>{value(selected, c.key, c.format, false)}</dd></div>)}</dl>{detail && type === 'merchants' ? <MerchantApplicationReview detail={detail} onChanged={refreshSelected} onBusyChange={setDetailBusy}/> : null}{detail && (type === 'users'||type==='merchants') ? <AccountContent key={String(selected[config.idKey])} detail={detail}/> : null}{detail && (type === 'products' || type === 'reviews') ? <ProductDetails key={String(detail.revisionId)} detail={detail} review={type === 'reviews'} onChanged={refreshSelected} onBusyChange={setDetailBusy}/> : null}{!detail && ['users', 'merchants', 'products', 'reviews', 'orders'].includes(type) ? <p className="muted">正在读取完整详情…</p> : null}{detail && type === 'orders' ? <div className="admin-detail-extra"><h3>商品明细与收货信息</h3>{(Array.isArray(detail.items) ? detail.items : []).map((item, index) => { const v = item as Record<string, unknown>; return <div className="product-cell" key={index}><MediaImage src={v.coverUrl} alt={String(v.name)} className="community-thumb"/><p>{String(v.name)} × {String(v.quantity)} · ¥{String(v.subtotal)}{Number(v.cancelledQuantity)>0?` · 已取消 ${String(v.cancelledQuantity)} 件，剩余履约 ${String(v.fulfillableQuantity)} 件`:""}</p></div>; })}<p>买家账号：{String(detail.buyerAccount)}</p><h3>收货信息</h3><dl className="detail-list">{Object.entries((detail.address || {}) as Record<string, unknown>).map(([key, val]) => <div key={key}><dt>{({ recipient: '收货人', name: '收货人', phone: '联系电话', province: '省', city: '市', district: '区', detail: '详细地址', address: '地址' } as Record<string, string>)[key] || key}</dt><dd>{String(val || '—')}</dd></div>)}</dl><OrderActions detail={detail} onChanged={refreshSelected}/><h3>订单事件</h3>{(Array.isArray(detail.events) ? detail.events : []).map((event, index) => { const v = event as Record<string, unknown>; return <p key={index}>{String(v.createdAt)} · {String(v.action)} · {String(v.toStatus)}</p>; })}</div> : null}</Modal> : null}{(mode === 'create' || mode === 'edit') ? <Modal title={mode === 'create' ? config.action : `编辑${config.title}`} busy={saving} onClose={() => closeMode()} onConfirm={() => save()}><form className="crud-form" onSubmit={save}>{error?<p className="page-error" role="alert">{error}</p>:null}{activeFields.map(f => <label key={f.key}>{f.label}{f.required ? <b>*</b> : null}{f.type === 'select' ? <select value={String(form[f.key] ?? f.options?.[0] ?? '')} onChange={e => setForm(v => ({ ...v, [f.key]: e.target.value,...(type==='categories'&&f.key==='parent'?{name:categoryOptions[e.target.value]?.[0]||''}:{}) }))}>{f.options?.map(o => <option key={o}>{o}</option>)}</select> : f.type === 'textarea' ? <textarea value={String(form[f.key] ?? '')} onChange={e => setForm(v => ({ ...v, [f.key]: e.target.value }))}/> : <input type={f.type === 'number' ? 'number' : f.type === 'password' ? 'password' : 'text'} minLength={f.type === 'password' ? 8 : undefined} value={String(form[f.key] ?? '')} onChange={e => setForm(v => ({ ...v, [f.key]: e.target.value }))}/>}</label>)}{type==='categories'&&form.name==='自定义名称'?<label>自定义分类名称<input value={String(form.customName||'')} onChange={e=>setForm(v=>({...v,customName:e.target.value}))}/></label>:null}</form>{type==='ratings'&&mode==='edit'?<div className="rating-delete"><button className="danger-button" disabled={saving} onClick={()=>setMode('delete')}>删除评价</button><small>删除后保留订单关联，内容不再展示。</small></div>:null}</Modal> : null}{mode === 'delete' && selected ? <Modal title={type === 'users' ? '确认停用账户' : '确认删除'} busy={saving} onClose={() => closeMode()} onConfirm={remove} confirmText={type === 'users' ? '停用账户' : '确认删除'} danger><div className="delete-confirm"><Trash2 /><div><strong>{type === 'users' ? '账户将无法登录，历史业务记录保留' : '删除后无法从页面恢复'}</strong><p>目标：“{String(selected[config.idKey])}”，操作会写入审计日志。</p></div></div></Modal> : null}{toast ? <Toast message={toast} onClose={() => setToast('')}/> : null}</>;
}
