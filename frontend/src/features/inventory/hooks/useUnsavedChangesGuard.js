import { useCallback, useEffect, useRef } from 'react';

const MESSAGE = 'Nếu bạn thoát khỏi màn hình này, các thay đổi chưa lưu sẽ bị mất.\nBạn có chắc chắn muốn thoát không?';

export default function useUnsavedChangesGuard({ when, confirm }) {
  const bypassRef = useRef(false);
  const whenRef = useRef(Boolean(when));
  const confirmRef = useRef(confirm);

  useEffect(() => {
    whenRef.current = Boolean(when);
  }, [when]);

  useEffect(() => {
    confirmRef.current = confirm;
  }, [confirm]);

  useEffect(() => {
    if (!when) return undefined;
    const handleBeforeUnload = (event) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => window.removeEventListener('beforeunload', handleBeforeUnload);
  }, [when]);

  useEffect(() => {
    const originalPushState = window.history.pushState;
    const originalReplaceState = window.history.replaceState;

    const shouldBlock = (url) => {
      if (!whenRef.current || bypassRef.current || !url) return false;
      const nextUrl = new URL(String(url), window.location.href);
      return nextUrl.pathname !== window.location.pathname || nextUrl.search !== window.location.search;
    };

    const askThenNavigate = (method, state, title, url) => {
      confirmRef.current({
        title: 'Bạn đang nhập dữ liệu',
        message: MESSAGE,
        confirmLabel: 'Thoát',
        cancelLabel: 'Ở lại',
        tone: 'danger',
      }).then((ok) => {
        if (!ok) return;
        bypassRef.current = true;
        method.call(window.history, state, title, url);
        window.dispatchEvent(new PopStateEvent('popstate', { state }));
        window.setTimeout(() => {
          bypassRef.current = false;
        }, 0);
      });
    };

    window.history.pushState = function guardedPushState(state, title, url) {
      if (shouldBlock(url)) {
        askThenNavigate(originalPushState, state, title, url);
        return undefined;
      }
      return originalPushState.call(window.history, state, title, url);
    };

    window.history.replaceState = function guardedReplaceState(state, title, url) {
      if (shouldBlock(url)) {
        askThenNavigate(originalReplaceState, state, title, url);
        return undefined;
      }
      return originalReplaceState.call(window.history, state, title, url);
    };

    return () => {
      window.history.pushState = originalPushState;
      window.history.replaceState = originalReplaceState;
    };
  }, []);

  const runWithoutGuard = useCallback((action) => {
    bypassRef.current = true;
    action();
    window.setTimeout(() => {
      bypassRef.current = false;
    }, 0);
  }, []);

  return { runWithoutGuard };
}
