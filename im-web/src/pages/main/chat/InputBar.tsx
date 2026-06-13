import { useRef, useState } from 'react';
import { Button, Input, Tooltip, Upload, App as AntApp } from 'antd';
import { AudioOutlined, FileOutlined, PictureOutlined, SendOutlined } from '@ant-design/icons';
import type { UploadProps } from 'antd';
import type { TextAreaRef } from 'antd/es/input/TextArea';
import { EmojiPicker } from '../../../components/EmojiPicker';
import { imSocket } from '../../../socket/ImSocket';
import { useFileUpload } from '../../../hooks/useFileUpload';

export function InputBar({ convId }: { convId: string }) {
  const [text, setText] = useState('');
  const inputRef = useRef<TextAreaRef | null>(null);
  const { message } = AntApp.useApp();
  const { upload, uploading, progress } = useFileUpload();

  function sendText() {
    const value = text.trim();
    if (!value) {
      return;
    }
    imSocket.sendText(convId, value);
    setText('');
  }

  const uploadProps: UploadProps = {
    showUploadList: false,
    beforeUpload: async (file) => {
      try {
        await upload(file);
        message.info('文件已上传，富媒体发送将在协议联调后启用');
      } catch (error) {
        message.error(error instanceof Error ? error.message : '文件上传失败');
      }
      return Upload.LIST_IGNORE;
    },
  };

  return (
    <footer className="input-bar">
      <div className="input-tools">
        <EmojiPicker
          onSelect={(emoji) => {
            setText((current) => `${current}${emoji}`);
            inputRef.current?.focus();
          }}
        />
        <Upload {...uploadProps} accept="image/*">
          <Tooltip title="图片">
            <Button type="text" icon={<PictureOutlined />} loading={uploading && progress < 100} aria-label="图片" />
          </Tooltip>
        </Upload>
        <Upload {...uploadProps}>
          <Tooltip title="文件">
            <Button type="text" icon={<FileOutlined />} aria-label="文件" />
          </Tooltip>
        </Upload>
        <Tooltip title="语音">
          <Button type="text" icon={<AudioOutlined />} aria-label="语音" />
        </Tooltip>
      </div>
      <div className="input-compose">
        <Input.TextArea
          ref={inputRef}
          value={text}
          autoSize={{ minRows: 1, maxRows: 5 }}
          placeholder="输入消息"
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
