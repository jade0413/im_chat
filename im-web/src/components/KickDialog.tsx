import { Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useUiStore } from '../store/uiStore';

export function KickDialog() {
  const navigate = useNavigate();
  const message = useUiStore((state) => state.kickMessage);
  const setKickMessage = useUiStore((state) => state.setKickMessage);

  return (
    <Modal
      open={Boolean(message)}
      title="账号已下线"
      okText="重新登录"
      cancelButtonProps={{ style: { display: 'none' } }}
      onOk={() => {
        setKickMessage(null);
        navigate('/login', { replace: true });
      }}
      onCancel={() => setKickMessage(null)}
    >
      <p>{message}</p>
    </Modal>
  );
}
