import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import RequireAuth from './auth/RequireAuth.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import DeploymentsList from './pages/DeploymentsList.jsx';
import DeploymentDetail from './pages/DeploymentDetail.jsx';
import NotFound from './pages/NotFound.jsx';
import { ToastProvider, useUnauthorizedToast } from './components/Toast.jsx';

function Root() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? '/deployments' : '/login'} replace />;
}

function SessionExpiredBridge() {
  useUnauthorizedToast();
  return null;
}

export default function App() {
  return (
    <BrowserRouter>
      <ToastProvider>
        <AuthProvider>
          <SessionExpiredBridge />
          <Routes>
          <Route path="/" element={<Root />} />
          <Route path="/login" element={<Login />} />
          <Route path="/register" element={<Register />} />
          <Route
            path="/deployments"
            element={
              <RequireAuth>
                <DeploymentsList />
              </RequireAuth>
            }
          />
          <Route
            path="/deployments/:id"
            element={
              <RequireAuth>
                <DeploymentDetail />
              </RequireAuth>
            }
          />
          <Route path="*" element={<NotFound />} />
        </Routes>
        </AuthProvider>
      </ToastProvider>
    </BrowserRouter>
  );
}
