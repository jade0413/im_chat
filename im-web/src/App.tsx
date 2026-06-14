import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { LoginPage } from './pages/auth/LoginPage';
import { RegisterPage } from './pages/auth/RegisterPage';
import { MainLayout } from './pages/main/MainLayout';
import { VisitorPage } from './pages/visitor/VisitorPage';
import { KickDialog } from './components/KickDialog';
import { useAuthStore } from './store/authStore';

export function App() {
  return (
    <BrowserRouter>
      <BootstrapGate>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/visitor" element={<VisitorPage />} />
          <Route
            path="/chat"
            element={
              <ProtectedRoute>
                <MainLayout />
              </ProtectedRoute>
            }
          />
          <Route path="*" element={<Navigate to="/chat" replace />} />
        </Routes>
        <KickDialog />
      </BootstrapGate>
    </BrowserRouter>
  );
}

function BootstrapGate({ children }: { children: React.ReactNode }) {
  const bootstrapped = useAuthStore((state) => state.bootstrapped);
  const refreshFromStorage = useAuthStore((state) => state.refreshFromStorage);

  useEffect(() => {
    if (!bootstrapped) {
      void refreshFromStorage();
    }
  }, [bootstrapped, refreshFromStorage]);

  if (!bootstrapped) {
    return (
      <div className="app-loading">
        <Spin />
      </div>
    );
  }

  return <>{children}</>;
}

function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((state) => state.accessToken);
  const location = useLocation();
  if (!accessToken) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <>{children}</>;
}
