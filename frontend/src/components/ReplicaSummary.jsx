export default function ReplicaSummary({ replicas, desired }) {
  if (!replicas) return null;
  const counts = replicas.reduce((acc, r) => {
    acc[r.status] = (acc[r.status] ?? 0) + 1;
    return acc;
  }, {});
  const running = counts.RUNNING ?? 0;
  const crashloop = counts.CRASHLOOP_BACKOFF ?? 0;
  const failed = counts.FAILED ?? 0;
  const exited = counts.EXITED ?? 0;

  const parts = [];
  parts.push(
    <span key="running" className="text-emerald-300">
      {running}/{desired} running
    </span>
  );
  if (crashloop > 0) parts.push(<span key="cl" className="text-rose-300">{crashloop} crashloop</span>);
  if (failed > 0) parts.push(<span key="f" className="text-rose-300">{failed} failed</span>);
  if (exited > 0) parts.push(<span key="e" className="text-amber-300">{exited} exited</span>);

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-0.5 text-xs">
      {parts.map((p, i) => (
        <span key={i}>{p}</span>
      ))}
    </div>
  );
}
