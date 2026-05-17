import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { UpgradeContent } from '../components/shared/UpgradeContent'
import './PrivateSpace.css'

function MobileUpgradePage() {
  const navigate = useNavigate()
  const { message } = AntApp.useApp()

  const handleConfirm = useCallback((plan) => {
    if (!plan) return
    message.success('升级申请已提交，等待审核')
    navigate(-1)
  }, [navigate, message])

  return (
    <MobilePageWrapper title="升级空间" showBack={false}>
      <div className="upgrade-page">
        <UpgradeContent
          onConfirm={handleConfirm}
          onCancel={() => navigate(-1)}
          cancelButtonText="返回"
        />
      </div>
    </MobilePageWrapper>
  )
}

export default MobileUpgradePage
