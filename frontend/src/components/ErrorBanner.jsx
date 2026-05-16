import { AlertCircle } from 'lucide-react';

export default function ErrorBanner({ message, details }) {
  if (!message && (!details || details.length === 0)) return null;
  return (
    <div
      role="alert"
      className="flex items-start gap-2 rounded-md border border-rose-900/60 bg-rose-950/40 px-3 py-2 text-sm text-rose-200"
    >
      <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" aria-hidden="true" />
      <div className="space-y-1">
        {message && <p className="font-medium">{message}</p>}
        {details && details.length > 0 && (
          <ul className="list-disc list-inside text-rose-300/90 text-xs">
            {details.map((d) => (
              <li key={d}>{d}</li>
            ))}
          </ul>
        )}
      </div>
    </div>
  );
}
