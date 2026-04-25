import {Routes, Route} from 'react-router-dom'
import GlobalLayout from './components/GlobalLayout'
import HomePage from './pages/HomePage'
import NotFound from './pages/NotFound'
import UserManagement from './pages/UserManagement'
import AdminUserList from './pages/AdminUserList'
import UserProfile from './pages/UserProfile'
import SpaceManagement from './pages/SpaceManagement'
import TeamManagement from './pages/TeamManagement'
import AIManagement from './pages/AIManagement'
import CommunitySquare from './pages/CommunitySquare'
import PrivateSpace from './pages/PrivateSpace'
import TeamSpace from './pages/TeamSpace'
import Notifications from './pages/Notifications'

function App() {
  return (
    <GlobalLayout>
      <Routes>
        <Route path="/" element={<HomePage/>}/>
        <Route path="/profile" element={<UserProfile/>}/>
        <Route path="/community" element={<CommunitySquare/>}/>
        <Route path="/private-space" element={<PrivateSpace/>}/>
        <Route path="/team-space" element={<TeamSpace/>}/>
        <Route path="/notifications" element={<Notifications/>}/>
        <Route path="/admin/users" element={<UserManagement/>}/>
          <Route path="/admin/spaces" element={<SpaceManagement/>}/>
          <Route path="/admin/teams" element={<TeamManagement/>}/>
          <Route path="/admin/ai" element={<AIManagement/>}/>
        <Route path="/api/user/admin/userList" element={<AdminUserList/>}/>
        <Route path="/404" element={<NotFound/>}/>
        <Route path="*" element={<NotFound/>}/>
      </Routes>
    </GlobalLayout>
  )
}

export default App
