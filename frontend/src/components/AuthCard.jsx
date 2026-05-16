import { Box } from 'lucide-react';

export default function AuthCard({ title, subtitle, children }) {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-100 px-6">
      <div className="w-full max-w-md space-y-8">
        <header className="space-y-3 text-center">
          <div className="inline-flex items-center gap-2 text-slate-300">
            <Box className="w-6 h-6 text-emerald-400" aria-hidden="true" />
            <span className="text-2xl font-semibold tracking-tight">MiniOrch</span>
          </div>
          <h1 className="text-3xl font-semibold tracking-tight">{title}</h1>
          {subtitle && <p className="text-slate-400 text-sm">{subtitle}</p>}
        </header>
        <section className="rounded-xl border border-slate-800 bg-slate-900/60 backdrop-blur p-6">
          {children}
        </section>
      </div>
    </main>
  );
}
