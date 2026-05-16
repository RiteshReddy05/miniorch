const TONES = {
  PENDING: 'sky',
  RUNNING: 'emerald',
  EXITED: 'amber',
  FAILED: 'rose',
  REMOVED: 'slate',
  CRASHLOOP_BACKOFF: 'rose',
  DELETING: 'slate',
  Healthy: 'emerald',
  Degraded: 'amber',
  Failed: 'rose',
  Pending: 'sky',
  PASSING: 'emerald',
  FAILING: 'rose',
  NOT_PROBED: 'slate',
};

const TONE_CLASSES = {
  emerald: 'bg-emerald-500/10 border-emerald-500/40 text-emerald-300',
  amber: 'bg-amber-500/10 border-amber-500/40 text-amber-300',
  rose: 'bg-rose-500/10 border-rose-500/40 text-rose-300',
  sky: 'bg-sky-500/10 border-sky-500/40 text-sky-300',
  slate: 'bg-slate-500/10 border-slate-600/50 text-slate-300',
};

export default function StatusBadge({ status }) {
  if (!status) return null;
  const tone = TONES[status] ?? 'slate';
  return (
    <span
      className={`inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium tracking-wide ${TONE_CLASSES[tone]}`}
    >
      {status}
    </span>
  );
}
