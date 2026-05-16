import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { Plus, RefreshCw, ScaleIcon, Boxes } from 'lucide-react';
import AppHeader from '../components/AppHeader.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import ReplicaSummary from '../components/ReplicaSummary.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import CreateDeploymentModal from '../components/CreateDeploymentModal.jsx';
import ScaleModal from '../components/ScaleModal.jsx';
import { DeploymentRowsSkeleton } from '../components/Skeleton.jsx';
import usePolling from '../hooks/usePolling.js';
import { listDeployments } from '../lib/deployments.js';
import { extractErrorMessage, relativeTime } from '../lib/format.js';

const POLL_MS = 10_000;

export default function DeploymentsList() {
  const [deployments, setDeployments] = useState(null);
  const [error, setError] = useState(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [scaleTarget, setScaleTarget] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const data = await listDeployments();
      setDeployments(data);
      setError(null);
    } catch (err) {
      setError(extractErrorMessage(err, 'failed to load deployments'));
    }
  }, []);

  usePolling(refresh, POLL_MS);

  function upsert(updated) {
    setDeployments((prev) => {
      if (!prev) return [updated];
      const idx = prev.findIndex((d) => d.id === updated.id);
      if (idx === -1) return [...prev, updated];
      const next = prev.slice();
      next[idx] = updated;
      return next;
    });
  }

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <div className="mb-6 flex items-center justify-between">
          <div className="space-y-1">
            <h1 className="text-2xl font-semibold tracking-tight">Deployments</h1>
            <p className="text-sm text-slate-400">
              Auto-refreshes every 10 seconds — same cadence as the reconciliation loop.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={refresh}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:border-slate-600 hover:bg-slate-900"
              aria-label="Refresh now"
            >
              <RefreshCw className="h-4 w-4" aria-hidden="true" />
              Refresh
            </button>
            <button
              type="button"
              onClick={() => setCreateOpen(true)}
              className="inline-flex items-center gap-1.5 rounded-md bg-emerald-500 px-3 py-1.5 text-sm font-medium text-slate-950 transition hover:bg-emerald-400"
            >
              <Plus className="h-4 w-4" aria-hidden="true" />
              New deployment
            </button>
          </div>
        </div>

        {error && (
          <div className="mb-6">
            <ErrorBanner message={error} />
          </div>
        )}

        {deployments === null ? (
          <DeploymentRowsSkeleton />
        ) : deployments.length === 0 ? (
          <div className="rounded-xl border border-dashed border-slate-800 bg-slate-900/40 px-6 py-16 text-center">
            <Boxes className="mx-auto h-10 w-10 text-slate-600" aria-hidden="true" />
            <p className="mt-3 text-slate-300">No deployments yet.</p>
            <p className="mt-1 text-sm text-slate-500">
              Hit “New deployment” to declare desired state. The controller takes care of the rest.
            </p>
          </div>
        ) : (
          <ul className="space-y-3">
            {deployments.map((d) => (
              <li
                key={d.id}
                className="rounded-xl border border-slate-800 bg-slate-900/60 px-5 py-4 transition hover:border-slate-700"
              >
                <div className="flex items-start gap-4">
                  <div className="min-w-0 flex-1 space-y-1">
                    <Link
                      to={`/deployments/${d.id}`}
                      className="block text-lg font-semibold tracking-tight text-slate-100 hover:text-emerald-300"
                    >
                      {d.name}
                    </Link>
                    <p className="truncate text-xs text-slate-500">
                      {d.image}:{d.tag}
                      <span className="mx-2 text-slate-700">·</span>
                      updated {relativeTime(d.updatedAt)}
                    </p>
                    <div className="pt-1">
                      <ReplicaSummary replicas={d.replicas} desired={d.desiredReplicas} />
                    </div>
                  </div>
                  <div className="flex flex-col items-end gap-2">
                    <div className="flex items-center gap-2">
                      <StatusBadge status={d.observedStatus || d.status} />
                    </div>
                    <button
                      type="button"
                      onClick={() => setScaleTarget(d)}
                      className="inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-3 py-1 text-xs text-slate-300 transition hover:border-slate-600 hover:bg-slate-800"
                    >
                      <ScaleIcon className="h-3.5 w-3.5" aria-hidden="true" />
                      Scale
                    </button>
                  </div>
                </div>
              </li>
            ))}
          </ul>
        )}
      </main>

      {createOpen && (
        <CreateDeploymentModal
          onClose={() => setCreateOpen(false)}
          onCreated={(d) => upsert(d)}
        />
      )}
      {scaleTarget && (
        <ScaleModal
          deployment={scaleTarget}
          onClose={() => setScaleTarget(null)}
          onScaled={(d) => upsert(d)}
        />
      )}
    </div>
  );
}
