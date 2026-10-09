import { useSearchParams } from 'react-router-dom';
import { AccountContent } from '../components/AccountContent';
import { ImageGallery, MediaImage } from '../components/Media';
import { ChangeEvent, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Heart, MessageCircle, Pencil, Plus, RotateCcw, Search, Upload } from 'lucide-react';
import { api, realtime } from '../api';
import { Modal, PageHeader, StatusTag, Toast } from '../components/Ui';
type Row = {
    id: string;
    title: string;
    content: string;
    authorId: string;
    authorName: string;
    authorAccount: string;
    role: string;
    coverUrl: string | null;
    images: string;
    product: string;
    publishedAt: string;
    status: string;
    reports: number;
    likeCount: number;
    commentCount: number;
};
type Person = {
    userId: string;
    account: string;
    nickname: string;
    avatarUrl: string | null;
    createdAt: string;
};
type Comment = Person & {
    id: string;
    content: string;
    status: string;
};
type Detail = {
    id: string;
    title: string;
    content: string;
    authorId: string;
    authorName: string;
    authorAccount: string;
    avatarUrl: string | null;
    images: string[];
    status: string;
    coverUrl?: string;
    createdAt: string;
    reports: number;
    likeCount: number;
    commentCount: number;
    likes: Person[];
    comments: Comment[];
};
type Account = {
    [key:string]:unknown;
    id: string;
    username: string;
    nickname: string;
    email: string | null;
    phone: string | null;
    avatarUrl: string | null;
    status: string;
    createdAt: string;
    orders: number;
    posts: number;
    comments: number;
    likes: number;
};
type Draft = {
    title: string;
    content: string;
    status: '公开' | '已隐藏';
    images: string[];
};
const blank: Draft = { title: '', content: '', status: '公开', images: [] };
const showStatus = (s: string) => s === 'PUBLIC' ? '公开' : s === 'HIDDEN' ? '已隐藏' : s === 'DELETED' ? '已删除' : s;
async function toJpeg(file: File) { const bitmap = await createImageBitmap(file), scale = Math.min(1, 1800 / Math.max(bitmap.width, bitmap.height)); const canvas = document.createElement('canvas'); canvas.width = Math.round(bitmap.width * scale); canvas.height = Math.round(bitmap.height * scale); canvas.getContext('2d')!.drawImage(bitmap, 0, 0, canvas.width, canvas.height); bitmap.close(); const blob = await new Promise<Blob | null>(resolve => canvas.toBlob(resolve, 'image/jpeg', .82)); if (!blob || blob.size > 3 * 1024 * 1024)
    throw new Error('图片处理失败或超过 3 MB'); return blob; }
