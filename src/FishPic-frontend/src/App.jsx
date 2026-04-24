import {Routes, Route} from 'react-router-dom'
import HomePage from './pages/HomePage'
import NotFound from './pages/NotFound'
import UserManagement from './pages/UserManagement'
import AdminUserList from './pages/AdminUserList'

function App() {
  return (
      <Routes>
          <Route path="/" element={<HomePage/>}/>
          <Route path="/admin/users" element={<UserManagement/>}/>
          <Route path="/api/user/admin/userList" element={<AdminUserList/>}/>
          <Route path="/404" element={<NotFound/>}/>
          <Route path="*" element={<NotFound/>}/>
      </Routes>
  )
}

export default App
