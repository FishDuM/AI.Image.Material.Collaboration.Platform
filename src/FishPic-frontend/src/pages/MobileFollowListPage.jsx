import { useSearchParams, useNavigate } from 'react-router-dom'
import MobilePageWrapper from '../components/MobilePageWrapper'
import FollowUserList from '../components/FollowUserList'

export default function MobileFollowListPage() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const type = searchParams.get('type') || 'follows'
  const userId = searchParams.get('userId')
  const title = type === 'fans' ? '粉丝' : '关注'

  const handleUserClick = (clickedUserId) => {
    navigate(`/mobile/profile?userId=${clickedUserId}`)
  }

  return (
    <MobilePageWrapper title={title}>
      <FollowUserList type={type} targetUserId={userId} onUserClick={handleUserClick} />
    </MobilePageWrapper>
  )
}
