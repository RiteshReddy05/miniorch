import {
  AlertCircle,
  AlertTriangle,
  CheckCircle2,
  Circle,
  Clock,
  Minus,
  Play,
  PlusCircle,
  RotateCcw,
  RotateCw,
  ShieldAlert,
  ShieldCheck,
  Sliders,
  XCircle,
} from 'lucide-react';

const ICONS = {
  CREATED: { Icon: PlusCircle, tone: 'text-emerald-300' },
  REPLICA_STARTED: { Icon: Play, tone: 'text-emerald-300' },
  REPLICA_FAILED: { Icon: XCircle, tone: 'text-rose-300' },
  REPLICA_STOPPED: { Icon: Minus, tone: 'text-slate-400' },
  REPLICA_REMOVED: { Icon: Minus, tone: 'text-slate-400' },
  REPLICA_RESTART_SCHEDULED: { Icon: Clock, tone: 'text-amber-300' },
  REPLICA_RESTART_ATTEMPTED: { Icon: RotateCw, tone: 'text-amber-300' },
  DEPLOYMENT_SCALED: { Icon: Sliders, tone: 'text-sky-300' },
  DEPLOYMENT_DEGRADED: { Icon: AlertTriangle, tone: 'text-amber-300' },
  DEPLOYMENT_HEALTHY: { Icon: CheckCircle2, tone: 'text-emerald-300' },
  DEPLOYMENT_DELETED: { Icon: Minus, tone: 'text-slate-400' },
  HEALTH_CHECK_FAILED: { Icon: AlertCircle, tone: 'text-rose-300' },
  HEALTH_CHECK_PASSED: { Icon: ShieldCheck, tone: 'text-emerald-300' },
  CRASHLOOP_BACKOFF_TRIPPED: { Icon: ShieldAlert, tone: 'text-rose-300' },
  CRASHLOOP_BACKOFF_RESET: { Icon: RotateCcw, tone: 'text-emerald-300' },
  ERROR: { Icon: XCircle, tone: 'text-rose-300' },
};

export default function EventIcon({ type, className = 'h-4 w-4' }) {
  const entry = ICONS[type] ?? { Icon: Circle, tone: 'text-slate-400' };
  const { Icon, tone } = entry;
  return <Icon className={`${className} ${tone}`} aria-hidden="true" />;
}
