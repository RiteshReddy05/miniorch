import { RotateCcw } from 'lucide-react';
import StatusBadge from './StatusBadge.jsx';
import { relativeTime } from '../lib/format.js';

export default function ReplicaCard({ replica, onReset, resetSubmitting }) {
  const canReset = replica.status === 'CRASHLOOP_BACKOFF';
  return (
    <article className="rounded-xl border border-slate-800 bg-slate-900/60 p-4">
      <header className="flex items-start justify-between gap-3">
        <div className="space-y-1 min-w-0">
          <h3 className="font-mono text-sm text-slate-200">{replica.containerName}</h3>
          <p className="font-mono text-xs text-slate-500">
            {replica.containerId
              ? replica.containerId.slice(0, 12)
              : 'no container'}
          </p>
        </div>
        <StatusBadge status={replica.status} />
      </header>

      <dl className="mt-3 grid grid-cols-2 gap-x-4 gap-y-2 text-xs sm:grid-cols-4">
        <Field label="Index" value={`#${replica.replicaIndex}`} />
        <Field label="Restarts" value={replica.restartCount} />
        <Field
          label="Last probe"
          value={
            <span className="inline-flex items-center gap-1.5">
              <StatusBadge status={replica.lastProbeResult} />
              <span className="text-slate-500">
                {replica.lastProbeAt ? relativeTime(replica.lastProbeAt) : ''}
              </span>
            </span>
          }
        />
        <Field label="Consec. fails" value={replica.consecutiveFailures} />
      </dl>

      {replica.lastError && (
        <p className="mt-3 break-words rounded-md border border-rose-900/40 bg-rose-950/30 px-3 py-2 text-xs text-rose-200">
          <span className="font-medium text-rose-300">Error: </span>
          {replica.lastError}
        </p>
      )}
      {replica.probeDetails && (
        <p className="mt-2 break-words rounded-md border border-slate-800 bg-slate-950/40 px-3 py-2 text-xs text-slate-400">
          <span className="font-medium text-slate-300">Probe: </span>
          {replica.probeDetails}
        </p>
      )}

      {canReset && (
        <div className="mt-3 flex justify-end">
          <button
            type="button"
            onClick={() => onReset(replica)}
            disabled={resetSubmitting}
            className="inline-flex items-center gap-1.5 rounded-md border border-amber-700/60 bg-amber-500/10 px-3 py-1.5 text-xs font-medium text-amber-200 transition hover:border-amber-500 hover:bg-amber-500/15 disabled:opacity-50"
          >
            <RotateCcw className="h-3.5 w-3.5" aria-hidden="true" />
            Reset replica
          </button>
        </div>
      )}
    </article>
  );
}

function Field({ label, value }) {
  return (
    <div className="space-y-0.5">
      <dt className="text-slate-500">{label}</dt>
      <dd className="text-slate-200">{value}</dd>
    </div>
  );
}
