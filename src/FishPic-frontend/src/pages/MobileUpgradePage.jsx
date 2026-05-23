import { useCallback } from 'react'
import { useNavigate } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { ArrowLeftOutlined } from '@ant-design/icons'
import MobilePageWrapper from '../components/MobilePageWrapper'
import { UpgradeContent } from '../components/shared/UpgradeContent'
import './PrivateSpace.css'

function MobileUpgradePage() {
  const navigate = useNavigate()
  const { modal } = AntApp.useApp()

  const handleConfirm = useCallback((plan) => {
    if (!plan) return
    modal.info({
      title: '升级会员',
      content: '请联系管理员开通 VIP/SVIP 会员',
      okText: '知道了',
    })
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
