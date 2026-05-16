export function SkeletonLine({ className = 'h-3 w-32' }) {
  return <span className={`inline-block animate-pulse rounded bg-slate-800/70 ${className}`} />;
}

export function SkeletonCard({ height = 'h-24' }) {
  return (
    <div
      className={`animate-pulse rounded-xl border border-slate-800/80 bg-slate-900/40 ${height}`}
    />
  );
}

export function DeploymentRowsSkeleton({ count = 3 }) {
  return (
    <ul className="space-y-3" aria-busy="true" aria-label="Loading deployments">
      {Array.from({ length: count }).map((_, idx) => (
        <li
          key={idx}
          className="rounded-xl border border-slate-800/80 bg-slate-900/40 px-5 py-4"
        >
          <div className="flex items-start gap-4">
            <div className="flex-1 space-y-2">
              <SkeletonLine className="h-4 w-48" />
              <SkeletonLine className="h-3 w-64" />
              <SkeletonLine className="h-3 w-40" />
            </div>
            <div className="flex flex-col items-end gap-2">
              <SkeletonLine className="h-5 w-20" />
              <SkeletonLine className="h-6 w-14" />
            </div>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function DeploymentDetailSkeleton() {
  return (
    <div className="space-y-6" aria-busy="true" aria-label="Loading deployment">
      <div className="space-y-2">
        <SkeletonLine className="h-7 w-64" />
        <SkeletonLine className="h-3 w-96" />
      </div>
      <div className="grid grid-cols-1 gap-3 lg:grid-cols-2">
        <SkeletonCard height="h-40" />
        <SkeletonCard height="h-40" />
      </div>
    </div>
  );
}
