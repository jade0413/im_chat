use crate::proto::im::ws::v1::Frame;
use anyhow::{Context, Result};
use prost::Message;

pub const CURRENT_PROTOCOL_VERSION: u32 = 1;
/// 默认帧上限：64 KB。可通过 IM_GATEWAY_MAX_FRAME_BYTES 环境变量覆盖。
pub const DEFAULT_MAX_FRAME_BYTES: usize = 64 * 1024;

pub fn encode(frame: &Frame) -> Vec<u8> {
    frame.encode_to_vec()
}

/// 用默认帧大小限制解码（`DEFAULT_MAX_FRAME_BYTES`）。
pub fn decode(payload: &[u8]) -> Result<Frame> {
    decode_with_limit(payload, DEFAULT_MAX_FRAME_BYTES)
}

/// 带自定义帧大小限制的解码。超限直接返回错误，防止恶意超大帧造成 OOM。
pub fn decode_with_limit(payload: &[u8], max_bytes: usize) -> Result<Frame> {
    if payload.len() > max_bytes {
        anyhow::bail!(
            "ws frame too large: {} bytes (limit {})",
            payload.len(),
            max_bytes
        );
    }
    Frame::decode(payload).context("invalid ws frame")
}

pub fn new_frame(cmd: i32, req_id: u64, body: Vec<u8>) -> Frame {
    Frame {
        version: CURRENT_PROTOCOL_VERSION,
        req_id,
        cmd,
        body,
    }
}

pub fn gateway_error_body(code: i32, message: impl Into<String>, req_id: u64) -> Vec<u8> {
    // Mirrors body/messages.proto ErrorBody without making the gateway compile business body proto.
    GatewayErrorBody {
        code,
        message: message.into(),
        req_id,
    }
    .encode_to_vec()
}

#[derive(Clone, PartialEq, ::prost::Message)]
struct GatewayErrorBody {
    #[prost(int32, tag = "1")]
    code: i32,
    #[prost(string, tag = "2")]
    message: String,
    #[prost(uint64, tag = "3")]
    req_id: u64,
}

#[cfg(test)]
mod tests {
    use super::{decode, decode_with_limit, encode, new_frame, DEFAULT_MAX_FRAME_BYTES};
    use crate::proto::im::ws::v1::Cmd;

    #[test]
    fn roundtrips_frame() {
        let frame = new_frame(Cmd::Ping as i32, 42, vec![1, 2, 3]);
        let decoded = decode(&encode(&frame)).unwrap();

        assert_eq!(decoded.version, frame.version);
        assert_eq!(decoded.req_id, 42);
        assert_eq!(decoded.cmd, Cmd::Ping as i32);
        assert_eq!(decoded.body, vec![1, 2, 3]);
    }

    #[test]
    fn rejects_frame_exceeding_limit() {
        let oversized = vec![0u8; DEFAULT_MAX_FRAME_BYTES + 1];
        let err = decode_with_limit(&oversized, DEFAULT_MAX_FRAME_BYTES).unwrap_err();
        assert!(err.to_string().contains("too large"), "err={err}");
    }

    #[test]
    fn accepts_frame_at_exact_limit() {
        // 只需不被 size check 拒绝（会被 protobuf 解析拒绝，但不是 size 错误）
        let at_limit = vec![0u8; DEFAULT_MAX_FRAME_BYTES];
        let err = decode_with_limit(&at_limit, DEFAULT_MAX_FRAME_BYTES).unwrap_err();
        assert!(!err.to_string().contains("too large"), "err={err}");
    }
}
