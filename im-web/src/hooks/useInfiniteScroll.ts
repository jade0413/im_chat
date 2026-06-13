import { RefObject, useEffect } from 'react';

export function useInfiniteScroll(ref: RefObject<HTMLElement>, onReachTop: () => void) {
  useEffect(() => {
    const element = ref.current;
    if (!element) {
      return;
    }
    const handleScroll = () => {
      if (element.scrollTop < 80) {
        onReachTop();
      }
    };
    element.addEventListener('scroll', handleScroll, { passive: true });
    return () => element.removeEventListener('scroll', handleScroll);
  }, [onReachTop, ref]);
}
