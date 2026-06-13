import { Button, Form, Input, App as AntApp } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { AuthLayout, AuthLink } from './AuthLayout';

interface LoginFormValues {
  account: string;
  password: string;
}

export function LoginPage() {
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const login = useAuthStore((state) => state.login);
  const loading = useAuthStore((state) => state.loading);

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
      title="IM Chat"
      subtitle="使用账号密码登录，进入实时消息工作台。"
      footer={
        <>
          没有账号？ <AuthLink to="/register">注册</AuthLink>
        </>
      }
    >
      <Form layout="vertical" onFinish={handleFinish} requiredMark={false}>
        <Form.Item name="account" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input prefix={<UserOutlined />} autoComplete="username" placeholder="手机号或用户名" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="至少 8 位" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={loading}>
          登录
        </Button>
      </Form>
    </AuthLayout>
  );
}
