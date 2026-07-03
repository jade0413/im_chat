package com.im.file.service;

import com.im.file.config.FileProperties;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ProcessFfmpegRunner implements FfmpegRunner {

  @Override
  public void run(Path source, Path target, FileProperties.Transcode transcode) throws Exception {
    List<String> command = List.of(
        transcode.ffmpegPath(),
        "-y",
        "-i",
        source.toString(),
        "-vf",
        "scale='min(1280,iw)':-2",
        "-c:v",
        "libx264",
        "-preset",
        "veryfast",
        "-crf",
        "28",
        "-c:a",
        "aac",
        "-movflags",
        "+faststart",
        target.toString());
    Process process = new ProcessBuilder(command)
        .redirectErrorStream(true)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .start();
    boolean finished = process.waitFor(transcode.timeout().toMillis(), TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("ffmpeg timed out");
    }
    if (process.exitValue() != 0) {
      throw new IllegalStateException("ffmpeg exited with code " + process.exitValue());
    }
  }
}
