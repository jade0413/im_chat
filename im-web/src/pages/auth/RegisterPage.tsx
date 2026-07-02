import { useState } from 'react';
import { Button, Form, Input, App as AntApp, Segmented } from 'antd';
import {
  LockOutlined,
  MailOutlined,
  MobileOutlined,
  SmileOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { AuthLayout, AuthLink } from './AuthLayout';

interface RegisterFormValues {
  account: string;
  password: string;
  nickname?: string;
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

export function RegisterPage() {
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const register = useAuthStore((state) => state.register);
  const loading = useAuthStore((state) => state.loading);
  const [accountMode, setAccountMode] = useState<AccountMode>('account');
  const accountMeta = accountModeMeta(accountMode);

  async function handleFinish(values: RegisterFormValues) {
    try {
      await register(values.account, values.password, values.nickname);
      navigate('/chat', { replace: true });
    } catch (error) {
      message.error(error instanceof Error ? error.message : '注册失败');
    }
  }

  return (
    <AuthLayout
      title="创建账号"
      subtitle="加入微光 Lumo，开启你的第一段对话"
      footer={
        <>
          已有账号？ <AuthLink to="/login">登录</AuthLink>
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
        <Form.Item name="nickname">
          <Input className="auth-input" prefix={<SmileOutlined />} placeholder="昵称（可选）" />
        </Form.Item>
        <Form.Item
          name="password"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]}
        >
          <Input.Password
            className="auth-input"
            prefix={<LockOutlined />}
            autoComplete="new-password"
            placeholder="设置至少 8 位密码"
          />
        </Form.Item>
        <Button className="auth-submit" type="primary" htmlType="submit" block loading={loading}>
          注册
        </Button>
      </Form>
    </AuthLayout>
  );
}