export function CommunityPage() {
 const [params]=useSearchParams(),target=params.get('record'),targetComment=params.get('comment');
 useEffect(()=>{if(target)refreshDetail(target).catch(e=>setError(e.message));},[target]);

    const [rows, setRows] = useState<Row[]>([]), [query, setQuery] = useState(''), [loading, setLoading] = useState(true), [error, setError] = useState(''), [toast, setToast] = useState('');
    const [detail, setDetail] = useState<Detail | null>(null), [account, setAccount] = useState<Account | null>(null), [draft, setDraft] = useState<Draft>(blank), [editor, setEditor] = useState<'create' | 'edit' | null>(null), [uploading, setUploading] = useState(false), [saving, setSaving] = useState(false), [commentText, setCommentText] = useState(''), [editingComment, setEditingComment] = useState<string | null>(null), [commentStatus, setCommentStatus] = useState('公开');
 useEffect(()=>{if(detail&&targetComment)document.getElementById('comment-'+targetComment)?.scrollIntoView({block:'center'});},[detail?.id,targetComment]);
    const load = useCallback(async () => { try {
        setLoading(true);
        setRows((await api<{
            data: Row[];
        }>('/community')).data);
        setError('');
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setLoading(false);
    } }, []);
    const currentDetail = useRef(detail);
    currentDetail.current = detail;
    useEffect(() => {
        let active = true;
        load();
        const unsubscribe = realtime(resource => {
            if (resource !== '*' && resource !== 'community' && resource !== 'users')
                return;
            load();
            const id = currentDetail.current?.id;
            if (id)
                api<{
                    data: Detail;
                }>(`/community/${encodeURIComponent(id)}/details`).then(result => {
                    if (active)
                        setDetail(current => current?.id === id ? result.data : current);
                }).catch(reason => { if (active)
                    setError(reason.message); });
        });
        return () => { active = false; unsubscribe(); };
    }, [load]);
    const filtered = useMemo(() => rows.filter(row => !query || [row.id, row.title, row.authorName, row.authorAccount, row.content, row.product].some(v => String(v || '').toLowerCase().includes(query.toLowerCase()))), [rows, query]);
    const refreshDetail = async (id: string) => { const value = (await api<{
        data: Detail;
    }>(`/community/${encodeURIComponent(id)}/details`)).data; setDetail(value); return value; };
    const run = async (operation: () => Promise<unknown>, message: string, refresh = true) => { try {
        await operation();
        setError('');
        setToast(message);
        await load();
        if (refresh && detail)
            await refreshDetail(detail.id);
    }
    catch (e) {
        setError((e as Error).message);
    } };
    const showAccount = async (id: string) => { try {
        setAccount((await api<{
            data: Account;
        }>(`/users/${encodeURIComponent(id)}/details`)).data);
    }
    catch (e) {
        setError((e as Error).message);
    } };
    const upload = async (event: ChangeEvent<HTMLInputElement>) => { const files = Array.from(event.target.files || []); event.target.value = ''; if (!files.length)
        return; if (draft.images.length + files.length > 9) {
        setError('一个帖子最多上传 9 张图片');
        return;
    } setUploading(true); try {
        const urls: string[] = [];
        for (const file of files) {
            const jpeg = await toJpeg(file);
            const response = await api<{
                url: string;
            }>('/admin/community/uploads', { method: 'POST', headers: { 'Content-Type': 'image/jpeg' }, body: jpeg });
            urls.push(response.url);
        }
        setDraft(v => ({ ...v, images: [...v.images, ...urls] }));
    }
    catch (e) {
        setError((e as Error).message);
    }
    finally {
        setUploading(false);
    } };
    const save = async () => { if (uploading || saving)
        return; setSaving(true); try {
        if (!draft.title.trim() || !draft.content.trim()) {
            setError('请填写标题和正文');
            return;
        }
        const current = editor, postId = detail?.id;
        await run(async () => { if (current === 'create')
            await api('/community', { method: 'POST', body: JSON.stringify(draft) });
        else
            await api(`/community/${encodeURIComponent(postId!)}/details`, { method: 'PUT', body: JSON.stringify(draft) }); setEditor(null); }, current === 'create' ? '帖子已发布' : '帖子已更新', false);
        if (current === 'edit' && postId)
            await refreshDetail(postId);
    }
    finally {
        setSaving(false);
    } };
    const startEdit = () => { if (!detail)
        return; setDraft({ title: detail.title, content: detail.content, status: showStatus(detail.status) === '公开' ? '公开' : '已隐藏', images: detail.images }); setEditor('edit'); };
    const deletePost = async (id: string) => { if (!window.confirm('确认删除此帖子？手机端将不再显示，历史记录仍保留用于审计。'))
        return; await run(() => api(`/community/${encodeURIComponent(id)}`, { method: 'DELETE' }), '帖子已删除', false); setDetail(null); };
    const saveComment = async () => { if (!detail || !commentText.trim())
        return; const id = detail.id, body = JSON.stringify({ content: commentText, status: commentStatus }); await run(() => api(`/community/${encodeURIComponent(id)}/comments${editingComment ? `/${encodeURIComponent(editingComment)}` : ''}`, { method: editingComment ? 'PUT' : 'POST', body }), '评论已保存'); setCommentText(''); setEditingComment(null); setCommentStatus('公开'); };
    const moderate = async (comment: Comment) => { if (!detail)
        return; await run(() => api(`/community/${encodeURIComponent(detail.id)}/comments/${encodeURIComponent(comment.id)}`, { method: 'PUT', body: JSON.stringify({ content: comment.content, status: comment.status === 'PUBLIC' ? '已隐藏' : '公开' }) }), '评论状态已更新'); };
    const removeComment = async (comment: Comment) => { if (!detail || !window.confirm('确认删除这条评论？'))
        return; await run(() => api(`/community/${encodeURIComponent(detail.id)}/comments/${encodeURIComponent(comment.id)}`, { method: 'DELETE' }), '评论已删除'); };
    return <><PageHeader title="社区内容" description="查看手机端实时帖子、作者账户、图片、点赞用户与评论，并管理可见状态。" action="发布帖子" onAction={() => { setDraft(blank); setEditor('create'); }}/>
 <section className="panel search-panel"><div className="search-field"><Search size={18}/><input aria-label="搜索社区内容" placeholder="搜索帖子、作者账户或关联商品" value={query} onChange={e => setQuery(e.target.value)}/></div><button className="secondary" onClick={() => { setQuery(''); load(); }}><RotateCcw size={17}/>刷新</button></section>
 {error ? <p className="page-error">{error}<button onClick={() => setError('')}>关闭</button></p> : null}
 <section className="panel data-panel"><header><div><h2>帖子列表</h2><p>{loading ? '正在读取数据库…' : `共 ${filtered.length} 条帖子`}</p></div><span className="data-note">点赞与评论来自同一数据库</span></header><div className="table-scroll"><table><thead><tr><th>帖子 / 内容</th><th>作者账户</th><th>关联商品</th><th>互动</th><th>发布时间</th><th>状态</th><th>操作</th></tr></thead><tbody>{filtered.map(row => <tr key={row.id}><td><strong>{row.title}</strong><small className="community-excerpt">{row.content}</small></td><td><button className="link" onClick={() => showAccount(row.authorId)}>{row.authorName}</button><small className="community-excerpt">{row.authorAccount} · {row.role}</small></td><td>{row.product || '—'}</td><td><span className="community-count"><Heart size={14}/>{row.likeCount}</span><span className="community-count"><MessageCircle size={14}/>{row.commentCount}</span></td><td>{row.publishedAt}</td><td><StatusTag value={row.status}/></td><td><button className="link" onClick={() => refreshDetail(row.id).catch(e => setError(e.message))}>查看</button><button className="link danger-link" onClick={() => deletePost(row.id)}>删除</button></td></tr>)}{!loading && !filtered.length ? <tr><td colSpan={7}>没有匹配的帖子</td></tr> : null}</tbody></table></div></section>
 {detail ? <Modal title="社区帖子详情" onClose={() => setDetail(null)} onConfirm={() => setDetail(null)} confirmText="关闭" hideCancel><div className="community-detail"><div className="community-detail-head"><div><strong>{detail.title}</strong><p>帖子 ID：{detail.id} · {new Date(detail.createdAt).toLocaleString('zh-CN')} · <StatusTag value={showStatus(detail.status)}/></p></div><button className="secondary" onClick={startEdit}><Pencil size={15}/>编辑帖子</button></div><button className="link" onClick={() => showAccount(detail.authorId)}>作者：{detail.authorName}（{detail.authorAccount}）</button><p className="community-body">{detail.content}</p><ImageGallery images={[detail.coverUrl, ...detail.images]} label="帖子图片"/><div className="community-stats"><span><Heart size={16}/> {detail.likeCount} 人点赞</span><span><MessageCircle size={16}/> {detail.commentCount} 条公开评论</span><span>举报 {detail.reports}</span></div><h3>点赞用户</h3>{detail.likes.length ? <div className="community-people">{detail.likes.map(user => <div key={user.userId}><button className="link" onClick={() => showAccount(user.userId)}>{user.nickname}（{user.account}）</button><small>{new Date(user.createdAt).toLocaleString('zh-CN')}</small><button className="link danger-link" onClick={() => { if (window.confirm('确认移除这条点赞记录？'))
        run(() => api(`/community/${encodeURIComponent(detail.id)}/likes/${encodeURIComponent(user.userId)}`, { method: 'DELETE' }), '点赞已移除'); }}>移除点赞</button></div>)}</div> : <p className="muted">暂无点赞</p>}<h3>评论管理</h3><div className="community-comment-form"><textarea aria-label="评论内容" placeholder="输入管理员评论或编辑所选评论" value={commentText} maxLength={500} onChange={e => setCommentText(e.target.value)}/><select aria-label="评论状态" value={commentStatus} onChange={e => setCommentStatus(e.target.value)}><option>公开</option><option>已隐藏</option></select><button className="primary" onClick={saveComment}><Plus size={15}/>{editingComment ? '保存评论' : '添加评论'}</button>{editingComment ? <button className="secondary" onClick={() => { setEditingComment(null); setCommentText(''); }}>取消编辑</button> : null}</div>{detail.comments.map(comment => <div className="community-comment" key={comment.id} id={'comment-'+comment.id}><div><button className="link" onClick={() => showAccount(comment.userId)}>{comment.nickname}（{comment.account}）</button><small>{new Date(comment.createdAt).toLocaleString('zh-CN')} · {showStatus(comment.status)}</small></div><p>{comment.content}</p><div>{comment.status !== 'DELETED' ? <><button className="link" onClick={() => { setEditingComment(comment.id); setCommentText(comment.content); setCommentStatus(comment.status === 'PUBLIC' ? '公开' : '已隐藏'); }}>编辑</button><button className="link" onClick={() => moderate(comment)}>{comment.status === 'PUBLIC' ? '隐藏' : '公开'}</button><button className="link danger-link" onClick={() => removeComment(comment)}>删除</button></> : null}</div></div>)}</div></Modal> : null}
 {editor ? <Modal title={editor === 'create' ? '发布社区帖子' : '编辑社区帖子'} onClose={() => setEditor(null)} onConfirm={save} busy={uploading || saving} confirmText="保存帖子"><div className="community-editor">{error ? <p className="page-error" role="alert">{error}</p> : null}<label>标题<input maxLength={80} value={draft.title} onChange={e => setDraft(v => ({ ...v, title: e.target.value }))}/></label><label>正文<textarea maxLength={2000} value={draft.content} onChange={e => setDraft(v => ({ ...v, content: e.target.value }))}/></label><label>状态<select value={draft.status} onChange={e => setDraft(v => ({ ...v, status: e.target.value as Draft['status'] }))}><option>公开</option><option>已隐藏</option></select></label><label className="secondary community-upload"><Upload size={17}/>{uploading ? '正在上传…' : '添加图片（最多 9 张）'}<input type="file" accept="image/*" multiple onChange={upload} disabled={uploading || draft.images.length >= 9}/></label><div className="community-gallery">{draft.images.map((src, index) => <div className="community-image-card" key={src}><MediaImage src={src} alt={`待保存图片 ${index + 1}`}/><button aria-label={`删除第 ${index + 1} 张图片`} onClick={() => setDraft(v => ({ ...v, images: v.images.filter((_, i) => i !== index) }))}>×</button></div>)}</div>{editor === 'create' ? <p className="muted">帖子将以当前管理员账户发布，手机端与网页端会显示同一内容。</p> : null}</div></Modal> : null}
 {account ? <Modal title="用户账户详情" onClose={() => setAccount(null)} onConfirm={() => setAccount(null)} confirmText="关闭" hideCancel><div className="community-account">{account.avatarUrl ? <MediaImage src={account.avatarUrl} alt="用户头像"/> : null}<dl className="detail-list"><div><dt>账号</dt><dd>{account.username}</dd></div><div><dt>昵称</dt><dd>{account.nickname}</dd></div><div><dt>状态</dt><dd><StatusTag value={account.status === 'ACTIVE' ? '正常' : '已停用'}/></dd></div><div><dt>邮箱 / 手机</dt><dd>{account.email || account.phone || '—'}</dd></div><div><dt>关联内容</dt><dd>{account.orders} 单 · {account.posts} 帖 · {account.comments} 条评论 · {account.likes} 次点赞</dd></div></dl><AccountContent key={account.id} detail={account}/><button className="secondary" onClick={() => run(async () => { await api(`/users/${encodeURIComponent(account.id)}`, { method: 'PUT', body: JSON.stringify({ nickname: account.nickname, status: account.status === 'ACTIVE' ? '已停用' : '正常' }) }); await showAccount(account.id); }, account.status === 'ACTIVE' ? '账户已停用' : '账户已启用')}>{account.status === 'ACTIVE' ? '停用账户' : '启用账户'}</button></div></Modal> : null}
 {toast ? <Toast message={toast} onClose={() => setToast('')}/> : null}</>;
}
