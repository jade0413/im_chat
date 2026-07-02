import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { MessageFilled } from '@ant-design/icons';

interface AuthLayoutProps {
  title: string;
  subtitle: string;
  eyebrow?: string;
  footer: ReactNode;
  children: ReactNode;
}

export function AuthLayout({ title, subtitle, eyebrow = '微光 Lumo', footer, children }: AuthLayoutProps) {
  return (
    <div className="auth-shell">
      <main className="auth-panel">
        <div className="auth-logo" aria-hidden="true">
          <MessageFilled />
        </div>
        <div className="auth-eyebrow">{eyebrow}</div>
        <h1 className="auth-title">{title}</h1>
        <p className="auth-subtitle">{subtitle}</p>
        {children}
        <div className="auth-footer">{footer}</div>
      </main>
    </div>
  );
}

export function AuthLink({ to, children }: { to: string; children: ReactNode }) {
  return <Link to={to}>{children}</Link>;
}
