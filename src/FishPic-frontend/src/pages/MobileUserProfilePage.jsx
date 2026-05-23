import { useNavigate } from 'react-router-dom'
import MobilePageWrapper from '../components/MobilePageWrapper'
import UserProfile from './UserProfile'

export default function MobileUserProfilePage() {
  const navigate = useNavigate()

  return (
    <MobilePageWrapper title="用户主页" showBack onClose={() => navigate(-1)}>
      <UserProfile />
    </MobilePageWrapper>
  )
}
