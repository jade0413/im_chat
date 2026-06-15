import { useEffect, useState } from 'react';
import {
  CustomerServiceOutlined,
  LogoutOutlined,
  MessageOutlined,
  SettingOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { Badge, Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import { imSocket } from '../../../socket/ImSocket';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { useFriendStore } from '../../../store/friendStore';
import { useUiStore } from '../../../store/uiStore';
import type { NavTab } from '../../../store/uiStore';
import { ProfileDrawer } from './ProfileDrawer';

export function NavSidebar() {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);
  const activeTab = useUiStore((state) => state.activeTab);
  const setActiveTab = useUiStore((state) => state.setActiveTab);
  const setActiveConv = useConvStore((state) => state.setActive);
  const pendingCount = useFriendStore((state) => state.pendingCount);
  const loadRequests = useFriendStore((state) => state.loadRequests);
  const [profileOpen, setProfileOpen] = useState(false);

  useEffect(() => {
    // 预加载好友申请，联系人图标可显示红点
    void loadRequests();
  }, [loadRequests]);

  function handleTab(tab: NavTab) {
    // 通讯录是独立面板（非会话列表），切入时清空右侧聊天，避免移动端只看到旧聊天
    if (tab === 'contacts') {
      setActiveConv(null);
    }
    setActiveTab(tab);
  }

  function handleLogout() {
    imSocket.disconnect();
    logout();
    navigate('/login', { replace: true });
  }

  function tabBtn(tab: NavTab, label: string, icon: React.ReactNode) {
    return (
      <Tooltip title={label} placement="right">
        <button
          className={`icon-button ${activeTab === tab ? 'active' : ''}`}
          type="button"
          aria-label={label}
          onClick={() => handleTab(tab)}
        >
          {icon}
        </button>
      </Tooltip>
    );
  }

  return (
    <aside className="nav-sidebar">
      <div className="nav-logo">IM</div>
      <nav className="nav-actions" aria-label="主导航">
        {tabBtn('chats', '消息', <MessageOutlined />)}
        {tabBtn(
          'contacts',
          '联系人',
          <Badge count={pendingCount} size="small" offset={[2, -2]}>
            <UserOutlined />
          </Badge>,
        )}
        {tabBtn('groups', '群组', <TeamOutlined />)}
        {tabBtn('cs', '客服', <CustomerServiceOutlined />)}
      </nav>
      <div className="nav-bottom">
        <Tooltip title="个人资料" placement="right">
          <button className="icon-button" type="button" aria-label="个人资料" onClick={() => setProfileOpen(true)}>
            <SettingOutlined />
          </button>
        </Tooltip>
        <Tooltip title="退出登录" placement="right">
          <button className="icon-button" type="button" aria-label="退出登录" onClick={handleLogout}>
            <LogoutOutlined />
          </button>
        </Tooltip>
      </div>

      <ProfileDrawer open={profileOpen} onClose={() => setProfileOpen(false)} />
    </aside>
  );
}
