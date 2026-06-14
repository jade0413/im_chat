import { EmptyState } from '../../components/EmptyState';
import { NetworkStatusBanner } from '../../components/NetworkStatusBanner';
import { useSocket } from '../../hooks/useSocket';
import { useConvStore } from '../../store/convStore';
import { ChatPanel } from './chat/ChatPanel';
import { ConvListPanel } from './convlist/ConvListPanel';
import { NavSidebar } from './sidebar/NavSidebar';

export function MainLayout() {
  useSocket();
  const activeConvId = useConvStore((state) => state.activeConvId);

  return (
    <>
      <NetworkStatusBanner />
      <div className={`main-shell ${activeConvId ? 'has-active-chat' : ''}`}>
        <NavSidebar />
        <ConvListPanel />
        {activeConvId ? <ChatPanel convId={activeConvId} /> : <EmptyState />}
      </div>
    </>
  );
}
