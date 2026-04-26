import { useEffect, useState } from 'react';
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import client from '../api/client.js';

const STATUS = {
  loading: 'loading',
  ok: 'ok',
  error: 'error',
};

export default function Landing() {
  const [status, setStatus] = useState(STATUS.loading);
  const [version, setVersion] = useState(null);

  useEffect(() => {
    let cancelled = false;
    client
      .get('/health')
      .then((res) => {
        if (cancelled) return;
        setVersion(res.data?.version ?? 'unknown');
        setStatus(STATUS.ok);
      })
      .catch(() => {
        if (cancelled) return;
        setStatus(STATUS.error);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-100 px-6">
      <div className="w-full max-w-md text-center space-y-8">
        <header className="space-y-3">
          <h1 className="text-5xl font-semibold tracking-tight">MiniOrch</h1>
          <p className="text-slate-400 text-sm">
            Container orchestration for a single Docker host.
          </p>
        </header>

        <section
          aria-live="polite"
          className="rounded-xl border border-slate-800 bg-slate-900/60 backdrop-blur p-6 flex items-center justify-center gap-3"
        >
          {status === STATUS.loading && (
            <>
              <Loader2 className="w-5 h-5 animate-spin text-slate-400" aria-hidden="true" />
              <span className="text-slate-400">Checking backend…</span>
            </>
          )}
          {status === STATUS.ok && (
            <>
              <CheckCircle2 className="w-5 h-5 text-emerald-400" aria-hidden="true" />
              <span className="text-emerald-300 font-medium">
                Backend: OK (v{version})
              </span>
            </>
          )}
          {status === STATUS.error && (
            <>
              <AlertCircle className="w-5 h-5 text-rose-400" aria-hidden="true" />
              <span className="text-rose-300 font-medium">Backend unreachable</span>
            </>
          )}
        </section>
      </div>
    </main>
  );
}
