import UpgradeContent from './UpgradeContent'

function UpgradeModal({ open, onClose, onConfirm, cancelButtonText }) {
  const handleClose = () => {
    onClose()
  }

  if (!open) return null

  return (
    <div className="upgrade-overlay" onClick={handleClose}>
      <div className="upgrade-content" onClick={(e) => e.stopPropagation()}>
        <UpgradeContent
          onCancel={handleClose}
          cancelButtonText={cancelButtonText}
          onConfirm={(plan) => {
            if (onConfirm) onConfirm(plan)
            handleClose()
          }}
        />
      </div>
    </div>
  )
}

export default UpgradeModal
