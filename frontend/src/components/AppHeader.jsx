import { Box, LogOut } from 'lucide-react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';

export default function AppHeader() {
  const { user, logout } = useAuth();
  return (
    <header className="sticky top-0 z-10 border-b border-slate-800 bg-slate-950/80 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center gap-4 px-6 py-3">
        <Link to="/deployments" className="inline-flex items-center gap-2 text-slate-100">
          <Box className="h-5 w-5 text-emerald-400" aria-hidden="true" />
          <span className="text-lg font-semibold tracking-tight">MiniOrch</span>
        </Link>
        <div className="flex-1" />
        {user && (
          <>
            <span className="text-sm text-slate-400">
              {user.username}
              <span className="mx-1.5 text-slate-600">·</span>
              <span className="text-slate-500">{user.role}</span>
            </span>
            <button
              type="button"
              onClick={logout}
              className="inline-flex items-center gap-1.5 rounded-md border border-slate-700 px-3 py-1.5 text-sm text-slate-300 transition hover:border-slate-600 hover:bg-slate-900"
            >
              <LogOut className="h-4 w-4" aria-hidden="true" />
              Sign out
            </button>
          </>
        )}
      </div>
    </header>
  );
}
