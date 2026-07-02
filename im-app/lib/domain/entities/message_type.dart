/// 消息类型。和 protobuf MsgContent oneof 保持一一对应，Custom 是租户扩展出口。
enum MessageType { text, image, voice, file, video, notification, custom }
