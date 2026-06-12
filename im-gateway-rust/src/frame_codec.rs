use crate::proto::im::ws::v1::Frame;
use anyhow::{Context, Result};
use prost::Message;

pub const CURRENT_PROTOCOL_VERSION: u32 = 1;

pub fn encode(frame: &Frame) -> Vec<u8> {
    frame.encode_to_vec()
}

pub fn decode(payload: &[u8]) -> Result<Frame> {
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
    use super::{decode, encode, new_frame};
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
}
