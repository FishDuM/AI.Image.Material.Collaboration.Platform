import { useState, useEffect } from 'react';
import { getSystemTypes } from '../api';

export function useSystemTypes() {
  const [types, setTypes] = useState([]);
  useEffect(() => {
    getSystemTypes()
      .then(res => setTypes(Array.isArray(res) ? res : []))
      .catch(() => {});
  }, []);
  return types;
}
