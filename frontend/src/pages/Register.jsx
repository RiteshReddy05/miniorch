import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext.jsx';
import client from '../api/client.js';
import AuthCard from '../components/AuthCard.jsx';
import FormField from '../components/FormField.jsx';
import ErrorBanner from '../components/ErrorBanner.jsx';

const USERNAME_PATTERN = /^[A-Za-z0-9_-]+$/;

function validateClientSide(username, password) {
  if (username.length < 3 || username.length > 30) {
    return 'username must be 3 to 30 characters';
  }
  if (!USERNAME_PATTERN.test(username)) {
    return 'username may only contain letters, digits, underscore, or hyphen';
  }
  if (password.length < 8 || password.length > 72) {
    return 'password must be 8 to 72 characters';
  }
  return null;
}

export default function Register() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [details, setDetails] = useState([]);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event) {
    event.preventDefault();
    setError(null);
    setDetails([]);

    const local = validateClientSide(username, password);
    if (local) {
      setError(local);
      return;
    }

    setSubmitting(true);
    try {
      await client.post('/auth/register', { username, password });
      const loginRes = await client.post('/auth/login', { username, password });
      login(loginRes.data.token);
      navigate('/deployments', { replace: true });
    } catch (err) {
      setError(err.response?.data?.error || 'registration failed');
      setDetails(err.response?.data?.details || []);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard title="Create account" subtitle="Single-tenant MiniOrch dev instance.">
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
          hint="3-30 chars, letters, digits, underscore, or hyphen."
        />
        <FormField
          label="Password"
          name="password"
          type="password"
          value={password}
          onChange={setPassword}
          required
          autoComplete="new-password"
          disabled={submitting}
          hint="8-72 characters."
        />
        <ErrorBanner message={error} details={details} />
        <button
          type="submit"
          disabled={submitting}
          className="w-full rounded-md bg-emerald-500 px-4 py-2 font-medium text-slate-950 transition hover:bg-emerald-400 disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
        <p className="text-center text-sm text-slate-400">
          Already registered?{' '}
          <Link to="/login" className="text-emerald-400 hover:text-emerald-300">
            Sign in
          </Link>
        </p>
      </form>
    </AuthCard>
  );
}
