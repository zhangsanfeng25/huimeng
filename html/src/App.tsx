import { lazy, Suspense } from 'react';
import { Navigate, Outlet, Route, Routes } from 'react-router-dom';
const AppShell = lazy(() => import('./components/AppShell').then(module => ({ default:module.AppShell })));
const DashboardPage = lazy(() => import('./pages/DashboardPage').then(module => ({ default:module.DashboardPage })));
import { LoginPage } from './pages/LoginPage';
const ManagementPage = lazy(() => import('./pages/ManagementPage').then(module => ({ default:module.ManagementPage })));
const CommunityPage = lazy(() => import('./pages/CommunityPage').then(module => ({ default:module.CommunityPage })));
const SettingsPage = lazy(() => import('./pages/SettingsPage').then(module => ({ default:module.SettingsPage })));
const AccountPage = lazy(() => import('./pages/AccountPage').then(module => ({ default:module.AccountPage })));
const ServicePage = lazy(() => import('./pages/ServicePage').then(module => ({default:module.ServicePage})));
const AiManagementPage = lazy(() => import('./pages/AiManagementPage').then(module => ({default:module.AiManagementPage})));
import { pageConfigs } from './data';
import { session } from './api';

function Protected() {
  return session.get() ? <AppShell><Suspense fallback={<div className="route-loading" role="status">正在加载…</div>}><Outlet/></Suspense></AppShell> : <Navigate to="/login" replace />;
}

export default function App() {
  return <Suspense fallback={<div className="route-loading" role="status">正在加载…</div>}><Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<Protected />}>
      <Route path="/" element={<DashboardPage />} />
      {(Object.keys(pageConfigs) as Array<keyof typeof pageConfigs>).map(type => <Route key={type} path={`/${type}`} element={<ManagementPage type={type} />} />)}
      <Route path="/community" element={<CommunityPage />} />
      <Route path="/settings" element={<SettingsPage />} />
      <Route path="/ai" element={<AiManagementPage />} />
      <Route path="/account" element={<AccountPage />} />
      <Route path="/after-sales" element={<ServicePage key="after-sales" kind="after-sales" />} />
      <Route path="/feedback" element={<ServicePage key="feedback" kind="feedback" />} />
    </Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes></Suspense>;
}
