import { useEffect, useState } from 'react';
import { App as AntApp, Avatar, Button, Divider, Drawer, Form, Input, Switch, Upload } from 'antd';
import { CameraOutlined, UserOutlined } from '@ant-design/icons';
import type { UploadChangeParam } from 'antd/es/upload';
import { updateProfile, updateUsername } from '../../../api/user';
import { updateFriendSettings } from '../../../api/friend';
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
  const [usernameInput, setUsernameInput] = useState('');
  const [savingUsername, setSavingUsername] = useState(false);
  const [verifyRequired, setVerifyRequired] = useState(true);
  const [savingVerify, setSavingVerify] = useState(false);

  useEffect(() => {
    if (open && user) {
      form.setFieldsValue({ nickname: user.nickname });
      setAvatarUrl(user.avatar);
      setUsernameInput(user.username ?? '');
      setVerifyRequired((user.friendVerifyRequired ?? 1) === 1);
    }
  }, [open, user, form]);

  async function handleSaveUsername() {
    const value = usernameInput.trim();
    if (!/^[a-z][a-z0-9_]{5,31}$/.test(value)) {
      message.error('用户名须字母开头，小写字母/数字/下划线，6-32 位');
      return;
    }
    setSavingUsername(true);
    try {
      await updateUsername(value);
      setUser({ ...user!, username: value });
      message.success('用户名已设置');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '设置失败');
    } finally {
      setSavingUsername(false);
    }
  }

  async function handleVerifyChange(checked: boolean) {
    setSavingVerify(true);
    try {
      await updateFriendSettings(checked ? 1 : 0);
      setVerifyRequired(checked);
      setUser({ ...user!, friendVerifyRequired: checked ? 1 : 0 });
      message.success(checked ? '已开启：加我需要验证' : '已关闭：免验证直接添加');
    } catch (err) {
      message.error(err instanceof Error ? err.message : '设置失败');
    } finally {
      setSavingVerify(false);
    }
  }

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

      {/* 用户名（可分享的加好友标识，D42） */}
      <Form.Item
        label="用户名"
        help="字母开头，小写字母/数字/下划线，6-32 位；可分享给别人加你"
        style={{ marginBottom: 16 }}
      >
        <Input.Group compact>
          <Input
            style={{ width: 'calc(100% - 76px)' }}
            prefix="@"
            placeholder="设置后可分享"
            value={usernameInput}
            maxLength={32}
            onChange={(e) => setUsernameInput(e.target.value.toLowerCase())}
          />
          <Button
            type="primary"
            style={{ width: 76 }}
            loading={savingUsername}
            disabled={!usernameInput || usernameInput === (user?.username ?? '')}
            onClick={handleSaveUsername}
          >
            {user?.username ? '修改' : '设置'}
          </Button>
        </Input.Group>
      </Form.Item>

      <Divider style={{ margin: '8px 0 16px' }} />

      {/* 加好友设置（D40） */}
      <div className="profile-setting-row">
        <div>
          <div className="profile-setting-title">加我需要验证</div>
          <div className="profile-setting-hint">关闭后，别人可免验证直接添加你为好友</div>
        </div>
        <Switch checked={verifyRequired} loading={savingVerify} onChange={handleVerifyChange} />
      </div>

      <Divider style={{ margin: '16px 0' }} />

      {/* 用户 ID（只读） */}
      <Form.Item label="用户 ID" style={{ marginBottom: 0 }}>
        <Input value={String(user?.id ?? '')} disabled />
      </Form.Item>
    </Drawer>
  );
}
