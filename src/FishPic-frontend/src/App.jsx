import {Routes, Route} from 'react-router-dom'
import { AuthProvider } from './context/AuthContext.jsx'
import GlobalLayout from './components/GlobalLayout'
import ProtectedRoute from './components/ProtectedRoute'
import HomePage from './pages/HomePage'
import NotFound from './pages/NotFound'
import UserManagement from './pages/UserManagement'
import AdminUserList from './pages/AdminUserList'
import UserProfile from './pages/UserProfile'
import SpaceManagement from './pages/SpaceManagement'
import TeamManagement from './pages/TeamManagement'
import AIManagement from './pages/AIManagement'
import SystemManagement from './pages/SystemManagement'
import AdminPictureManagement from './pages/AdminPictureManagement'
import CommunitySquare from './pages/CommunitySquare'
import PrivateSpace from './pages/PrivateSpace'
import TeamSpace from './pages/TeamSpace'
import Notifications from './pages/Notifications'

function App() {
  return (
    <AuthProvider>
      <GlobalLayout>
        <Routes>
          <Route path="/" element={<HomePage/>}/>
          <Route path="/profile" element={<ProtectedRoute><UserProfile/></ProtectedRoute>}/>
          <Route path="/community" element={<CommunitySquare/>}/>
          <Route path="/private-space" element={<ProtectedRoute><PrivateSpace/></ProtectedRoute>}/>
          <Route path="/team-space" element={<ProtectedRoute><TeamSpace/></ProtectedRoute>}/>
          <Route path="/notifications" element={<ProtectedRoute><Notifications/></ProtectedRoute>}/>
          <Route path="/admin/users" element={<ProtectedRoute requireAdmin><UserManagement/></ProtectedRoute>}/>
          <Route path="/admin/pictures" element={<ProtectedRoute requireAdmin><AdminPictureManagement/></ProtectedRoute>}/>
          <Route path="/admin/spaces" element={<ProtectedRoute requireAdmin><SpaceManagement/></ProtectedRoute>}/>
          <Route path="/admin/teams" element={<ProtectedRoute requireAdmin><TeamManagement/></ProtectedRoute>}/>
          <Route path="/admin/ai" element={<ProtectedRoute requireAdmin><AIManagement/></ProtectedRoute>}/>
          <Route path="/admin/system" element={<ProtectedRoute requireAdmin><SystemManagement/></ProtectedRoute>}/>
          <Route path="/admin/user-list" element={<ProtectedRoute requireAdmin><AdminUserList/></ProtectedRoute>}/>
          <Route path="/404" element={<NotFound/>}/>
          <Route path="*" element={<NotFound/>}/>
        </Routes>
      </GlobalLayout>
    </AuthProvider>
  )
}

export default App
