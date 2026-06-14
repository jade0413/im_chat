import { useEffect, useRef, useState } from 'react';
import { Button } from 'antd';
import { CaretRightOutlined, PauseOutlined, LoadingOutlined } from '@ant-design/icons';

interface AudioPlayerProps {
  durationMs: number;
  src?: string;
}

export function AudioPlayer({ durationMs, src }: AudioPlayerProps) {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [playing, setPlaying] = useState(false);
  const [currentMs, setCurrentMs] = useState(0);
  const [loadingAudio, setLoadingAudio] = useState(false);
  const totalSec = Math.max(1, Math.round(durationMs / 1000));
  const displaySec = playing ? Math.min(totalSec, Math.round(currentMs / 1000)) : totalSec;

  useEffect(() => {
    if (!src) return;
    const audio = new Audio(src);
    audioRef.current = audio;

    audio.ontimeupdate = () => {
      setCurrentMs(audio.currentTime * 1000);
    };
    audio.onended = () => {
      setPlaying(false);
      setCurrentMs(0);
    };
    audio.onwaiting = () => setLoadingAudio(true);
    audio.oncanplay = () => setLoadingAudio(false);

    return () => {
      audio.pause();
      audio.src = '';
      audioRef.current = null;
      setPlaying(false);
      setCurrentMs(0);
    };
  }, [src]);

  async function togglePlay() {
    const audio = audioRef.current;
    if (!audio) return;
    if (playing) {
      audio.pause();
      setPlaying(false);
    } else {
      setLoadingAudio(true);
      try {
        await audio.play();
        setPlaying(true);
      } finally {
        setLoadingAudio(false);
      }
    }
  }

  const progress = playing && durationMs > 0 ? Math.min(1, currentMs / durationMs) : 0;

  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 180 }}>
      <Button
        shape="circle"
        size="small"
        icon={
          loadingAudio ? (
            <LoadingOutlined />
          ) : playing ? (
            <PauseOutlined />
          ) : (
            <CaretRightOutlined />
          )
        }
        onClick={src ? togglePlay : undefined}
        disabled={!src}
      />
      <div style={{ flex: 1, height: 3, borderRadius: 999, background: 'currentColor', opacity: 0.18, position: 'relative' }}>
        {progress > 0 && (
          <div
            style={{
              position: 'absolute',
              left: 0,
              top: 0,
              height: '100%',
              width: `${progress * 100}%`,
              borderRadius: 999,
              background: 'currentColor',
              opacity: 2, // 覆盖父元素的 0.18
            }}
          />
        )}
      </div>
      <span style={{ minWidth: 28, textAlign: 'right', fontSize: 12 }}>{displaySec}s</span>
    </div>
  );
}
