String mediaMimeFromFileName(String fileName) {
  final ext =
      fileName.contains('.') ? fileName.split('.').last.toLowerCase() : '';
  return mediaMimeFromExtension(ext);
}

String mediaMimeFromExtension(String extension) {
  final ext = extension.startsWith('.')
      ? extension.substring(1).toLowerCase()
      : extension.toLowerCase();
  return switch (ext) {
    'jpg' || 'jpeg' => 'image/jpeg',
    'png' => 'image/png',
    'gif' => 'image/gif',
    'webp' => 'image/webp',
    'heic' => 'image/heic',
    'heif' => 'image/heif',
    'mp4' => 'video/mp4',
    'm4v' => 'video/x-m4v',
    'mov' => 'video/quicktime',
    'webm' => 'video/webm',
    'mkv' => 'video/x-matroska',
    'avi' => 'video/x-msvideo',
    '3gp' => 'video/3gpp',
    '3g2' => 'video/3gpp2',
    'mpeg' || 'mpg' => 'video/mpeg',
    'm4a' => 'audio/mp4',
    'aac' => 'audio/aac',
    'mp3' => 'audio/mpeg',
    'ogg' => 'audio/ogg',
    'opus' => 'audio/opus',
    'wav' => 'audio/wav',
    'pdf' => 'application/pdf',
    'doc' => 'application/msword',
    'docx' =>
      'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
    'xls' => 'application/vnd.ms-excel',
    'xlsx' =>
      'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    'ppt' => 'application/vnd.ms-powerpoint',
    'pptx' =>
      'application/vnd.openxmlformats-officedocument.presentationml.presentation',
    'txt' => 'text/plain',
    'csv' => 'text/csv',
    'zip' => 'application/zip',
    'rar' => 'application/vnd.rar',
    '7z' => 'application/x-7z-compressed',
    _ => 'application/octet-stream',
  };
}
