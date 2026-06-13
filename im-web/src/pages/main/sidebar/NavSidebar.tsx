import { LogoutOutlined, MessageOutlined, SettingOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { Tooltip } from 'antd';
import { useNavigate } from 'react-router-dom';
import { imSocket } from '../../../socket/ImSocket';
import { useAuthStore } from '../../../store/authStore';

export function NavSidebar() {
  const navigate = useNavigate();
  const logout = useAuthStore((state) => state.logout);

  function handleLogout() {
    imSocket.disconnect();
    logout();
    navigate('/login', { replace: true });
  }

  return (
    <aside className="nav-sidebar">
      <div className="nav-logo">IM</div>
      <nav className="nav-actions" aria-label="主导航">
        <Tooltip title="消息" placement="right">
          <button className="icon-button active" type="button" aria-label="消息">
            <MessageOutlined />
          </button>
        </Tooltip>
        <Tooltip title="联系人" placement="right">
          <button className="icon-button" type="button" aria-label="联系人">
            <UserOutlined />
          </button>
        </Tooltip>
        <Tooltip title="群组" placement="right">
          <button className="icon-button" type="button" aria-label="群组">
            <TeamOutlined />
          </button>
        </Tooltip>
      </nav>
      <div className="nav-bottom">
        <Tooltip title="设置" placement="right">
          <button className="icon-button" type="button" aria-label="设置">
            <SettingOutlined />
          </button>
        </Tooltip>
        <Tooltip title="退出登录" placement="right">
          <button className="icon-button" type="button" aria-label="退出登录" onClick={handleLogout}>
            <LogoutOutlined />
          </button>
        </Tooltip>
      </div>
    </aside>
  );
}
