import { useCallback, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, Sliders, Trash2 } from 'lucide-react';
import AppHeader from '../components/AppHeader.jsx';
import StatusBadge from '../components/StatusBadge.jsx';
import ReplicaCard from '../components/ReplicaCard.jsx';
import EventTimeline from '../components/EventTimeline.jsx';
import ScaleModal from '../components/ScaleModal.jsx';
import ResetReplicaDialog from '../components/ResetReplicaDialog.jsx';
import DeleteDeploymentDialog from '../components/DeleteDeploymentDialog.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';
import usePolling from '../hooks/usePolling.js';
import { getDeployment, getDeploymentEvents } from '../lib/deployments.js';
import { extractErrorMessage, relativeTime } from '../lib/format.js';

const POLL_MS = 10_000;
const TABS = [
  { key: 'replicas', label: 'Replicas' },
  { key: 'events', label: 'Events' },
];

export default function DeploymentDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [deployment, setDeployment] = useState(null);
  const [events, setEvents] = useState(null);
  const [error, setError] = useState(null);
  const [tab, setTab] = useState('replicas');
  const [scaleOpen, setScaleOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [resetTarget, setResetTarget] = useState(null);

  const refresh = useCallback(async () => {
    try {
      const [d, ev] = await Promise.all([getDeployment(id), getDeploymentEvents(id)]);
      setDeployment(d);
      setEvents(ev);
      setError(null);
    } catch (err) {
      setError(extractErrorMessage(err, 'failed to load deployment'));
      if (err?.response?.status === 404) {
        setTimeout(() => navigate('/deployments', { replace: true }), 1500);
      }
    }
  }, [id, navigate]);

  usePolling(refresh, POLL_MS, [id]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100">
      <AppHeader />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Link
          to="/deployments"
          className="mb-4 inline-flex items-center gap-1.5 text-sm text-slate-400 hover:text-slate-200"
        >
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />
          All deployments
        </Link>

        {error && (
          <div className="mb-6">
            <ErrorBanner message={error} />
          </div>
        )}

        {deployment === null ? (
          <p className="text-sm text-slate-500">Loading deployment…</p>
        ) : (
          <>
            <header className="flex flex-wrap items-start justify-between gap-4">
              <div className="space-y-1 min-w-0">
                <div className="flex items-center gap-3">
                  <h1 className="text-2xl font-semibold tracking-tight">{deployment.name}</h1>
                  <StatusBadge status={deployment.observedStatus || deployment.status} />
                </div>
                <p className="font-mono text-xs text-slate-500">
                  {deployment.image}:{deployment.tag}
                  <span className="mx-2 text-slate-700">·</span>
                  desired {deployment.desiredReplicas}
                  <span className="mx-2 text-slate-700">·</span>
                  probe {deployment.probe?.type ?? 'DOCKER'}
                  <span className="mx-2 text-slate-700">·</span>
                  updated {relativeTime(deployment.updatedAt)}
                </p>
              </div>
              <div className="flex items-center gap-2">
                <button
                  type="button"
                  onClick={() => setScaleOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:border-slate-600 hover:bg-slate-900"
                >
                  <Sliders className="h-4 w-4" aria-hidden="true" />
                  Scale
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteOpen(true)}
                  className="inline-flex items-center gap-1.5 rounded-md border border-rose-900/60 bg-rose-950/30 px-3 py-1.5 text-sm text-rose-200 transition hover:border-rose-700 hover:bg-rose-950/60"
                >
                  <Trash2 className="h-4 w-4" aria-hidden="true" />
                  Delete
                </button>
              </div>
            </header>

            <nav className="mt-6 flex gap-6 border-b border-slate-800" role="tablist">
              {TABS.map((t) => (
                <button
                  key={t.key}
                  type="button"
                  role="tab"
                  aria-selected={tab === t.key}
                  onClick={() => setTab(t.key)}
                  className={`-mb-px border-b-2 px-1 py-2 text-sm transition ${
                    tab === t.key
                      ? 'border-emerald-400 text-slate-100'
                      : 'border-transparent text-slate-500 hover:text-slate-300'
                  }`}
                >
                  {t.label}
                </button>
              ))}
            </nav>

            <section className="mt-6">
              {tab === 'replicas' && (
                <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
                  {[...deployment.replicas]
                    .sort((a, b) => a.replicaIndex - b.replicaIndex)
                    .map((replica) => (
                      <ReplicaCard
                        key={replica.id}
                        replica={replica}
                        onReset={(r) => setResetTarget(r)}
                      />
                    ))}
                </div>
              )}
              {tab === 'events' && <EventTimeline events={events} />}
            </section>
          </>
        )}
      </main>

      {scaleOpen && deployment && (
        <ScaleModal
          deployment={deployment}
          onClose={() => setScaleOpen(false)}
          onScaled={(d) => setDeployment(d)}
        />
      )}
      {resetTarget && (
        <ResetReplicaDialog
          deploymentId={id}
          replica={resetTarget}
          onClose={() => setResetTarget(null)}
          onResetDone={(d) => setDeployment(d)}
        />
      )}
      {deleteOpen && deployment && (
        <DeleteDeploymentDialog
          deployment={deployment}
          onClose={() => setDeleteOpen(false)}
          onDeleted={() => navigate('/deployments', { replace: true })}
        />
      )}
    </div>
  );
}
