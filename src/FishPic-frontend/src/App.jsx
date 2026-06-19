import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import GlobalLayout from './components/GlobalLayout'
import ProtectedRoute from './components/ProtectedRoute'
import ErrorBoundary from './components/ErrorBoundary'
import { Spin, Result, Button } from 'antd'
import './App.css'

const HomePage = lazy(() => import('./pages/HomePage'))
const NotFound = lazy(() => import('./pages/NotFound'))
const UserManagement = lazy(() => import('./pages/UserManagement'))
const UserProfile = lazy(() => import('./pages/UserProfile'))
const SpaceManagement = lazy(() => import('./pages/SpaceManagement'))
const AIImageTools = lazy(() => import('./pages/AIImageTools'))
const AIManagement = lazy(() => import('./pages/AIManagement'))
const SystemManagement = lazy(() => import('./pages/SystemManagement'))
const AdminDashboard = lazy(() => import('./pages/AdminDashboard'))
const AuditLogManagement = lazy(() => import('./pages/AuditLogManagement'))
const AdminPictureManagement = lazy(() => import('./pages/AdminPictureManagement'))
const PrivateSpace = lazy(() => import('./pages/PrivateSpace'))
const TeamSpace = lazy(() => import('./pages/TeamSpace'))
const TeamSpaceDetail = lazy(() => import('./pages/TeamSpaceDetail'))
const MobileLoginPage = lazy(() => import('./pages/MobileLoginPage'))
const MobileRegisterPage = lazy(() => import('./pages/MobileRegisterPage'))
const MobileEditPicturePage = lazy(() => import('./pages/MobileEditPicturePage'))
const MobileEditProfilePage = lazy(() => import('./pages/MobileEditProfilePage'))
const MobileUpgradePage = lazy(() => import('./pages/MobileUpgradePage'))
const MobileUserProfilePage = lazy(() => import('./pages/MobileUserProfilePage'))
const MobileSaveToSpacePage = lazy(() => import('./pages/MobileSaveToSpacePage'))
const SharePage = lazy(() => import('./pages/SharePage'))

function PageLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
      <Spin size="large" />
    </div>
  )
}

// 路由级错误回退组件
function RouteErrorFallback() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
      <Result
        status="error"
        title="页面加载失败"
        subTitle="抱歉，该页面出现了错误，请尝试刷新页面或返回首页"
        extra={[
          <Button key="home" onClick={() => window.location.href = '/'}>
            返回首页
          </Button>,
          <Button key="refresh" type="primary" onClick={() => window.location.reload()}>
            刷新页面
          </Button>
        ]}
      />
    </div>
  )
}

// 路由级错误边界，懒加载组件用
function RouteWithBoundary({ children }) {
  return (
    <ErrorBoundary fallback={<RouteErrorFallback />}>
      <Suspense fallback={<PageLoading />}>
        {children}
      </Suspense>
    </ErrorBoundary>
  )
}

function App() {
  return (
    <AuthProvider>
      <Routes>
        <Route path="/s/:token" element={<RouteWithBoundary><SharePage /></RouteWithBoundary>} />
        {/* 移动端页面 - 使用路由级错误边界 */}
        <Route path="/mobile/login" element={<RouteWithBoundary><MobileLoginPage /></RouteWithBoundary>} />
        <Route path="/mobile/register" element={<RouteWithBoundary><MobileRegisterPage /></RouteWithBoundary>} />
        <Route path="/mobile/picture/edit" element={<RouteWithBoundary><ProtectedRoute><MobileEditPicturePage /></ProtectedRoute></RouteWithBoundary>} />
        <Route path="/mobile/profile/edit" element={<RouteWithBoundary><ProtectedRoute><MobileEditProfilePage /></ProtectedRoute></RouteWithBoundary>} />
        <Route path="/mobile/upgrade" element={<RouteWithBoundary><ProtectedRoute><MobileUpgradePage /></ProtectedRoute></RouteWithBoundary>} />
        <Route path="/mobile/profile" element={<RouteWithBoundary><ProtectedRoute><MobileUserProfilePage /></ProtectedRoute></RouteWithBoundary>} />
        <Route path="/mobile/save-to-space" element={<RouteWithBoundary><ProtectedRoute><MobileSaveToSpacePage /></ProtectedRoute></RouteWithBoundary>} />

        {/* 桌面端页面 - 使用路由级错误边界 */}
        <Route path="*" element={
          <GlobalLayout>
            <Routes>
              <Route path="/" element={<RouteWithBoundary><HomePage /></RouteWithBoundary>} />
              
              <Route path="/profile" element={<RouteWithBoundary><ProtectedRoute><UserProfile /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/private-space" element={<RouteWithBoundary><ProtectedRoute><PrivateSpace /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/team-space" element={<RouteWithBoundary><ProtectedRoute><TeamSpace /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/team-space/:id" element={<RouteWithBoundary><ProtectedRoute><TeamSpaceDetail /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/ai-tools" element={<RouteWithBoundary><ProtectedRoute><AIImageTools /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/users" element={<RouteWithBoundary><ProtectedRoute permission="system:user:manage"><UserManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/pictures" element={<RouteWithBoundary><ProtectedRoute permission="system:log:manage"><AdminPictureManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/spaces" element={<RouteWithBoundary><ProtectedRoute permission="system:team:manage"><SpaceManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/dashboard" element={<RouteWithBoundary><ProtectedRoute permission="system:log:manage"><AdminDashboard /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/audit-logs" element={<RouteWithBoundary><ProtectedRoute permission="system:log:manage"><AuditLogManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/ai" element={<RouteWithBoundary><ProtectedRoute permission="system:ai:manage"><AIManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/admin/system" element={<RouteWithBoundary><ProtectedRoute permission="system:config"><SystemManagement /></ProtectedRoute></RouteWithBoundary>} />
              <Route path="/404" element={<RouteWithBoundary><NotFound /></RouteWithBoundary>} />
              <Route path="*" element={<RouteWithBoundary><NotFound /></RouteWithBoundary>} />
            </Routes>
          </GlobalLayout>
        } />
      </Routes>
    </AuthProvider>
  )
}

export default App
