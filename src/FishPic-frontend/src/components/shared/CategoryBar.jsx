function CategoryBar({ items, selected, onSelect, className = '' }) {
  if (!items || items.length === 0) return null

  return (
    <div className={`category-bar ${className}`}>
      {items.map((cat) => (
        <span
          key={cat}
          className={`category-tag ${selected === cat ? 'category-tag-active' : ''}`}
          onClick={() => onSelect?.(cat)}
        >
          {cat}
        </span>
      ))}
    </div>
  )
}

export default CategoryBar