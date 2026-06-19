import { useMemo } from 'react';

export function useMasonryItems(pictures) {
  return useMemo(() => pictures.map(pic => ({ key: `pic-${pic.id}`, data: pic })), [pictures]);
}
