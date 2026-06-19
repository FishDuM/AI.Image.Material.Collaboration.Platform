import { Button, Popconfirm } from 'antd'

function BatchActionBar({ selectedCount, className, countClassName, actionsClassName, actions, deleteAction, onCancel, cancelIcon }) {
  return (
    <div className={className}>
      <span className={countClassName}>
        已选择 <strong>{selectedCount}</strong> 张图片
      </span>
      <div className={actionsClassName}>
        <Button icon={cancelIcon} onClick={onCancel}>
          取消
        </Button>
        {actions.map((action, index) => (
          <Button
            key={index}
            icon={action.icon}
            onClick={action.onClick}
            disabled={action.disabled}
            style={action.style}
            type={action.type}
            danger={action.danger}
          >
            {action.label}
          </Button>
        ))}
        {deleteAction && (
          <Popconfirm
            title="确认删除"
            description={`确定要删除选中的 ${selectedCount} 张图片吗？`}
            onConfirm={deleteAction.onClick}
            okText="删除"
            cancelText="取消"
            okButtonProps={{ danger: true, disabled: selectedCount === 0 }}
          >
            <Button
              type="primary"
              danger
              disabled={deleteAction.disabled ?? selectedCount === 0}
            >
              {deleteAction.label || '删除选中'}
            </Button>
          </Popconfirm>
        )}
      </div>
    </div>
  )
}

export default BatchActionBar
