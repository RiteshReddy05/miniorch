import EventIcon from './EventIcon.jsx';
import { relativeTime } from '../lib/format.js';

export default function EventTimeline({ events }) {
  if (!events) {
    return <p className="text-sm text-slate-500">Loading events…</p>;
  }
  if (events.length === 0) {
    return <p className="text-sm text-slate-500">No events yet.</p>;
  }
  return (
    <ol className="space-y-3">
      {events.map((event) => (
        <li
          key={event.id}
          className="flex items-start gap-3 rounded-lg border border-slate-800 bg-slate-900/60 px-4 py-3"
        >
          <EventIcon type={event.type} className="mt-0.5 h-4 w-4" />
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-baseline gap-x-2 gap-y-0.5">
              <span className="font-mono text-xs uppercase tracking-wide text-slate-400">
                {event.type}
              </span>
              <span
                className="text-xs text-slate-500"
                title={new Date(event.createdAt).toISOString()}
              >
                {relativeTime(event.createdAt)}
              </span>
            </div>
            <p className="mt-0.5 text-sm text-slate-200">{event.message}</p>
          </div>
        </li>
      ))}
    </ol>
  );
}
