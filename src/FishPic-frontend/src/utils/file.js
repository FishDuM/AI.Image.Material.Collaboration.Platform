export function downloadFile(url, filename = 'image') {
  if (!url) return
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  if (url.startsWith('blob:')) {
    // Delay revoke to ensure the browser has time to start the download
    setTimeout(() => window.URL.revokeObjectURL(url), 1000)
  }
}
