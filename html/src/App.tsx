import { lazy, Suspense } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
const AppShell = lazy(() => import('./components/AppShell').then(module => ({ default:module.AppShell })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(module => ({ default:module.DashboardPage })));
import { LoginPage } from './pages/LoginPage';
const ManagementPage = lazy(() => import('./pages/ManagementPage').then(module => ({ default:module.ManagementPage })));
const CommunityPage = lazy(() => import('./pages/CommunityPage').then(module => ({ default:module.CommunityPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(module => ({ default:module.SettingsPage })));
const AccountPage = lazy(() => import('./pages/AccountPage').then(module => ({ default:module.AccountPage })));
import { pageConfigs } from './data';
import { session } from './api';

function Protected({ children }: { children: React.ReactNode }) {
  return session.get() ? <AppShell><Suspense fallback={<div className="route-loading" role="status">正在加载…</div>}>{children}</Suspense></AppShell> : <Navigate to="/login" replace />;
}

export default function App() {
  return <Suspense fallback={<div className="route-loading" role="status">正在加载…</div>}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route path="/" element={<Protected><DashboardPage /></Protected>} />
    {(Object.keys(pageConfigs) as Array<keyof typeof pageConfigs>).map(type => <Route key={type} path={`/${type}`} element={<Protected><ManagementPage type={type} /></Protected>} />)}
    <Route path="/community" element={<Protected><CommunityPage /></Protected>} />
    <Route path="/settings" element={<Protected><SettingsPage /></Protected>} />
    <Route path="/account" element={<Protected><AccountPage /></Protected>} />
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></Suspense>;
}
