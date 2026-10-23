import { FormEvent, useEffect, useState } from 'react';
import { Eye, EyeOff, Images, LockKeyhole, UserRound } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { api, session } from '../api';
import { applyAppearance, readAppearance } from '../theme';

const LOGIN_WALLPAPERS = [
  { name: '梦幻紫蓝绘本夜景', src: '/login-wallpapers/dream_night.png' },
  { name: '清新薄荷森林', src: '/login-wallpapers/mint_forest.png' },
  { name: '温暖奶油晨光', src: '/login-wallpapers/cream_sunrise.png' },
  { name: '深蓝星空科技童话', src: '/login-wallpapers/cosmic_library.png' },
] as const;

const WALLPAPER_STORAGE_KEY = 'huimeng.login-wallpaper.v1';

function readWallpaperIndex() {
  try {
    const saved = Number.parseInt(localStorage.getItem(WALLPAPER_STORAGE_KEY) ?? '0', 10);
    return Number.isInteger(saved) && saved >= 0 && saved < LOGIN_WALLPAPERS.length ? saved : 0;
  } catch {
    return 0;
  }
}

export function LoginPage() {
  const [account, setAccount] = useState('admin');
  const [password, setPassword] = useState('');
  const [remember, setRemember] = useState(false);
  const [show, setShow] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [recover,setRecover]=useState(false),[contact,setContact]=useState(''),[replacement,setReplacement]=useState(''),[confirmation,setConfirmation]=useState(''),[notice,setNotice]=useState('');
  const [wallpaperIndex, setWallpaperIndex] = useState(readWallpaperIndex);
  const navigate = useNavigate();
  const wallpaper = LOGIN_WALLPAPERS[wallpaperIndex];

  useEffect(() => {
    applyAppearance(readAppearance());
    if (session.get()) navigate('/', { replace: true });
  }, [navigate]);

  useEffect(() => {
    const nextIndex = (wallpaperIndex + 1) % LOGIN_WALLPAPERS.length;
    const preload = new window.Image();
    preload.src = LOGIN_WALLPAPERS[nextIndex].src;
  }, [wallpaperIndex]);

  const switchWallpaper = () => {
    setWallpaperIndex(current => {
      const next = (current + 1) % LOGIN_WALLPAPERS.length;
      try { localStorage.setItem(WALLPAPER_STORAGE_KEY, String(next)); } catch { /* Storage may be unavailable. */ }
      return next;
    });
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    try {
      setLoading(true);
      setError('');
      const result = await api<{ token: string }>('/auth/login', {
        method: 'POST',
        body: JSON.stringify({ username: account, password }),
      });
      session.set(result.token, remember);
      navigate('/');
    } catch (reason) {
      setError((reason as Error).message);
    } finally {
      setLoading(false);
    }
  };
  const resetPassword=async(event:FormEvent)=>{
    event.preventDefault();setError('');
    if(replacement!==confirmation){setError('两次输入的密码不一致');return;}
    setLoading(true);
    try{await api('/auth/forgot-password',{method:'POST',body:JSON.stringify({account:contact.trim(),newPassword:replacement})});session.clear();setRecover(false);setReplacement('');setConfirmation('');setPassword('');setNotice('密码已重置，请使用新密码登录。');}
    catch(reason){setError((reason as Error).message);}finally{setLoading(false);}
  };

  return <div className="login-page">
    <div key={wallpaper.src} className="login-wallpaper" style={{ backgroundImage: `url(${wallpaper.src})` }} aria-hidden="true" />
    <section className="login-visual" aria-hidden="true" />
    <button
      className="login-wallpaper-switch"
      type="button"
      onClick={switchWallpaper}
      title={`当前：${wallpaper.name}，点击切换下一张`}
      aria-label={`切换登录壁纸，当前为${wallpaper.name}`}
    >
      <Images size={18} /><span>切换壁纸</span>
    </button>
    <section className="login-panel">
      {recover?<form onSubmit={resetPassword} aria-label="找回密码">
        <div className="login-title"><h2>找回密码</h2></div>
        <p>本地演示：通过账号已保存的邮箱或手机号直接重置。</p>
        <label>邮箱／手机号<div className="input-wrap"><UserRound size={19}/><input required value={contact} onChange={e=>setContact(e.target.value)} autoComplete="username"/></div></label>
        <label>新密码<div className="input-wrap"><LockKeyhole size={19}/><input required type="password" minLength={8} maxLength={64} value={replacement} onChange={e=>setReplacement(e.target.value)} autoComplete="new-password"/></div></label>
        <label>确认新密码<div className="input-wrap"><LockKeyhole size={19}/><input required type="password" minLength={8} maxLength={64} value={confirmation} onChange={e=>setConfirmation(e.target.value)} autoComplete="new-password"/></div></label>
        {error?<p className="form-error" role="alert">{error}</p>:null}
        <button className="login-button" disabled={loading}>{loading?'正在重置…':'确认重置'}</button>
        <button className="link" type="button" disabled={loading} onClick={()=>{setRecover(false);setError('');}}>返回登录</button>
      </form>:<form onSubmit={submit} aria-label="管理员登录">
        <div className="login-title"><h2>管理员登录</h2></div>
        {notice?<p role="status">{notice}</p>:null}
        <label>账号<div className="input-wrap"><UserRound size={19} /><input value={account} onChange={event => setAccount(event.target.value)} autoComplete="username" /></div></label>
        <label>密码<div className="input-wrap"><LockKeyhole size={19} /><input type={show ? 'text' : 'password'} value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" placeholder="请输入管理员密码" required /><button type="button" onClick={() => setShow(value => !value)} aria-label={show ? '隐藏密码' : '显示密码'}>{show ? <EyeOff size={18} /> : <Eye size={18} />}</button></div></label>
        {error ? <p className="form-error">{error}</p> : null}
        <label className="remember"><input type="checkbox" checked={remember} onChange={event => setRemember(event.target.checked)} />记住登录状态</label>
        <button className="login-button" disabled={loading}>{loading ? '正在登录…' : '登录管理网站'}</button>
        <button className="link" type="button" disabled={loading} onClick={()=>{setRecover(true);setError('');setNotice('');}}>忘记密码</button>
      </form>}
    </section>
  </div>;
}
