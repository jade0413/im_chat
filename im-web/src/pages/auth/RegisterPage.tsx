import { Button, Form, Input, App as AntApp } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useAuthStore } from '../../store/authStore';
import { AuthLayout, AuthLink } from './AuthLayout';

interface RegisterFormValues {
  account: string;
  password: string;
  nickname?: string;
}

export function RegisterPage() {
  const navigate = useNavigate();
  const { message } = AntApp.useApp();
  const register = useAuthStore((state) => state.register);
  const loading = useAuthStore((state) => state.loading);

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
      subtitle="MVP 使用账号密码，短信验证码后续接入。"
      footer={
        <>
          已有账号？ <AuthLink to="/login">登录</AuthLink>
        </>
      }
    >
      <Form layout="vertical" onFinish={handleFinish} requiredMark={false}>
        <Form.Item name="account" label="账号" rules={[{ required: true, message: '请输入账号' }]}>
          <Input prefix={<UserOutlined />} autoComplete="username" placeholder="手机号或用户名" />
        </Form.Item>
        <Form.Item name="nickname" label="昵称">
          <Input placeholder="不填则使用账号" />
        </Form.Item>
        <Form.Item
          name="password"
          label="密码"
          rules={[
            { required: true, message: '请输入密码' },
            { min: 8, message: '密码至少 8 位' },
          ]}
        >
          <Input.Password prefix={<LockOutlined />} autoComplete="new-password" placeholder="至少 8 位" />
        </Form.Item>
        <Button type="primary" htmlType="submit" block loading={loading}>
          注册
        </Button>
      </Form>
    </AuthLayout>
  );
}
