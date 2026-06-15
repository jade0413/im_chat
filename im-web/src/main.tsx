import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider, App as AntApp, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import 'antd/dist/reset.css';
import './styles.css';
import { App } from './App';

ReactDOM.createRoot(document.getElementById('root')!).render(
  <ConfigProvider
    locale={zhCN}
    theme={{
      algorithm: theme.defaultAlgorithm,
      token: {
        colorPrimary: '#1677ff',
        colorBgContainer: '#ffffff',
        borderRadius: 10,
        borderRadiusLG: 14,
        controlHeight: 38,
        wireframe: false,
        boxShadowSecondary: '0 8px 24px rgba(15, 23, 42, 0.08)',
        fontFamily:
          '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
      },
      components: {
        Button: {
          controlHeight: 38,
          borderRadius: 10,
          primaryShadow: '0 4px 12px rgba(22, 119, 255, 0.28)',
        },
        Input: { controlHeight: 40, borderRadius: 12 },
        Modal: { borderRadiusLG: 16 },
        Card: { borderRadiusLG: 16 },
      },
    }}
  >
    <AntApp>
      <App />
    </AntApp>
  </ConfigProvider>,
);
