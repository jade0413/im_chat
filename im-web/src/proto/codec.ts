import Long from 'long';
import { im } from './generated/bundle';

export const WsCmd = im.ws.v1.Cmd;
export const Frame = im.ws.v1.Frame;
export const AuthReq = im.ws.v1.AuthReq;
export const AuthResp = im.ws.v1.AuthResp;
export const KickNotify = im.ws.v1.KickNotify;

export const MsgSend = im.body.v1.MsgSend;
export const MsgSendAck = im.body.v1.MsgSendAck;
export const MsgPush = im.body.v1.MsgPush;
export const MsgRecvAck = im.body.v1.MsgRecvAck;
export const SyncReq = im.body.v1.SyncReq;
export const SyncResp = im.body.v1.SyncResp;
export const ReadReport = im.body.v1.ReadReport;
export const ReadNotify = im.body.v1.ReadNotify;
export const RevokeNotify = im.body.v1.RevokeNotify;
export const ConvNotify = im.body.v1.ConvNotify;
export const ErrorBody = im.body.v1.ErrorBody;

export const MsgContent = im.common.v1.MsgContent;
export const ConvType = im.common.v1.ConvType;
export const RevokeReason = im.common.v1.RevokeReason;

let reqId = 1;

export function nextReqId(): Long {
  reqId = reqId >= Number.MAX_SAFE_INTEGER - 1 ? 1 : reqId + 1;
  return Long.fromNumber(reqId);
}

export function encodeFrame(cmd: number, body?: Uint8Array, reqIdOverride?: Long): Uint8Array {
  return Frame.encode(
    Frame.create({
      version: 1,
      reqId: reqIdOverride ?? nextReqId(),
      cmd,
      body: body ?? new Uint8Array(),
    }),
  ).finish();
}

export function decodeFrame(data: ArrayBuffer): im.ws.v1.IFrame {
  return Frame.decode(new Uint8Array(data));
}
