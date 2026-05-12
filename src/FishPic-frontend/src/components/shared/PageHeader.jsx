function PageHeader({ title, actions, style }) {
  return (
    <div className="page-header" style={style}>
      <h1 className="page-header-title">{title}</h1>
      {actions && <div className="page-header-actions">{actions}</div>}
    </div>
  )
}

export default PageHeader
