import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Input, Tooltip, Upload, App as AntApp } from 'antd';
import { AudioOutlined, FileOutlined, PictureOutlined, SendOutlined, LoadingOutlined, UserSwitchOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { ApiError } from '../../../api/client';
import { claimCsConversation, getCsConversation } from '../../../api/cs';
import { EmojiPicker } from '../../../components/EmojiPicker';
import { imSocket } from '../../../socket/ImSocket';
import { useFileUpload } from '../../../hooks/useFileUpload';
import { useRecorder } from '../../../hooks/useRecorder';
import { useAuthStore } from '../../../store/authStore';
import { useConvStore } from '../../../store/convStore';
import { useUiStore } from '../../../store/uiStore';
import { idToString } from '../../../utils/id';

export function InputBar({ convId }: { convId: string }) {
  const conv = useConvStore((state) => state.conversations.get(convId));
  const agentStatus = useAuthStore((state) => Number(state.user?.agentStatus ?? 0));
  const [text, setText] = useState('');
  const inputRef = useRef<TextAreaRef | null>(null);
  const upsertConv = useConvStore((state) => state.upsertConv);
  const requestCsRefresh = useUiStore((state) => state.requestCsRefresh);
  const { message } = AntApp.useApp();
  const { upload, uploading, progress } = useFileUpload();
  const { recording, start: startRecord, stop: stopRecord } = useRecorder();
  const [sendingVoice, setSendingVoice] = useState(false);
  const [claiming, setClaiming] = useState(false);

  // 图片粘贴发送
  const handlePaste = useCallback(
    async (event: ClipboardEvent) => {
      const items = Array.from(event.clipboardData?.items ?? []);
      const imageItem = items.find((item) => item.type.startsWith('image/'));
      if (!imageItem) return;
      event.preventDefault();
      const file = imageItem.getAsFile();
      if (!file) return;
      try {
        const result = await upload(file);
        const previewUrl = URL.createObjectURL(file);
        imSocket.sendImage(convId, {
          objectKey: result.objectKey,
          thumbKey: result.thumbKey,
          width: result.width,
          height: result.height,
          size: file.size,
          mime: file.type,
          previewUrl,
        });
      } catch (error) {
        message.error(error instanceof Error ? error.message : '图片上传失败');
      }
    },
    [convId, upload, message],
  );

  useEffect(() => {
    document.addEventListener('paste', handlePaste);
    return () => document.removeEventListener('paste', handlePaste);
  }, [handlePaste]);

  function sendText() {
    const value = text.trim();
    if (!value) return;
    imSocket.sendText(convId, value);
    setText('');
  }

  async function handleClaim() {
    if (!conv || claiming) return;
    setClaiming(true);
    try {
      await claimCsConversation(convId);
      try {
        const item = await getCsConversation(convId);
        upsertConv({
          ...conv,
          title: item.visitorName || conv.title,
          peerUserId: idToString(item.visitorUserId),
          maxSeq: idToString(item.maxSeq),
          lastMsgAbstract: item.lastMsgAbstract || conv.lastMsgAbstract,
          lastMsgTime: idToString(item.lastMsgTimeMs),
          csStatus: idToString(item.csStatus),
          visitorOnline: item.visitorOnline,
          visitorReadSeq: idToString(item.visitorReadSeq ?? 0),
          peerReadSeq: idToString(item.visitorReadSeq ?? conv.peerReadSeq ?? 0),
        });
      } catch {
        upsertConv({ ...conv, csStatus: '2' });
      }
      requestCsRefresh();
      message.success('已认领会话');
    } catch (error) {
      message.error(readableError(error));
    } finally {
      setClaiming(false);
    }
  }

  const imageUploadProps: UploadProps = {
    showUploadList: false,
    accept: 'image/*',
    beforeUpload: async (file) => {
      try {
        const result = await upload(file);
        // 发送前生成本地预览 URL，先乐观显示，后续由 useMediaUrl 替换为预签名 URL
        const previewUrl = URL.createObjectURL(file);
        imSocket.sendImage(convId, {
          objectKey: result.objectKey,
          thumbKey: result.thumbKey,
          width: result.width,
          height: result.height,
          size: file.size,
          mime: file.type,
          previewUrl,
        });
      } catch (error) {
        message.error(error instanceof Error ? error.message : '图片上传失败');
      }
      return Upload.LIST_IGNORE;
    },
  };

  const fileUploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      if (file.type.startsWith('image/')) {
        message.warning('请使用图片按钮发送图片');
        return Upload.LIST_IGNORE;
      }
      try {
        const result = await upload(file);
        imSocket.sendFile(convId, {
          objectKey: result.objectKey,
          fileName: file.name,
          size: file.size,
          mime: file.type,
        });
      } catch (error) {
        message.error(error instanceof Error ? error.message : '文件上传失败');
      }
      return Upload.LIST_IGNORE;
    },
  };

  async function handleVoiceClick() {
    if (recording) {
      setSendingVoice(true);
      try {
        const { blob, durationMs } = await stopRecord();
        if (durationMs < 500) {
          message.warning('录音时间太短');
          return;
        }
        const voiceFile = new File([blob], 'voice.webm', { type: 'audio/webm;codecs=opus' });
        const result = await upload(voiceFile);
        imSocket.sendVoice(convId, {
          objectKey: result.objectKey,
          durationMs,
          size: blob.size,
        });
      } catch (error) {
        message.error(error instanceof Error ? error.message : '语音发送失败');
      } finally {
        setSendingVoice(false);
      }
    } else {
      try {
        await startRecord();
      } catch {
        message.error('无法访问麦克风，请检查浏览器权限');
      }
    }
  }

  if (conv?.type === 4) {
    return (
      <footer className="input-bar">
        <div className="input-disabled-notice">
          <span>系统通知会话，不可回复</span>
        </div>
      </footer>
    );
  }

  if (conv?.type === 3 && conv.csStatus !== '2') {
    const canClaim = conv.csStatus === '1' && agentStatus === 1;
    return (
      <footer className="input-bar">
        <div className="input-disabled-notice">
          <span>{disabledNotice(conv.csStatus, agentStatus)}</span>
          {conv.csStatus === '1' && (
            <Button type="primary" icon={<UserSwitchOutlined />} loading={claiming} disabled={!canClaim} onClick={handleClaim}>
              认领会话
            </Button>
          )}
        </div>
      </footer>
    );
  }

  return (
    <footer className="input-bar">
      <div className="input-tools">
        <EmojiPicker
          onSelect={(emoji) => {
            setText((current) => `${current}${emoji}`);
            inputRef.current?.focus();
          }}
        />
        <Upload {...imageUploadProps}>
          <Tooltip title="图片">
            <Button
              type="text"
              icon={<PictureOutlined />}
              loading={uploading && progress < 100}
              aria-label="图片"
            />
          </Tooltip>
        </Upload>
        <Upload {...fileUploadProps}>
          <Tooltip title="文件">
            <Button type="text" icon={<FileOutlined />} aria-label="文件" />
          </Tooltip>
        </Upload>
        <Tooltip title={recording ? '点击停止录音并发送' : '点击开始录音'}>
          <Button
            type={recording ? 'primary' : 'text'}
            danger={recording}
            icon={sendingVoice ? <LoadingOutlined /> : <AudioOutlined />}
            onClick={handleVoiceClick}
            aria-label={recording ? '停止录音' : '语音'}
          />
        </Tooltip>
      </div>
      <div className="input-compose">
        <Input.TextArea
          ref={inputRef}
          value={text}
          autoSize={{ minRows: 1, maxRows: 5 }}
          placeholder={recording ? '录音中...' : '输入消息'}
          onChange={(event) => setText(event.target.value)}
          onPressEnter={(event) => {
            if (!event.shiftKey) {
              event.preventDefault();
              sendText();
            }
          }}
        />
        <Button type="primary" icon={<SendOutlined />} onClick={sendText} disabled={!text.trim()}>
          发送
        </Button>
      </div>
    </footer>
  );
}

function disabledNotice(csStatus: string | undefined, agentStatus: number): string {
  if (csStatus === '1') {
    if (agentStatus === 1) {
      return '认领会话后可查看记录并回复访客';
    }
    if (agentStatus === 2) {
      return '当前忙碌中，切换为在线后可认领新访客';
    }
    return '当前离线，切换为在线后可认领新访客';
  }
  return '会话已结单，不能继续发送消息';
}

function readableError(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return '操作失败，请稍后重试';
}
