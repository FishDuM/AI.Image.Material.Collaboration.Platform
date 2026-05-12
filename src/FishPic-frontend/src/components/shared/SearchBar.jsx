import { useRef } from 'react'
import { SearchOutlined } from '@ant-design/icons'

function SearchBar({ placeholder = '搜索...', value, onChange, onSearch, className = '' }) {
  const inputRef = useRef(null)

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      onSearch?.()
    }
  }

  const handleClear = () => {
    onChange?.('')
    inputRef.current?.focus()
  }

  return (
    <div className={`search-bar-clean${className ? ` ${className}` : ''}`}>
      <div className="search-bar-clean-inner">
        <SearchOutlined className="search-bar-clean-icon" />
        <input
          ref={inputRef}
          className="search-bar-clean-input"
          type="text"
          placeholder={placeholder}
          value={value}
          onChange={(e) => onChange?.(e.target.value)}
          onKeyDown={handleKeyDown}
        />
        {value && (
          <button type="button" className="search-bar-clean-clear" onClick={handleClear}>
            ✕
          </button>
        )}
      </div>
      <button type="button" className="search-bar-clean-btn" onClick={onSearch}>
        搜索
      </button>
    </div>
  )
}

export default SearchBar
