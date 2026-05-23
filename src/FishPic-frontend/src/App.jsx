import { lazy, Suspense } from 'react'
import { Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import GlobalLayout from './components/GlobalLayout'
import ProtectedRoute from './components/ProtectedRoute'
import { Spin } from 'antd'
import './App.css'

const HomePage = lazy(() => import('./pages/HomePage'))
const NotFound = lazy(() => import('./pages/NotFound'))
const UserManagement = lazy(() => import('./pages/UserManagement'))
const AdminUserList = lazy(() => import('./pages/AdminUserList'))
const UserProfile = lazy(() => import('./pages/UserProfile'))
const SpaceManagement = lazy(() => import('./pages/SpaceManagement'))
const TeamManagement = lazy(() => import('./pages/TeamManagement'))
const AIImageTools = lazy(() => import('./pages/AIImageTools'))
const AIManagement = lazy(() => import('./pages/AIManagement'))
const SystemManagement = lazy(() => import('./pages/SystemManagement'))
const AdminPictureManagement = lazy(() => import('./pages/AdminPictureManagement'))
const AdminCommentManagement = lazy(() => import('./pages/AdminCommentManagement'))
const AdminPostManagement = lazy(() => import('./pages/AdminPostManagement'))
const CommunitySquare = lazy(() => import('./pages/CommunitySquare'))
const PrivateSpace = lazy(() => import('./pages/PrivateSpace'))
const TeamSpace = lazy(() => import('./pages/TeamSpace'))
const TeamSpaceDetail = lazy(() => import('./pages/TeamSpaceDetail'))
const Notifications = lazy(() => import('./pages/Notifications'))
const MobileLoginPage = lazy(() => import('./pages/MobileLoginPage'))
const MobileRegisterPage = lazy(() => import('./pages/MobileRegisterPage'))
const MobilePostCreatePage = lazy(() => import('./pages/MobilePostCreatePage'))
const MobilePostDetailPage = lazy(() => import('./pages/MobilePostDetailPage'))
const MobileEditPicturePage = lazy(() => import('./pages/MobileEditPicturePage'))
const MobileEditProfilePage = lazy(() => import('./pages/MobileEditProfilePage'))
const MobileUpgradePage = lazy(() => import('./pages/MobileUpgradePage'))
const MobileFollowListPage = lazy(() => import('./pages/MobileFollowListPage'))
const MobileUserProfilePage = lazy(() => import('./pages/MobileUserProfilePage'))

function PageLoading() {
  return (
    <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh' }}>
      <Spin size="large" />
    </div>
  )
}

function App() {
  return (
    <AuthProvider>
      <Suspense fallback={<PageLoading />}>
        <Routes>
          <Route path="/mobile/login" element={<MobileLoginPage />} />
          <Route path="/mobile/register" element={<MobileRegisterPage />} />
          <Route path="/mobile/post/create" element={<ProtectedRoute><MobilePostCreatePage /></ProtectedRoute>} />
          <Route path="/mobile/post/detail/:postId" element={<MobilePostDetailPage />} />
          <Route path="/mobile/picture/edit" element={<ProtectedRoute><MobileEditPicturePage /></ProtectedRoute>} />
          <Route path="/mobile/profile/edit" element={<ProtectedRoute><MobileEditProfilePage /></ProtectedRoute>} />
          <Route path="/mobile/upgrade" element={<ProtectedRoute><MobileUpgradePage /></ProtectedRoute>} />
          <Route path="/mobile/follow-list" element={<ProtectedRoute><MobileFollowListPage /></ProtectedRoute>} />
          <Route path="/mobile/profile" element={<ProtectedRoute><MobileUserProfilePage /></ProtectedRoute>} />
          <Route path="*" element={
            <GlobalLayout>
              <Routes>
                <Route path="/" element={<HomePage />} />
                <Route path="/profile" element={<ProtectedRoute><UserProfile /></ProtectedRoute>} />
                <Route path="/community" element={<CommunitySquare />} />
                <Route path="/private-space" element={<ProtectedRoute><PrivateSpace /></ProtectedRoute>} />
                <Route path="/team-space" element={<ProtectedRoute><TeamSpace /></ProtectedRoute>} />
                <Route path="/team-space/:id" element={<ProtectedRoute><TeamSpaceDetail /></ProtectedRoute>} />
                <Route path="/notifications" element={<ProtectedRoute><Notifications /></ProtectedRoute>} />
                <Route path="/ai-tools" element={<ProtectedRoute><AIImageTools /></ProtectedRoute>} />
                <Route path="/admin/users" element={<ProtectedRoute requireAdmin><UserManagement /></ProtectedRoute>} />
                <Route path="/admin/pictures" element={<ProtectedRoute requireAdmin><AdminPictureManagement /></ProtectedRoute>} />
                <Route path="/admin/comments" element={<ProtectedRoute requireAdmin><AdminCommentManagement /></ProtectedRoute>} />
                <Route path="/admin/posts" element={<ProtectedRoute requireAdmin><AdminPostManagement /></ProtectedRoute>} />
                <Route path="/admin/spaces" element={<ProtectedRoute requireAdmin><SpaceManagement /></ProtectedRoute>} />
                <Route path="/admin/teams" element={<ProtectedRoute requireAdmin><TeamManagement /></ProtectedRoute>} />
                <Route path="/admin/ai" element={<ProtectedRoute requireAdmin><AIManagement /></ProtectedRoute>} />
                <Route path="/admin/system" element={<ProtectedRoute requireAdmin><SystemManagement /></ProtectedRoute>} />
                <Route path="/admin/user-list" element={<ProtectedRoute requireAdmin><AdminUserList /></ProtectedRoute>} />
                <Route path="/404" element={<NotFound />} />
                <Route path="*" element={<NotFound />} />
              </Routes>
            </GlobalLayout>
          } />
        </Routes>
      </Suspense>
    </AuthProvider>
  )
}

export default App
