import { SyncStatus } from './SyncStatus';
import { ReactNode, useEffect, useState } from 'react';
import { NavLink, useLocation, useNavigate } from 'react-router-dom';
import { Bell, BookOpen, ChevronDown, ChevronLeft, ChevronRight, ClipboardList, FolderTree, Gauge, LogOut, Menu, MessageSquare, PackageCheck, Settings, ShieldCheck, ShoppingBag, Star, Store, Users } from 'lucide-react';
import { api, realtime, session } from '../api';
import { applyAppearance, readAppearance } from '../theme';

const navGroups = [
  { label: '', items: [{ to: '/', label: '工作台', icon: Gauge }] },
  { label: '账号管理', items: [{ to: '/users', label: '用户管理', icon: Users }, { to: '/merchants', label: '商家管理', icon: Store }] },
  { label: '商品管理', items: [{ to: '/categories', label: '商品分类', icon: FolderTree }, { to: '/reviews', label: '商品审核', icon: PackageCheck }, { to: '/products', label: '商品列表', icon: BookOpen }] },
  { label: '交易管理', items: [{ to: '/orders', label: '订单管理', icon: ShoppingBag }, { to: '/after-sales', label: '售后管理', icon: PackageCheck }, { to: '/ratings', label: '评价管理', icon: Star }] },
  { label: '内容管理', items: [{ to: '/community', label: '社区内容', icon: MessageSquare }, { to: '/feedback', label: '留言反馈', icon: MessageSquare }, { to: '/notices', label: '通知中心', icon: Bell }] },
  { label: '系统管理', items: [{ to: '/logs', label: '操作日志', icon: ClipboardList }, { to: '/settings', label: '系统设置', icon: Settings }] },
];

export function AppShell({ children }: { children: ReactNode }) {
  const [collapsed, setCollapsed] = useState(() => window.innerWidth <= 1100);
  const [adminName,setAdminName]=useState('管理员');
  const location = useLocation();
  const [expandedGroups,setExpandedGroups]=useState<Record<string,boolean>>(()=>{
    let saved:Record<string,boolean>={};
    try{saved=JSON.parse(localStorage.getItem('huimeng:sidebar-groups:v1')||'{}');}catch{ /* use the current route */ }
    const active=navGroups.find(group=>group.label&&group.items.some(item=>item.to===window.location.pathname));
    return {...saved,...(active?{[active.label]:true}:{})};
  });
  useEffect(()=>{
    const active=navGroups.find(group=>group.label&&group.items.some(item=>item.to===location.pathname));
    if(active)setExpandedGroups(value=>value[active.label]?value:{...value,[active.label]:true});
  },[location.pathname]);
  useEffect(()=>{try{localStorage.setItem('huimeng:sidebar-groups:v1',JSON.stringify(expandedGroups));}catch{ /* storage is optional */ }},[expandedGroups]);
  const navigate = useNavigate();
  const current = location.pathname === '/account' ? '账户安全' : navGroups.flatMap(group => group.items).find(item => item.to === location.pathname)?.label ?? '经营工作台';
  useEffect(() => { applyAppearance(readAppearance()); }, []);
  useEffect(()=>{api<{user:{name:string}}>('/auth/me').then(result=>setAdminName(result.user.name)).catch(()=>{})},[]);

  useEffect(()=>realtime(()=>{}),[]);
  const logout = async () => {
    try { await api('/auth/logout', { method: 'POST' }); } catch { /* 本地服务中断时仍允许退出 */ }
    session.clear();
    navigate('/login');
  };

  return <div className={`app ${collapsed ? 'is-collapsed' : ''}`}>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark"><img src="/images/app_icon-c3.webp" alt="绘梦儿童管理网站图标" /></span><span className="brand-copy"><strong>绘梦儿童管理网站</strong><small>让好绘本，陪伴更多孩子</small></span></div>
      <nav>
        {navGroups.map((group,index) => {
          const expanded=!group.label||Boolean(expandedGroups[group.label]),GroupIcon=group.items[0].icon;
          const active=group.items.some(item=>item.to===location.pathname);
          return <div className={`nav-group ${active?'is-current':''}`} key={group.label || 'main'}>
            {group.label?<button type="button" className="sidebar-group-trigger" title={group.label} aria-label={group.label} aria-expanded={expanded} aria-controls={`nav-group-${index}`} onClick={()=>{
              if(collapsed){setCollapsed(false);setExpandedGroups(value=>({...value,[group.label]:true}));}
              else setExpandedGroups(value=>({...value,[group.label]:!value[group.label]}));
            }}><GroupIcon size={18} aria-hidden="true"/><span>{group.label}</span><ChevronDown size={16} className={expanded?'is-open':''} aria-hidden="true"/></button>:null}
            <div id={`nav-group-${index}`} className={group.label?'sidebar-submenu':''} hidden={!expanded}>
              {group.items.map(({ to, label, icon: Icon }) => <NavLink key={to} to={to} end={to === '/'} aria-label={label} title={label} onClick={()=>{if(window.innerWidth<=1100)setCollapsed(true);}}><Icon size={18} /><span>{label}</span></NavLink>)}
            </div>
          </div>;
        })}
      </nav>
      <div className="sidebar-art"><img src="/images/app_icon-c3.webp" alt="绘梦软件图标" /><span>用绘本<br />点亮孩子更大的世界</span></div>
    </aside>
    {!collapsed?<button className="sidebar-backdrop" aria-label="关闭导航菜单" onClick={()=>setCollapsed(true)}/>:null}
    <header className="topbar">
      <button className="icon-button" onClick={() => setCollapsed(value => !value)} aria-label={collapsed ? '展开侧栏' : '收起侧栏'}>{collapsed ? <ChevronRight /> : <Menu />}</button>
      <div className="breadcrumb">工作台 <span>/</span> <strong>{current}</strong></div>
      <div className="top-actions"><SyncStatus/><button className="notification" aria-label="通知中心" onClick={() => navigate('/notices')}><Bell size={20} /></button><button className="admin admin-button" onClick={() => navigate('/account')}><span><ShieldCheck size={18} /></span><strong>{adminName}</strong></button><button className="logout" onClick={logout}><LogOut size={18} />退出登录</button></div>
    </header>
    <main>{children}</main>
    <button className="collapse-fab" onClick={() => setCollapsed(value => !value)} aria-label="切换侧栏"><ChevronLeft size={17} /></button>
  </div>;
}
