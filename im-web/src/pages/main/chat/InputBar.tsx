import { useCallback, useEffect, useRef, useState } from 'react';
import { Button, Input, Tooltip, Upload, App as AntApp } from 'antd';
import { AudioOutlined, FileOutlined, PictureOutlined, SendOutlined, LoadingOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { EmojiPicker } from '../../../components/EmojiPicker';
import { imSocket } from '../../../socket/ImSocket';
import { useFileUpload } from '../../../hooks/useFileUpload';
import { useRecorder } from '../../../hooks/useRecorder';

export function InputBar({ convId }: { convId: string }) {
  const [text, setText] = useState('');
  const inputRef = useRef<TextAreaRef | null>(null);
  const { message } = AntApp.useApp();
  const { upload, uploading, progress } = useFileUpload();
  const { recording, start: startRecord, stop: stopRecord } = useRecorder();
  const [sendingVoice, setSendingVoice] = useState(false);

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
