import UpgradeContent from './UpgradeContent'

function UpgradeModal({ open, onClose, onConfirm, cancelButtonText }) {
  if (!open) return null

  return (
    <div className="upgrade-overlay" onClick={onClose}>
      <div className="upgrade-content" onClick={(e) => e.stopPropagation()}>
        <UpgradeContent
          onCancel={onClose}
          cancelButtonText={cancelButtonText}
          onConfirm={(plan) => {
            onConfirm?.(plan)
            onClose()
          }}
        />
      </div>
    </div>
  )
}

export default UpgradeModal
