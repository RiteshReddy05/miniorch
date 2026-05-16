import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import { AuthProvider, useAuth } from './auth/AuthContext.jsx';
import RequireAuth from './auth/RequireAuth.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import DeploymentsList from './pages/DeploymentsList.jsx';
import DeploymentDetail from './pages/DeploymentDetail.jsx';

function Root() {
  const { isAuthenticated } = useAuth();
  return <Navigate to={isAuthenticated ? '/deployments' : '/login'} replace />;
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
          <Route path="*" element={<Root />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
