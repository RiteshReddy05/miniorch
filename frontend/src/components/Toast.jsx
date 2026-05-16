import { createContext, useCallback, useContext, useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Info, X } from 'lucide-react';

const ToastContext = createContext(null);

const TONE_STYLES = {
  success: 'border-emerald-700/60 bg-emerald-950/70 text-emerald-100',
  error: 'border-rose-800/60 bg-rose-950/70 text-rose-100',
  info: 'border-slate-700 bg-slate-900/80 text-slate-100',
};

const TONE_ICONS = {
  success: CheckCircle2,
  error: AlertCircle,
  info: Info,
};

let counter = 0;

function nextId() {
  counter += 1;
  return `t${Date.now().toString(36)}${counter}`;
}

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const dismiss = useCallback((id) => {
    setToasts((prev) => prev.filter((t) => t.id !== id));
  }, []);

  const push = useCallback(
    (toast) => {
      const id = nextId();
      const item = { id, tone: 'info', duration: 4000, ...toast };
      setToasts((prev) => [...prev, item]);
      if (item.duration > 0) {
        setTimeout(() => dismiss(id), item.duration);
      }
      return id;
    },
    [dismiss]
  );

  return (
    <ToastContext.Provider value={{ push, dismiss }}>
      {children}
      <Viewport toasts={toasts} dismiss={dismiss} />
    </ToastContext.Provider>
  );
}

function Viewport({ toasts, dismiss }) {
  return (
    <div className="pointer-events-none fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2">
      {toasts.map((t) => {
        const Icon = TONE_ICONS[t.tone] ?? Info;
        return (
          <div
            key={t.id}
            role="status"
            aria-live="polite"
            className={`pointer-events-auto flex items-start gap-2 rounded-lg border px-4 py-3 shadow-lg backdrop-blur ${TONE_STYLES[t.tone] ?? TONE_STYLES.info}`}
          >
            <Icon className="mt-0.5 h-4 w-4 shrink-0" aria-hidden="true" />
            <div className="min-w-0 flex-1 space-y-0.5">
              {t.title && <p className="text-sm font-medium">{t.title}</p>}
              {t.message && <p className="text-xs opacity-90">{t.message}</p>}
            </div>
            <button
              type="button"
              onClick={() => dismiss(t.id)}
              className="text-current opacity-70 transition hover:opacity-100"
              aria-label="Dismiss"
            >
              <X className="h-3.5 w-3.5" />
            </button>
          </div>
        );
      })}
    </div>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) {
    throw new Error('useToast must be used within a ToastProvider');
  }
  return ctx;
}

export function useUnauthorizedToast() {
  const { push } = useToast();
  useEffect(() => {
    const handler = () =>
      push({
        tone: 'error',
        title: 'Session expired',
        message: 'Please sign in again.',
      });
    window.addEventListener('miniorch:unauthorized', handler);
    return () => window.removeEventListener('miniorch:unauthorized', handler);
  }, [push]);
}
