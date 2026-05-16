import { useEffect, useRef } from 'react';

export default function usePolling(fetcher, intervalMs, deps = []) {
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;

  useEffect(() => {
    let cancelled = false;
    let intervalId = null;

    async function tick() {
      if (cancelled) return;
      try {
        await fetcherRef.current();
      } catch {
        /* fetcher owns error reporting */
      }
    }

    function start() {
      if (intervalId !== null || document.hidden) return;
      intervalId = setInterval(tick, intervalMs);
    }

    function stop() {
      if (intervalId === null) return;
      clearInterval(intervalId);
      intervalId = null;
    }

    function handleVisibility() {
      if (document.hidden) {
        stop();
      } else {
        tick();
        start();
      }
    }

    tick();
    if (!document.hidden) start();
    document.addEventListener('visibilitychange', handleVisibility);

    return () => {
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', handleVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, ...deps]);
}
