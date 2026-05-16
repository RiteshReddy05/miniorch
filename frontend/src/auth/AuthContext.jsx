import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

const AuthContext = createContext(null);

const STORAGE_KEY = 'miniorch.token';
const UNAUTHORIZED_EVENT = 'miniorch:unauthorized';

function decodeJwt(token) {
  if (!token) return null;
  try {
    const payload = token.split('.')[1];
    const padded = payload + '='.repeat((4 - (payload.length % 4)) % 4);
    const normalized = padded.replace(/-/g, '+').replace(/_/g, '/');
    return JSON.parse(atob(normalized));
  } catch {
    return null;
  }
}

function isExpired(claims) {
  if (!claims?.exp) return true;
  return claims.exp * 1000 <= Date.now();
}

function readSession() {
  const token = sessionStorage.getItem(STORAGE_KEY);
  if (!token) return { token: null, claims: null };
  const claims = decodeJwt(token);
  if (!claims || isExpired(claims)) {
    sessionStorage.removeItem(STORAGE_KEY);
    return { token: null, claims: null };
  }
  return { token, claims };
}

export function AuthProvider({ children }) {
  const [{ token, claims }, setSession] = useState(readSession);

  useEffect(() => {
    const handler = () => setSession({ token: null, claims: null });
    window.addEventListener(UNAUTHORIZED_EVENT, handler);
    return () => window.removeEventListener(UNAUTHORIZED_EVENT, handler);
  }, []);

  useEffect(() => {
    if (!claims?.exp) return undefined;
    const msUntilExpiry = claims.exp * 1000 - Date.now();
    if (msUntilExpiry <= 0) {
      sessionStorage.removeItem(STORAGE_KEY);
      setSession({ token: null, claims: null });
      return undefined;
    }
    const id = setTimeout(() => {
      sessionStorage.removeItem(STORAGE_KEY);
      setSession({ token: null, claims: null });
    }, msUntilExpiry);
    return () => clearTimeout(id);
  }, [claims]);

  const login = useCallback((newToken) => {
    const decoded = decodeJwt(newToken);
    if (!decoded || isExpired(decoded)) {
      sessionStorage.removeItem(STORAGE_KEY);
      setSession({ token: null, claims: null });
      return;
    }
    sessionStorage.setItem(STORAGE_KEY, newToken);
    setSession({ token: newToken, claims: decoded });
  }, []);

  const logout = useCallback(() => {
    sessionStorage.removeItem(STORAGE_KEY);
    setSession({ token: null, claims: null });
  }, []);

  const user = useMemo(() => {
    if (!claims) return null;
    return { id: claims.sub, username: claims.username, role: claims.role };
  }, [claims]);

  const value = useMemo(
    () => ({ token, user, login, logout, isAuthenticated: !!token }),
    [token, user, login, logout]
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
