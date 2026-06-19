import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp } from 'antd'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { UpgradeContent } from '../components/shared/UpgradeContent'
import { showUpgradeHint } from '../utils/constants'
import './PrivateSpace.css'

function MobileUpgradePage() {
  const navigate = useNavigate()
  const { modal } = AntApp.useApp()

  const handleConfirm = useCallback((plan) => {
    if (!plan) return
    showUpgradeHint(modal)
  }, [modal])

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
