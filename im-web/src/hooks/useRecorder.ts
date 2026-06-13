import { useRef, useState } from 'react';

export function useRecorder() {
  const [recording, setRecording] = useState(false);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const startedAtRef = useRef(0);

  async function start() {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    chunksRef.current = [];
    startedAtRef.current = Date.now();
    const recorder = new MediaRecorder(stream, { mimeType: 'audio/webm;codecs=opus' });
    recorder.ondataavailable = (event) => {
      if (event.data.size > 0) {
        chunksRef.current.push(event.data);
      }
    };
    recorder.start();
    mediaRecorderRef.current = recorder;
    setRecording(true);
  }

  async function stop(): Promise<{ blob: Blob; durationMs: number }> {
    const recorder = mediaRecorderRef.current;
    if (!recorder) {
      throw new Error('当前没有正在录制的语音');
    }
    const stopped = new Promise<void>((resolve) => {
      recorder.onstop = () => resolve();
    });
    recorder.stop();
    recorder.stream.getTracks().forEach((track) => track.stop());
    await stopped;
    setRecording(false);
    return {
      blob: new Blob(chunksRef.current, { type: 'audio/webm;codecs=opus' }),
      durationMs: Date.now() - startedAtRef.current,
    };
  }

  return { recording, start, stop };
}
