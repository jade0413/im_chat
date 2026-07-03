package com.im.file.service;

import com.im.file.config.FileProperties;
import java.nio.file.Path;

public interface FfmpegRunner {

  void run(Path source, Path target, FileProperties.Transcode transcode) throws Exception;
}
