import { Link } from 'react-router-dom';
import { ArrowLeft, Compass } from 'lucide-react';

export default function NotFound() {
  return (
    <main className="min-h-screen bg-slate-950 text-slate-100 flex items-center justify-center px-6">
      <div className="w-full max-w-md space-y-6 text-center">
        <div className="mx-auto inline-flex h-12 w-12 items-center justify-center rounded-full border border-slate-800 bg-slate-900/80">
          <Compass className="h-6 w-6 text-emerald-400" aria-hidden="true" />
        </div>
        <div className="space-y-2">
          <h1 className="text-4xl font-semibold tracking-tight">404</h1>
          <p className="text-slate-400">
            We don't have a page at that path. The deployment may have been deleted, or
            the URL may be a typo.
          </p>
        </div>
        <Link
          to="/deployments"
          className="inline-flex items-center gap-1.5 rounded-md bg-emerald-500 px-4 py-2 text-sm font-medium text-slate-950 transition hover:bg-emerald-400"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          Back to deployments
        </Link>
      </div>
    </main>
  );
}
