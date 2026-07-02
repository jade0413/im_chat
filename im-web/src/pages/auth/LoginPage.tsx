import { useState } from 'react';
import { Button, Form, Input, App as AntApp, Segmented } from 'antd';
import {
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { AuthLayout, AuthLink } from './AuthLayout';

interface LoginFormValues {
  account: string;
  password: string;
}

type AccountMode = 'account' | 'phone' | 'email';

function accountModeMeta(mode: AccountMode) {
  switch (mode) {
    case 'phone':
      return { icon: <MobileOutlined />, placeholder: '请输入手机号' };
    case 'email':
      return { icon: <MailOutlined />, placeholder: 'chen@lumo.im' };
    default:
      return { icon: <UserOutlined />, placeholder: '请输入账号' };
  }
}

export function LoginPage() {
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const login = useAuthStore((state) => state.login);
  const loading = useAuthStore((state) => state.loading);
  const [accountMode, setAccountMode] = useState<AccountMode>('account');
  const accountMeta = accountModeMeta(accountMode);

  async function handleFinish(values: LoginFormValues) {
    try {
      await login(values.account, values.password);
      navigate('/chat', { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败');
    }
  }

  return (
    <AuthLayout
      title="欢迎回来"
      subtitle="登录微光 Lumo，继续你的对话"
      footer={
        <>
          还没有账号？ <AuthLink to="/register">注册</AuthLink>
        </>
      }
    >
      <Form className="auth-form" layout="vertical" onFinish={handleFinish} requiredMark={false}>
        <Segmented
          block
          className="auth-account-switch"
          value={accountMode}
          onChange={(value) => setAccountMode(value as AccountMode)}
          options={[
            { label: '账号', value: 'account' },
            { label: '手机号', value: 'phone' },
            { label: '邮箱', value: 'email' },
          ]}
        />
        <Form.Item name="account" rules={[{ required: true, message: '请输入账号' }]}>
          <Input
            className="auth-input"
            prefix={accountMeta.icon}
            autoComplete="username"
            placeholder={accountMeta.placeholder}
          />
        </Form.Item>
        <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password
            className="auth-input"
            prefix={<LockOutlined />}
            autoComplete="current-password"
            placeholder="请输入密码"
          />
        </Form.Item>
        <div className="auth-forgot">
          <button type="button" onClick={() => message.info('密码找回暂未开放，请联系管理员重置')}>
            忘记密码？
          </button>
        </div>
        <Button className="auth-submit" type="primary" htmlType="submit" block loading={loading}>
          登录
        </Button>
      </Form>
    </AuthLayout>
  );
}
