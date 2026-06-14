import { useState } from 'react';
import { LogoutOutlined, MessageOutlined, SettingOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import { imSocket } from '../../../socket/ImSocket';
import { useAuthStore } from '../../../store/authStore';
import { useUiStore } from '../../../store/uiStore';
import type { NavTab } from '../../../store/uiStore';
import { ProfileDrawer } from './ProfileDrawer';

export function NavSidebar() {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);
  const activeTab = useUiStore((state) => state.activeTab);
  const setActiveTab = useUiStore((state) => state.setActiveTab);
  const [profileOpen, setProfileOpen] = useState(false);

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
          onClick={() => setActiveTab(tab)}
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
        {tabBtn('contacts', '联系人', <UserOutlined />)}
        {tabBtn('groups', '群组', <TeamOutlined />)}
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
