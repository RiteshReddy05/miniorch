import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import client from '../api/client.js';
import AuthCard from '../components/AuthCard.jsx';
import FormField from '../components/FormField.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setDetails([]);
    setSubmitting(true);
    try {
      const res = await client.post('/auth/login', { username, password });
      login(res.data.token);
      const target = location.state?.from || '/deployments';
      navigate(target, { replace: true });
    } catch (err) {
      setError(err.response?.data?.error || 'login failed');
      setDetails(err.response?.data?.details || []);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard title="Sign in" subtitle="Bearer-token session, expires in one hour.">
      <form onSubmit={handleSubmit} className="space-y-4">
        <FormField
          label="Username"
          name="username"
          value={username}
          onChange={setUsername}
          autoFocus
          required
          autoComplete="username"
          disabled={submitting}
        />
        <FormField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          required
          autoComplete="current-password"
          disabled={submitting}
        />
        <ErrorBanner message={error} details={details} />
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-emerald-500 px-4 py-2 font-medium text-slate-950 transition hover:bg-emerald-400 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>
        <p className="text-center text-sm text-slate-400">
          No account?{' '}
          <Link to="/register" className="text-emerald-400 hover:text-emerald-300">
            Register
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}
