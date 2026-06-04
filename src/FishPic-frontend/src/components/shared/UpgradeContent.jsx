import { useState } from 'react'
import { Button } from 'antd'
import { CrownOutlined, CloudServerOutlined, CheckCircleFilled, ArrowLeftOutlined } from '@ant-design/icons'

const UPGRADE_PLANS = [
  {
    key: 'vip',
    name: 'VIP',
    price: '¥9.9',
    period: '/月',
    level: 1,
    features: ['5 GB 专属存储空间', '支持上传单张 5 MB 图片', '优先审核通过', '专属客服通道'],
  },
  {
    key: 'svip',
    name: 'SVIP',
    price: '¥19.9',
    period: '/月',
    level: 2,
    hot: true,
    features: ['10 GB 专属存储空间', '支持上传单张 20 MB 图片', '极速审核通过', '专属客服通道', '优先体验新功能'],
  },
]

const ADDON_PLANS = [
  { key: 'addon-1g', name: '+1 GB', size: '1 GB', price: '¥1.0', period: '/月' },
  { key: 'addon-5g', name: '+5 GB', size: '5 GB', price: '¥3.9', period: '/月' },
  { key: 'addon-10g', name: '+10 GB', size: '10 GB', price: '¥6.9', period: '/月' },
]

function UpgradeContent({ onConfirm, onCancel, cancelButtonText }) {
  const [selectedPlan, setSelectedPlan] = useState(null)

  const handleConfirm = () => {
    if (!selectedPlan) return
    if (onConfirm) {
      onConfirm(selectedPlan)
    }
  }

  return (
    <>
      <div className="upgrade-header">
        <h2 className="upgrade-title">升级空间</h2>
        <p className="upgrade-subtitle">解锁更多存储，享受专属特权</p>
      </div>

      <div className="upgrade-section">
        <div className="upgrade-section-title">
          <CrownOutlined className="upgrade-section-icon" />
          <span>会员套餐</span>
        </div>
        <div className="upgrade-plan-grid">
          {UPGRADE_PLANS.map((plan) => (
            <div
              key={plan.key}
              className={`upgrade-plan-card ${plan.hot ? 'upgrade-plan-hot' : ''} ${selectedPlan === plan.key ? 'upgrade-plan-selected' : ''} level-${plan.key}`}
              onClick={() => setSelectedPlan(plan.key)}
            >
              {plan.hot && <div className="upgrade-hot-badge">推荐</div>}
              <div className="upgrade-plan-name">{plan.name}</div>
              <div className="upgrade-plan-price">
                <span className="upgrade-price-amount">{plan.price}</span>
                <span className="upgrade-price-period">{plan.period}</span>
              </div>
              <div className="upgrade-plan-features">
                {plan.features.map((f, i) => (
                  <div key={i} className="upgrade-feature-item">
                    <CheckCircleFilled className="upgrade-feature-check" />
                    <span>{f}</span>
                  </div>
                ))}
              </div>
              <Button
                type={selectedPlan === plan.key ? 'primary' : 'default'}
                block
                className="upgrade-plan-btn"
              >
                {selectedPlan === plan.key ? '已选择' : '选择套餐'}
              </Button>
            </div>
          ))}
        </div>
      </div>

      <div className="upgrade-section">
        <div className="upgrade-section-title">
          <CloudServerOutlined className="upgrade-section-icon" />
          <span>空间增量包</span>
        </div>
        <div className="upgrade-addon-grid">
          {ADDON_PLANS.map((addon) => (
            <div
              key={addon.key}
              className={`upgrade-addon-card ${selectedPlan === addon.key ? 'upgrade-addon-selected' : ''}`}
              onClick={() => setSelectedPlan(addon.key)}
            >
              <div className="upgrade-addon-name">{addon.name}</div>
              <div className="upgrade-addon-size">{addon.size}</div>
              <div className="upgrade-addon-price">
                <span className="upgrade-price-amount">{addon.price}</span>
                <span className="upgrade-price-period">{addon.period}</span>
              </div>
            </div>
          ))}
        </div>
      </div>

      <div className="upgrade-footer">
        {onCancel && (
          <Button size="large" icon={<ArrowLeftOutlined />} onClick={onCancel}>
            {cancelButtonText || '取 消'}
          </Button>
        )}
        <Button size="large" type="primary" disabled={!selectedPlan} onClick={handleConfirm}>
          确认升级
        </Button>
      </div>
    </>
  )
}

export { UpgradeContent, UPGRADE_PLANS, ADDON_PLANS }
export default UpgradeContent
