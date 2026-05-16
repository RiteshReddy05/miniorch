import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import RequireAuth from './auth/RequireAuth.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';

function Root() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? '/deployments' : '/login'} replace />;
}

function PlaceholderDeployments() {
  return (
    <main className="min-h-screen flex items-center justify-center bg-slate-950 text-slate-100">
      <p className="text-slate-400">Deployments view lands in the next commit.</p>
    </main>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Root />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/deployments/*"
            element={
              <RequireAuth>
                <PlaceholderDeployments />
              </RequireAuth>
            }
          />
          <Route path="*" element={<Root />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
