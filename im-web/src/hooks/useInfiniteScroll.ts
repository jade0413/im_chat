import { RefObject, useEffect, useRef } from 'react';

/**
 * L3：用 callbackRef 固定 scroll listener，避免 onReachTop 引用变化时频繁重绑。
 * listener 只在 element 挂载/卸载时注册/销毁一次，每次调用时读取最新回调。
 */
export function useInfiniteScroll(ref: RefObject<HTMLElement>, onReachTop: () => void) {
  const callbackRef = useRef(onReachTop);
  callbackRef.current = onReachTop;

  useEffect(() => {
    const element = ref.current;
    if (!element) return;

    const handleScroll = () => {
      if (element.scrollTop < 80) {
        callbackRef.current();
      }
    };

    element.addEventListener('scroll', handleScroll, { passive: true });
    return () => element.removeEventListener('scroll', handleScroll);
  }, [ref]); // ref 不变则 listener 永远只绑一次
}
