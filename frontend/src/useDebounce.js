import { useEffect, useState } from 'react'

/** Returns a debounced copy of `value` that updates at most every `delay` ms. */
export default function useDebounce(value, delay = 200) {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay)
    return () => clearTimeout(id)
  }, [value, delay])
  return debounced
}
