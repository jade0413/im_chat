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
