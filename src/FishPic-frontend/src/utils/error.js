export function isCanceledError(error) {
  return error?.name === 'CanceledError' || error?.code === 'ERR_CANCELED'
}
