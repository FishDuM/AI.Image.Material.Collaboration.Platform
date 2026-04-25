import './FunnyBackground.css'

function FunnyBackground({ children }) {
  return (
    <div className="funny-background-container">
      <div className="floating-emoji emoji-1">🐱</div>
      <div className="floating-emoji emoji-2">🚀</div>
      <div className="floating-emoji emoji-3">🗺️</div>
      <div className="floating-emoji emoji-4">🍕</div>
      <div className="floating-emoji emoji-5">🎮</div>
      <div className="floating-emoji emoji-6">🎉</div>
      <div className="floating-emoji emoji-7">🌟</div>
      <div className="floating-emoji emoji-8">🎈</div>
      <div className="floating-emoji emoji-9">🐶</div>
      <div className="floating-emoji emoji-10">🌈</div>
      <div className="floating-emoji emoji-11">🎸</div>
      <div className="floating-emoji emoji-12">🍦</div>
      <div className="floating-emoji emoji-13">🎪</div>
      <div className="floating-emoji emoji-14">🦄</div>
      <div className="floating-emoji emoji-15">🎯</div>
      <div className="floating-emoji emoji-16">🎨</div>
      <div className="floating-emoji emoji-17">🎭</div>
      <div className="floating-emoji emoji-18">🎪</div>
      <div className="floating-emoji emoji-19">🌸</div>
      <div className="floating-emoji emoji-20">🍀</div>
      <div className="floating-emoji emoji-21">🎵</div>
      <div className="floating-emoji emoji-22">🌙</div>
      <div className="floating-emoji emoji-23">☀️</div>
      <div className="floating-emoji emoji-24">⚡</div>
      
      <div className="funny-background-content">
        {children}
      </div>
    </div>
  )
}

export default FunnyBackground
