package com.im.file.service;

import com.im.file.dao.entity.FileTranscodeJobEntity;

public interface FileTranscoder {

  TranscodeResult transcode(FileTranscodeJobEntity job) throws Exception;
}
