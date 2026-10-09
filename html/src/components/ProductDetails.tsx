import { ChangeEvent, useState } from 'react';
import { api } from '../api';
import { ImageGallery, MediaImage } from './Media';
import { StatusTag } from './Ui';
type Detail = Record<string, unknown>;
export function ProductDetails({ detail, review, onChanged, onBusyChange }: {
    detail: Detail;
    review: boolean;
    onChanged: () => void;
    onBusyChange: (busy: boolean) => void;
}) {
    const [editing, setEditing] = useState(false), [draft, setDraft] = useState(detail), [busy, setBusy] = useState(false), [error, setError] = useState(''), [reason, setReason] = useState('');
    const operation = async (work: () => Promise<unknown>, preserveEditor = false) => { setBusy(true); onBusyChange(true); setError(''); try {
        await work();
        if (!preserveEditor) {
            setEditing(false);
            onChanged();
        }
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setBusy(false);
        onBusyChange(false);
    } };
    const upload = async (event: ChangeEvent<HTMLInputElement>) => {
        const files = Array.from(event.target.files || []);
        event.target.value = '';
        if (files.length + (Array.isArray(draft.images) ? draft.images.length : 0) > 9) {
            setError('最多 9 张详情图片，请先移除不需要的图片');
            return;
        }
        await operation(async () => {
            const urls: string[] = [];
            for (const file of files) {
                const bitmap = await createImageBitmap(file), scale = Math.min(1, 1800 / Math.max(bitmap.width, bitmap.height)), canvas = document.createElement('canvas');
                canvas.width = Math.round(bitmap.width * scale);
                canvas.height = Math.round(bitmap.height * scale);
                canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
                bitmap.close();
                const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', .85));
                if (!blob || blob.size > 3 * 1024 * 1024)
                    throw new Error('图片超过 3 MB，请缩小后重试');
                urls.push((await api<{
                    url: string;
                }>('/admin/media/uploads', { method: 'POST', headers: { 'Content-Type': 'image/jpeg' }, body: blob })).url);
            }
            setDraft(current => ({ ...current, coverUrl: current.coverUrl || urls[0], images: [...(Array.isArray(current.images) ? current.images : []), ...urls] }));
        }, true);
        setEditing(true);
    };
    const decide = (status: string) => operation(() => api(`/reviews/${encodeURIComponent(String(detail.revisionId))}`, { method: 'PUT', body: JSON.stringify({ status, note: reason }) }));
    return <section className="product-details"><h3>{String(detail.name)} · 版本 {String(detail.version)}</h3><dl className="detail-list">{[['商品编号', detail.id], ['审核编号', detail.revisionId], ['商家', detail.shop], ['分类', detail.category], ['适龄范围', detail.ageRange], ['单价', `¥${Number(detail.price).toFixed(2)}`], ['可用 / 预留库存', `${detail.stock} / ${detail.reserved}`], ['提交时间', detail.submittedAt], ['审核时间', detail.reviewedAt], ['驳回原因', detail.rejectionReason]].map(([name, value]) => <div key={String(name)}><dt>{String(name)}</dt><dd>{String(value || '—')}</dd></div>)}</dl><p>审核状态：<StatusTag value={({ PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', DRAFT: '草稿' } as Record<string, string>)[String(detail.approval)] || String(detail.approval)}/></p><h3>商品介绍</h3><p className="full-text">{String(detail.summary || '未填写介绍')}</p><h3>封面与详情图片</h3><ImageGallery images={[detail.coverUrl, ...(Array.isArray(detail.images) ? detail.images : [])]} label="商品图片"/>{detail.audioUrl ? <p><a href={String(detail.audioUrl)} target="_blank" rel="noreferrer">打开配套音频</a></p> : null}
    {!review && !editing ? <button className="secondary" onClick={() => { setDraft(detail); setEditing(true); }}>维护商品图文</button> : null}
    {editing ? <div className="crud-form content-editor">{[['name', '商品名称'], ['summary', '商品介绍'], ['ageRange', '适龄范围']].map(([key, label]) => <label key={key}>{label}{key === 'summary' ? <textarea value={String(draft[key] || '')} onChange={e => setDraft(v => ({ ...v, [key]: e.target.value }))}/> : <input value={String(draft[key] || '')} onChange={e => setDraft(v => ({ ...v, [key]: e.target.value }))}/>}</label>)}<label className="secondary">上传真实图片<input type="file" accept="image/*" multiple disabled={busy} onChange={upload}/></label><div className="media-gallery">{[...new Set([draft.coverUrl, ...(Array.isArray(draft.images) ? draft.images : [])].filter(Boolean))].map(src => <div key={String(src)}><MediaImage src={src} alt="待保存商品图片"/><div className="row-actions"><button className="link" disabled={busy} onClick={() => setDraft(v => ({ ...v, coverUrl: src }))}>设为封面</button><button className="link danger-link" disabled={busy} onClick={() => setDraft(v => ({ ...v, coverUrl: v.coverUrl === src ? '' : v.coverUrl, images: (Array.isArray(v.images) ? v.images : []).filter(x => x !== src) }))}>移除</button></div></div>)}</div><div className="row-actions"><button className="primary" disabled={busy} onClick={() => operation(() => api(`/products/${encodeURIComponent(String(detail.id))}/content`, { method: 'PUT', body: JSON.stringify(draft) }))}>保存图文</button><button className="secondary" disabled={busy} onClick={() => setEditing(false)}>取消修改</button></div><p className="muted">原图缺失时请上传真实素材；保存后网页和手机读取同一份图文。</p></div> : null}
    {review && detail.approval === 'PENDING' && detail.version === detail.latestVersion ? <div className="review-actions"><h3>审核此版本</h3><label>审核说明（驳回必填）<textarea value={reason} maxLength={2000} onChange={e => setReason(e.target.value)}/></label><div className="row-actions"><button className="primary" disabled={busy} onClick={() => decide('已通过')}>通过并上架</button><button className="danger-button" disabled={busy || !reason.trim()} onClick={() => decide('已驳回')}>驳回并说明原因</button></div></div> : review ? <p className="muted">此版本已审核或已有新版本，请查看最新待审记录。</p> : null}{error ? <p role="alert" className="page-error">{error}</p> : null}
  </section>;
}
