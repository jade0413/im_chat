import { useEffect, useState } from 'react';
import { App as AntApp, Avatar, Button, Drawer, Form, Input, Upload } from 'antd';
import { CameraOutlined, UserOutlined } from '@ant-design/icons';
import type { UploadChangeParam } from 'antd/es/upload';
import { updateProfile } from '../../../api/user';
import { useFileUpload } from '../../../hooks/useFileUpload';
import { useAuthStore } from '../../../store/authStore';

interface ProfileDrawerProps {
  open: boolean;
  onClose: () => void;
}

export function ProfileDrawer({ open, onClose }: ProfileDrawerProps) {
  const { message } = AntApp.useApp();
  const user = useAuthStore((state) => state.user);
  const setUser = useAuthStore((state) => state.setUser);
  const { upload, uploading } = useFileUpload();
  const [saving, setSaving] = useState(false);
  const [form] = Form.useForm<{ nickname: string }>();
  const [avatarUrl, setAvatarUrl] = useState<string | undefined>(user?.avatar);

  useEffect(() => {
    if (open && user) {
      form.setFieldsValue({ nickname: user.nickname });
      setAvatarUrl(user.avatar);
    }
  }, [open, user, form]);

  async function handleAvatarUpload(info: UploadChangeParam) {
    const file = info.file.originFileObj;
    if (!file) return;
    try {
      const result = await upload(file);
      // 用上传完成的 objectKey 作为临时 URL（实际展示用预签名，这里只存 key）
      const previewUrl = URL.createObjectURL(file);
      setAvatarUrl(previewUrl);
      // 立即保存头像
      const updated = await updateProfile({ avatar: result.objectKey });
      setUser({ ...user!, avatar: updated.avatar });
      message.success('头像已更新');
    } catch {
      message.error('头像上传失败');
    }
  }

  async function handleSave() {
    const values = await form.validateFields();
    setSaving(true);
    try {
      const updated = await updateProfile({ nickname: values.nickname });
      setUser({ ...user!, nickname: updated.nickname });
      message.success('资料已保存');
      onClose();
    } catch {
      message.error('保存失败，请重试');
    } finally {
      setSaving(false);
    }
  }

  return (
    <Drawer
      title="个人资料"
      placement="left"
      width={320}
      open={open}
      onClose={onClose}
      footer={
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button onClick={onClose}>取消</Button>
          <Button type="primary" loading={saving} onClick={handleSave}>
            保存
          </Button>
        </div>
      }
    >
      {/* 头像 */}
      <div style={{ display: 'flex', justifyContent: 'center', marginBottom: 24 }}>
        <Upload
          showUploadList={false}
          accept="image/*"
          beforeUpload={() => Upload.LIST_IGNORE}
          onChange={handleAvatarUpload}
        >
          <div style={{ position: 'relative', cursor: 'pointer' }}>
            <Avatar
              size={80}
              src={avatarUrl}
              icon={<UserOutlined />}
              style={{ display: 'block' }}
            >
              {user?.nickname?.charAt(0)}
            </Avatar>
            <div
              style={{
                position: 'absolute',
                inset: 0,
                borderRadius: '50%',
                background: 'rgba(0,0,0,0.38)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                opacity: uploading ? 1 : 0,
                transition: 'opacity 0.2s',
              }}
              className="avatar-overlay"
            >
              <CameraOutlined style={{ color: '#fff', fontSize: 20 }} />
            </div>
          </div>
        </Upload>
      </div>

      {/* 账号（只读） */}
      <Form.Item label="账号" style={{ marginBottom: 16 }}>
        <Input value={user?.account} disabled />
      </Form.Item>

      {/* 昵称 */}
      <Form form={form} layout="vertical">
        <Form.Item
          label="昵称"
          name="nickname"
          rules={[
            { required: true, message: '昵称不能为空' },
            { max: 32, message: '最多 32 个字符' },
          ]}
        >
          <Input placeholder="输入你的昵称" maxLength={32} showCount />
        </Form.Item>
      </Form>

      {/* 用户 ID（只读） */}
      <Form.Item label="用户 ID" style={{ marginBottom: 0 }}>
        <Input value={String(user?.id ?? '')} disabled />
      </Form.Item>
    </Drawer>
  );
}
