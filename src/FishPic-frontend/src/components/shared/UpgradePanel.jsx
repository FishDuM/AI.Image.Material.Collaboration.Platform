import { Button } from 'antd'
import { CrownOutlined } from '@ant-design/icons'

const PLANS = [
  {
    key: 'vip',
    name: 'VIP',
    price: '9.9',
    period: '/月',
    desc: '基础扩容方案',
    features: ['存储空间 5GB', '单次上传 20MB', '优先客服'],
  },
  {
    key: 'svip',
    name: 'SVIP',
    price: '19.9',
    period: '/月',
    desc: '高级扩容方案',
    features: ['存储空间 20GB', '单次上传 50MB', '专属客服', 'AI 增强功能'],
    recommended: true,
  },
]

const ADDONS = [
  { key: '1gb', size: '1GB', price: '1.0' },
  { key: '5gb', size: '5GB', price: '4.5' },
  { key: '10gb', size: '10GB', price: '8.0' },
  { key: '50gb', size: '50GB', price: '35.0' },
]

function UpgradePanel({ currentLevel, onUpgrade }) {
  return (
    <>
      <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 16 }}>
        <CrownOutlined style={{ marginRight: 8 }} />
        升级方案
      </h3>
      <div className="upgrade-plan-grid">
        {PLANS.map((plan) => (
          <div
            key={plan.key}
            className={`upgrade-plan-card ${plan.recommended ? 'recommended' : ''}`}
          >
            <div className="upgrade-plan-name">{plan.name}</div>
            <div className="upgrade-plan-price">
              ¥{plan.price}<span>{plan.period}</span>
            </div>
            <div className="upgrade-plan-desc">{plan.desc}</div>
            <ul style={{ listStyle: 'none', padding: 0, marginBottom: 16, fontSize: 13, color: 'var(--text-secondary)' }}>
              {plan.features.map((f) => (
                <li key={f} style={{ marginBottom: 4 }}>✓ {f}</li>
              ))}
            </ul>
            <Button
              type={plan.recommended ? 'primary' : 'default'}
              className="upgrade-plan-btn"
              disabled={currentLevel === plan.key}
              onClick={() => onUpgrade?.(plan.key)}
            >
              {currentLevel === plan.key ? '当前方案' : '立即升级'}
            </Button>
          </div>
        ))}
      </div>
      <h3 style={{ fontSize: 16, fontWeight: 600, color: 'var(--text-primary)', marginBottom: 12 }}>
        增量包
      </h3>
      <div className="upgrade-addon-grid">
        {ADDONS.map((addon) => (
          <div key={addon.key} className="upgrade-addon-card" onClick={() => onUpgrade?.(addon.key)}>
            <div className="upgrade-addon-size">{addon.size}</div>
            <div className="upgrade-addon-price">¥{addon.price}</div>
          </div>
        ))}
      </div>
    </>
  )
}

export default UpgradePanel
