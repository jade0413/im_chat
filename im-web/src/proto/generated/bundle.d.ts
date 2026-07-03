import * as $protobuf from "protobufjs";
import Long = require("long");
/** Namespace im. */
export namespace im {

    /** Namespace ws. */
    namespace ws {

        /** Namespace v1. */
        namespace v1 {

            /** Cmd enum. */
            enum Cmd {
                CMD_UNSPECIFIED = 0,
                AUTH = 1,
                AUTH_ACK = 2,
                PING = 3,
                PONG = 4,
                KICK = 5,
                MSG_SEND = 10,
                MSG_SEND_ACK = 11,
                MSG_PUSH = 12,
                MSG_RECV_ACK = 13,
                SYNC_REQ = 20,
                SYNC_RESP = 21,
                READ_REPORT = 22,
                READ_NOTIFY = 23,
                REVOKE_NOTIFY = 24,
                CONV_NOTIFY = 25,
                CALL_INVITE = 40,
                CALL_ANSWER = 41,
                CALL_SIGNAL = 42,
                CALL_HANGUP = 43,
                CALL_NOTIFY = 45,
                CALL_ACK = 49,
                ERROR = 99
            }

            /** Properties of a Frame. */
            interface IFrame {

                /** Frame version */
                version?: (number|null);

                /** Frame reqId */
                reqId?: (number|Long|null);

                /** Frame cmd */
                cmd?: (im.ws.v1.Cmd|null);

                /** Frame body */
                body?: (Uint8Array|null);
            }

            /** Represents a Frame. */
            class Frame implements IFrame {

                /**
                 * Constructs a new Frame.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.ws.v1.IFrame);

                /** Frame version. */
                public version: number;

                /** Frame reqId. */
                public reqId: (number|Long);

                /** Frame cmd. */
                public cmd: im.ws.v1.Cmd;

                /** Frame body. */
                public body: Uint8Array;

                /**
                 * Creates a new Frame instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns Frame instance
                 */
                public static create(properties?: im.ws.v1.IFrame): im.ws.v1.Frame;

                /**
                 * Encodes the specified Frame message. Does not implicitly {@link im.ws.v1.Frame.verify|verify} messages.
                 * @param message Frame message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.ws.v1.IFrame, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified Frame message, length delimited. Does not implicitly {@link im.ws.v1.Frame.verify|verify} messages.
                 * @param message Frame message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.ws.v1.IFrame, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a Frame message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns Frame
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.ws.v1.Frame;

                /**
                 * Decodes a Frame message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns Frame
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.ws.v1.Frame;

                /**
                 * Verifies a Frame message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns Frame
                 */
                public static fromObject(object: { [k: string]: any }): im.ws.v1.Frame;

                /**
                 * Creates a plain object from a Frame message. Also converts values to other types if specified.
                 * @param message Frame
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.ws.v1.Frame, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this Frame to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for Frame
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of an AuthReq. */
            interface IAuthReq {

                /** AuthReq token */
                token?: (string|null);

                /** AuthReq tenantId */
                tenantId?: (number|Long|null);

                /** AuthReq deviceId */
                deviceId?: (string|null);

                /** AuthReq platform */
                platform?: (number|null);

                /** AuthReq appVersion */
                appVersion?: (string|null);

                /** AuthReq timestamp */
                timestamp?: (number|Long|null);
            }

            /** Represents an AuthReq. */
            class AuthReq implements IAuthReq {

                /**
                 * Constructs a new AuthReq.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.ws.v1.IAuthReq);

                /** AuthReq token. */
                public token: string;

                /** AuthReq tenantId. */
                public tenantId: (number|Long);

                /** AuthReq deviceId. */
                public deviceId: string;

                /** AuthReq platform. */
                public platform: number;

                /** AuthReq appVersion. */
                public appVersion: string;

                /** AuthReq timestamp. */
                public timestamp: (number|Long);

                /**
                 * Creates a new AuthReq instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns AuthReq instance
                 */
                public static create(properties?: im.ws.v1.IAuthReq): im.ws.v1.AuthReq;

                /**
                 * Encodes the specified AuthReq message. Does not implicitly {@link im.ws.v1.AuthReq.verify|verify} messages.
                 * @param message AuthReq message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.ws.v1.IAuthReq, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified AuthReq message, length delimited. Does not implicitly {@link im.ws.v1.AuthReq.verify|verify} messages.
                 * @param message AuthReq message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.ws.v1.IAuthReq, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes an AuthReq message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns AuthReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.ws.v1.AuthReq;

                /**
                 * Decodes an AuthReq message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns AuthReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.ws.v1.AuthReq;

                /**
                 * Verifies an AuthReq message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates an AuthReq message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns AuthReq
                 */
                public static fromObject(object: { [k: string]: any }): im.ws.v1.AuthReq;

                /**
                 * Creates a plain object from an AuthReq message. Also converts values to other types if specified.
                 * @param message AuthReq
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.ws.v1.AuthReq, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this AuthReq to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for AuthReq
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of an AuthResp. */
            interface IAuthResp {

                /** AuthResp code */
                code?: (number|null);

                /** AuthResp message */
                message?: (string|null);

                /** AuthResp userId */
                userId?: (number|Long|null);

                /** AuthResp serverTs */
                serverTs?: (number|Long|null);

                /** AuthResp heartbeatIntervalSec */
                heartbeatIntervalSec?: (number|null);
            }

            /** Represents an AuthResp. */
            class AuthResp implements IAuthResp {

                /**
                 * Constructs a new AuthResp.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.ws.v1.IAuthResp);

                /** AuthResp code. */
                public code: number;

                /** AuthResp message. */
                public message: string;

                /** AuthResp userId. */
                public userId: (number|Long);

                /** AuthResp serverTs. */
                public serverTs: (number|Long);

                /** AuthResp heartbeatIntervalSec. */
                public heartbeatIntervalSec: number;

                /**
                 * Creates a new AuthResp instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns AuthResp instance
                 */
                public static create(properties?: im.ws.v1.IAuthResp): im.ws.v1.AuthResp;

                /**
                 * Encodes the specified AuthResp message. Does not implicitly {@link im.ws.v1.AuthResp.verify|verify} messages.
                 * @param message AuthResp message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.ws.v1.IAuthResp, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified AuthResp message, length delimited. Does not implicitly {@link im.ws.v1.AuthResp.verify|verify} messages.
                 * @param message AuthResp message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.ws.v1.IAuthResp, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes an AuthResp message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns AuthResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.ws.v1.AuthResp;

                /**
                 * Decodes an AuthResp message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns AuthResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.ws.v1.AuthResp;

                /**
                 * Verifies an AuthResp message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates an AuthResp message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns AuthResp
                 */
                public static fromObject(object: { [k: string]: any }): im.ws.v1.AuthResp;

                /**
                 * Creates a plain object from an AuthResp message. Also converts values to other types if specified.
                 * @param message AuthResp
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.ws.v1.AuthResp, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this AuthResp to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for AuthResp
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a KickNotify. */
            interface IKickNotify {

                /** KickNotify reason */
                reason?: (im.ws.v1.KickNotify.Reason|null);

                /** KickNotify message */
                message?: (string|null);
            }

            /** Represents a KickNotify. */
            class KickNotify implements IKickNotify {

                /**
                 * Constructs a new KickNotify.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.ws.v1.IKickNotify);

                /** KickNotify reason. */
                public reason: im.ws.v1.KickNotify.Reason;

                /** KickNotify message. */
                public message: string;

                /**
                 * Creates a new KickNotify instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns KickNotify instance
                 */
                public static create(properties?: im.ws.v1.IKickNotify): im.ws.v1.KickNotify;

                /**
                 * Encodes the specified KickNotify message. Does not implicitly {@link im.ws.v1.KickNotify.verify|verify} messages.
                 * @param message KickNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.ws.v1.IKickNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified KickNotify message, length delimited. Does not implicitly {@link im.ws.v1.KickNotify.verify|verify} messages.
                 * @param message KickNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.ws.v1.IKickNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a KickNotify message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns KickNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.ws.v1.KickNotify;

                /**
                 * Decodes a KickNotify message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns KickNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.ws.v1.KickNotify;

                /**
                 * Verifies a KickNotify message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a KickNotify message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns KickNotify
                 */
                public static fromObject(object: { [k: string]: any }): im.ws.v1.KickNotify;

                /**
                 * Creates a plain object from a KickNotify message. Also converts values to other types if specified.
                 * @param message KickNotify
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.ws.v1.KickNotify, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this KickNotify to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for KickNotify
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            namespace KickNotify {

                /** Reason enum. */
                enum Reason {
                    REASON_UNSPECIFIED = 0,
                    NEW_DEVICE_LOGIN = 1,
                    TOKEN_EXPIRED = 2,
                    ADMIN_OFFLINE = 3,
                    PROTO_TOO_OLD = 4
                }
            }
        }
    }

    /** Namespace body. */
    namespace body {

        /** Namespace v1. */
        namespace v1 {

            /** Properties of a MsgSend. */
            interface IMsgSend {

                /** MsgSend clientMsgId */
                clientMsgId?: (string|null);

                /** MsgSend toUserId */
                toUserId?: (number|Long|null);

                /** MsgSend groupId */
                groupId?: (number|Long|null);

                /** MsgSend convId */
                convId?: (number|Long|null);

                /** MsgSend content */
                content?: (im.common.v1.IMsgContent|null);

                /** MsgSend ext */
                ext?: ({ [k: string]: string }|null);
            }

            /** Represents a MsgSend. */
            class MsgSend implements IMsgSend {

                /**
                 * Constructs a new MsgSend.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IMsgSend);

                /** MsgSend clientMsgId. */
                public clientMsgId: string;

                /** MsgSend toUserId. */
                public toUserId?: (number|Long|null);

                /** MsgSend groupId. */
                public groupId?: (number|Long|null);

                /** MsgSend convId. */
                public convId?: (number|Long|null);

                /** MsgSend content. */
                public content?: (im.common.v1.IMsgContent|null);

                /** MsgSend ext. */
                public ext: { [k: string]: string };

                /** MsgSend target. */
                public target?: ("toUserId"|"groupId"|"convId");

                /**
                 * Creates a new MsgSend instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns MsgSend instance
                 */
                public static create(properties?: im.body.v1.IMsgSend): im.body.v1.MsgSend;

                /**
                 * Encodes the specified MsgSend message. Does not implicitly {@link im.body.v1.MsgSend.verify|verify} messages.
                 * @param message MsgSend message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IMsgSend, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified MsgSend message, length delimited. Does not implicitly {@link im.body.v1.MsgSend.verify|verify} messages.
                 * @param message MsgSend message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IMsgSend, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a MsgSend message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns MsgSend
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.MsgSend;

                /**
                 * Decodes a MsgSend message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns MsgSend
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.MsgSend;

                /**
                 * Verifies a MsgSend message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a MsgSend message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns MsgSend
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.MsgSend;

                /**
                 * Creates a plain object from a MsgSend message. Also converts values to other types if specified.
                 * @param message MsgSend
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.MsgSend, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this MsgSend to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for MsgSend
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a MsgSendAck. */
            interface IMsgSendAck {

                /** MsgSendAck code */
                code?: (number|null);

                /** MsgSendAck clientMsgId */
                clientMsgId?: (string|null);

                /** MsgSendAck serverMsgId */
                serverMsgId?: (number|Long|null);

                /** MsgSendAck convId */
                convId?: (number|Long|null);

                /** MsgSendAck seq */
                seq?: (number|Long|null);

                /** MsgSendAck serverTime */
                serverTime?: (number|Long|null);
            }

            /** Represents a MsgSendAck. */
            class MsgSendAck implements IMsgSendAck {

                /**
                 * Constructs a new MsgSendAck.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IMsgSendAck);

                /** MsgSendAck code. */
                public code: number;

                /** MsgSendAck clientMsgId. */
                public clientMsgId: string;

                /** MsgSendAck serverMsgId. */
                public serverMsgId: (number|Long);

                /** MsgSendAck convId. */
                public convId: (number|Long);

                /** MsgSendAck seq. */
                public seq: (number|Long);

                /** MsgSendAck serverTime. */
                public serverTime: (number|Long);

                /**
                 * Creates a new MsgSendAck instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns MsgSendAck instance
                 */
                public static create(properties?: im.body.v1.IMsgSendAck): im.body.v1.MsgSendAck;

                /**
                 * Encodes the specified MsgSendAck message. Does not implicitly {@link im.body.v1.MsgSendAck.verify|verify} messages.
                 * @param message MsgSendAck message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IMsgSendAck, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified MsgSendAck message, length delimited. Does not implicitly {@link im.body.v1.MsgSendAck.verify|verify} messages.
                 * @param message MsgSendAck message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IMsgSendAck, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a MsgSendAck message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns MsgSendAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.MsgSendAck;

                /**
                 * Decodes a MsgSendAck message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns MsgSendAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.MsgSendAck;

                /**
                 * Verifies a MsgSendAck message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a MsgSendAck message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns MsgSendAck
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.MsgSendAck;

                /**
                 * Creates a plain object from a MsgSendAck message. Also converts values to other types if specified.
                 * @param message MsgSendAck
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.MsgSendAck, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this MsgSendAck to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for MsgSendAck
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a MsgPush. */
            interface IMsgPush {

                /** MsgPush convId */
                convId?: (number|Long|null);

                /** MsgPush convType */
                convType?: (im.common.v1.ConvType|null);

                /** MsgPush seq */
                seq?: (number|Long|null);

                /** MsgPush serverMsgId */
                serverMsgId?: (number|Long|null);

                /** MsgPush clientMsgId */
                clientMsgId?: (string|null);

                /** MsgPush sender */
                sender?: (im.body.v1.ISender|null);

                /** MsgPush sendTime */
                sendTime?: (number|Long|null);

                /** MsgPush content */
                content?: (im.common.v1.IMsgContent|null);

                /** MsgPush ext */
                ext?: ({ [k: string]: string }|null);

                /** MsgPush contentOmitted */
                contentOmitted?: (boolean|null);

                /** MsgPush omittedReason */
                omittedReason?: (string|null);
            }

            /** Represents a MsgPush. */
            class MsgPush implements IMsgPush {

                /**
                 * Constructs a new MsgPush.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IMsgPush);

                /** MsgPush convId. */
                public convId: (number|Long);

                /** MsgPush convType. */
                public convType: im.common.v1.ConvType;

                /** MsgPush seq. */
                public seq: (number|Long);

                /** MsgPush serverMsgId. */
                public serverMsgId: (number|Long);

                /** MsgPush clientMsgId. */
                public clientMsgId: string;

                /** MsgPush sender. */
                public sender?: (im.body.v1.ISender|null);

                /** MsgPush sendTime. */
                public sendTime: (number|Long);

                /** MsgPush content. */
                public content?: (im.common.v1.IMsgContent|null);

                /** MsgPush ext. */
                public ext: { [k: string]: string };

                /** MsgPush contentOmitted. */
                public contentOmitted: boolean;

                /** MsgPush omittedReason. */
                public omittedReason: string;

                /**
                 * Creates a new MsgPush instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns MsgPush instance
                 */
                public static create(properties?: im.body.v1.IMsgPush): im.body.v1.MsgPush;

                /**
                 * Encodes the specified MsgPush message. Does not implicitly {@link im.body.v1.MsgPush.verify|verify} messages.
                 * @param message MsgPush message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IMsgPush, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified MsgPush message, length delimited. Does not implicitly {@link im.body.v1.MsgPush.verify|verify} messages.
                 * @param message MsgPush message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IMsgPush, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a MsgPush message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns MsgPush
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.MsgPush;

                /**
                 * Decodes a MsgPush message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns MsgPush
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.MsgPush;

                /**
                 * Verifies a MsgPush message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a MsgPush message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns MsgPush
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.MsgPush;

                /**
                 * Creates a plain object from a MsgPush message. Also converts values to other types if specified.
                 * @param message MsgPush
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.MsgPush, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this MsgPush to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for MsgPush
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a Sender. */
            interface ISender {

                /** Sender userId */
                userId?: (number|Long|null);

                /** Sender nickname */
                nickname?: (string|null);

                /** Sender avatar */
                avatar?: (string|null);

                /** Sender verifiedType */
                verifiedType?: (im.common.v1.VerifiedType|null);

                /** Sender userType */
                userType?: (im.common.v1.UserType|null);
            }

            /** Represents a Sender. */
            class Sender implements ISender {

                /**
                 * Constructs a new Sender.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.ISender);

                /** Sender userId. */
                public userId: (number|Long);

                /** Sender nickname. */
                public nickname: string;

                /** Sender avatar. */
                public avatar: string;

                /** Sender verifiedType. */
                public verifiedType: im.common.v1.VerifiedType;

                /** Sender userType. */
                public userType: im.common.v1.UserType;

                /**
                 * Creates a new Sender instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns Sender instance
                 */
                public static create(properties?: im.body.v1.ISender): im.body.v1.Sender;

                /**
                 * Encodes the specified Sender message. Does not implicitly {@link im.body.v1.Sender.verify|verify} messages.
                 * @param message Sender message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.ISender, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified Sender message, length delimited. Does not implicitly {@link im.body.v1.Sender.verify|verify} messages.
                 * @param message Sender message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.ISender, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a Sender message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns Sender
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.Sender;

                /**
                 * Decodes a Sender message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns Sender
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.Sender;

                /**
                 * Verifies a Sender message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a Sender message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns Sender
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.Sender;

                /**
                 * Creates a plain object from a Sender message. Also converts values to other types if specified.
                 * @param message Sender
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.Sender, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this Sender to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for Sender
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a MsgRecvAck. */
            interface IMsgRecvAck {

                /** MsgRecvAck items */
                items?: (im.body.v1.MsgRecvAck.IAckItem[]|null);
            }

            /** Represents a MsgRecvAck. */
            class MsgRecvAck implements IMsgRecvAck {

                /**
                 * Constructs a new MsgRecvAck.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IMsgRecvAck);

                /** MsgRecvAck items. */
                public items: im.body.v1.MsgRecvAck.IAckItem[];

                /**
                 * Creates a new MsgRecvAck instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns MsgRecvAck instance
                 */
                public static create(properties?: im.body.v1.IMsgRecvAck): im.body.v1.MsgRecvAck;

                /**
                 * Encodes the specified MsgRecvAck message. Does not implicitly {@link im.body.v1.MsgRecvAck.verify|verify} messages.
                 * @param message MsgRecvAck message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IMsgRecvAck, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified MsgRecvAck message, length delimited. Does not implicitly {@link im.body.v1.MsgRecvAck.verify|verify} messages.
                 * @param message MsgRecvAck message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IMsgRecvAck, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a MsgRecvAck message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns MsgRecvAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.MsgRecvAck;

                /**
                 * Decodes a MsgRecvAck message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns MsgRecvAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.MsgRecvAck;

                /**
                 * Verifies a MsgRecvAck message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a MsgRecvAck message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns MsgRecvAck
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.MsgRecvAck;

                /**
                 * Creates a plain object from a MsgRecvAck message. Also converts values to other types if specified.
                 * @param message MsgRecvAck
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.MsgRecvAck, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this MsgRecvAck to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for MsgRecvAck
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            namespace MsgRecvAck {

                /** Properties of an AckItem. */
                interface IAckItem {

                    /** AckItem convId */
                    convId?: (number|Long|null);

                    /** AckItem seq */
                    seq?: (number|Long|null);
                }

                /** Represents an AckItem. */
                class AckItem implements IAckItem {

                    /**
                     * Constructs a new AckItem.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: im.body.v1.MsgRecvAck.IAckItem);

                    /** AckItem convId. */
                    public convId: (number|Long);

                    /** AckItem seq. */
                    public seq: (number|Long);

                    /**
                     * Creates a new AckItem instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns AckItem instance
                     */
                    public static create(properties?: im.body.v1.MsgRecvAck.IAckItem): im.body.v1.MsgRecvAck.AckItem;

                    /**
                     * Encodes the specified AckItem message. Does not implicitly {@link im.body.v1.MsgRecvAck.AckItem.verify|verify} messages.
                     * @param message AckItem message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: im.body.v1.MsgRecvAck.IAckItem, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified AckItem message, length delimited. Does not implicitly {@link im.body.v1.MsgRecvAck.AckItem.verify|verify} messages.
                     * @param message AckItem message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: im.body.v1.MsgRecvAck.IAckItem, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes an AckItem message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns AckItem
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.MsgRecvAck.AckItem;

                    /**
                     * Decodes an AckItem message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns AckItem
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.MsgRecvAck.AckItem;

                    /**
                     * Verifies an AckItem message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates an AckItem message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns AckItem
                     */
                    public static fromObject(object: { [k: string]: any }): im.body.v1.MsgRecvAck.AckItem;

                    /**
                     * Creates a plain object from an AckItem message. Also converts values to other types if specified.
                     * @param message AckItem
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: im.body.v1.MsgRecvAck.AckItem, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this AckItem to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for AckItem
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }
            }

            /** Properties of a SyncReq. */
            interface ISyncReq {

                /** SyncReq convVersions */
                convVersions?: (im.body.v1.SyncReq.IConvVersion[]|null);

                /** SyncReq convListVersion */
                convListVersion?: (number|Long|null);
            }

            /** Represents a SyncReq. */
            class SyncReq implements ISyncReq {

                /**
                 * Constructs a new SyncReq.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.ISyncReq);

                /** SyncReq convVersions. */
                public convVersions: im.body.v1.SyncReq.IConvVersion[];

                /** SyncReq convListVersion. */
                public convListVersion: (number|Long);

                /**
                 * Creates a new SyncReq instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns SyncReq instance
                 */
                public static create(properties?: im.body.v1.ISyncReq): im.body.v1.SyncReq;

                /**
                 * Encodes the specified SyncReq message. Does not implicitly {@link im.body.v1.SyncReq.verify|verify} messages.
                 * @param message SyncReq message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.ISyncReq, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified SyncReq message, length delimited. Does not implicitly {@link im.body.v1.SyncReq.verify|verify} messages.
                 * @param message SyncReq message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.ISyncReq, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a SyncReq message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns SyncReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.SyncReq;

                /**
                 * Decodes a SyncReq message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns SyncReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.SyncReq;

                /**
                 * Verifies a SyncReq message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a SyncReq message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns SyncReq
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.SyncReq;

                /**
                 * Creates a plain object from a SyncReq message. Also converts values to other types if specified.
                 * @param message SyncReq
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.SyncReq, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this SyncReq to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for SyncReq
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            namespace SyncReq {

                /** Properties of a ConvVersion. */
                interface IConvVersion {

                    /** ConvVersion convId */
                    convId?: (number|Long|null);

                    /** ConvVersion localMaxSeq */
                    localMaxSeq?: (number|Long|null);
                }

                /** Represents a ConvVersion. */
                class ConvVersion implements IConvVersion {

                    /**
                     * Constructs a new ConvVersion.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: im.body.v1.SyncReq.IConvVersion);

                    /** ConvVersion convId. */
                    public convId: (number|Long);

                    /** ConvVersion localMaxSeq. */
                    public localMaxSeq: (number|Long);

                    /**
                     * Creates a new ConvVersion instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns ConvVersion instance
                     */
                    public static create(properties?: im.body.v1.SyncReq.IConvVersion): im.body.v1.SyncReq.ConvVersion;

                    /**
                     * Encodes the specified ConvVersion message. Does not implicitly {@link im.body.v1.SyncReq.ConvVersion.verify|verify} messages.
                     * @param message ConvVersion message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: im.body.v1.SyncReq.IConvVersion, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified ConvVersion message, length delimited. Does not implicitly {@link im.body.v1.SyncReq.ConvVersion.verify|verify} messages.
                     * @param message ConvVersion message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: im.body.v1.SyncReq.IConvVersion, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a ConvVersion message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns ConvVersion
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.SyncReq.ConvVersion;

                    /**
                     * Decodes a ConvVersion message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns ConvVersion
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.SyncReq.ConvVersion;

                    /**
                     * Verifies a ConvVersion message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a ConvVersion message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns ConvVersion
                     */
                    public static fromObject(object: { [k: string]: any }): im.body.v1.SyncReq.ConvVersion;

                    /**
                     * Creates a plain object from a ConvVersion message. Also converts values to other types if specified.
                     * @param message ConvVersion
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: im.body.v1.SyncReq.ConvVersion, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this ConvVersion to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for ConvVersion
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }
            }

            /** Properties of a SyncResp. */
            interface ISyncResp {

                /** SyncResp deltas */
                deltas?: (im.body.v1.SyncResp.IConvDelta[]|null);

                /** SyncResp convListVersion */
                convListVersion?: (number|Long|null);

                /** SyncResp fullSync */
                fullSync?: (boolean|null);
            }

            /** Represents a SyncResp. */
            class SyncResp implements ISyncResp {

                /**
                 * Constructs a new SyncResp.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.ISyncResp);

                /** SyncResp deltas. */
                public deltas: im.body.v1.SyncResp.IConvDelta[];

                /** SyncResp convListVersion. */
                public convListVersion: (number|Long);

                /** SyncResp fullSync. */
                public fullSync: boolean;

                /**
                 * Creates a new SyncResp instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns SyncResp instance
                 */
                public static create(properties?: im.body.v1.ISyncResp): im.body.v1.SyncResp;

                /**
                 * Encodes the specified SyncResp message. Does not implicitly {@link im.body.v1.SyncResp.verify|verify} messages.
                 * @param message SyncResp message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.ISyncResp, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified SyncResp message, length delimited. Does not implicitly {@link im.body.v1.SyncResp.verify|verify} messages.
                 * @param message SyncResp message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.ISyncResp, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a SyncResp message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns SyncResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.SyncResp;

                /**
                 * Decodes a SyncResp message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns SyncResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.SyncResp;

                /**
                 * Verifies a SyncResp message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a SyncResp message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns SyncResp
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.SyncResp;

                /**
                 * Creates a plain object from a SyncResp message. Also converts values to other types if specified.
                 * @param message SyncResp
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.SyncResp, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this SyncResp to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for SyncResp
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            namespace SyncResp {

                /** Properties of a ConvDelta. */
                interface IConvDelta {

                    /** ConvDelta conv */
                    conv?: (im.body.v1.IConvInfo|null);

                    /** ConvDelta msgs */
                    msgs?: (im.body.v1.IMsgPush[]|null);

                    /** ConvDelta serverMaxSeq */
                    serverMaxSeq?: (number|Long|null);

                    /** ConvDelta hasMore */
                    hasMore?: (boolean|null);
                }

                /** Represents a ConvDelta. */
                class ConvDelta implements IConvDelta {

                    /**
                     * Constructs a new ConvDelta.
                     * @param [properties] Properties to set
                     */
                    constructor(properties?: im.body.v1.SyncResp.IConvDelta);

                    /** ConvDelta conv. */
                    public conv?: (im.body.v1.IConvInfo|null);

                    /** ConvDelta msgs. */
                    public msgs: im.body.v1.IMsgPush[];

                    /** ConvDelta serverMaxSeq. */
                    public serverMaxSeq: (number|Long);

                    /** ConvDelta hasMore. */
                    public hasMore: boolean;

                    /**
                     * Creates a new ConvDelta instance using the specified properties.
                     * @param [properties] Properties to set
                     * @returns ConvDelta instance
                     */
                    public static create(properties?: im.body.v1.SyncResp.IConvDelta): im.body.v1.SyncResp.ConvDelta;

                    /**
                     * Encodes the specified ConvDelta message. Does not implicitly {@link im.body.v1.SyncResp.ConvDelta.verify|verify} messages.
                     * @param message ConvDelta message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encode(message: im.body.v1.SyncResp.IConvDelta, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Encodes the specified ConvDelta message, length delimited. Does not implicitly {@link im.body.v1.SyncResp.ConvDelta.verify|verify} messages.
                     * @param message ConvDelta message or plain object to encode
                     * @param [writer] Writer to encode to
                     * @returns Writer
                     */
                    public static encodeDelimited(message: im.body.v1.SyncResp.IConvDelta, writer?: $protobuf.Writer): $protobuf.Writer;

                    /**
                     * Decodes a ConvDelta message from the specified reader or buffer.
                     * @param reader Reader or buffer to decode from
                     * @param [length] Message length if known beforehand
                     * @returns ConvDelta
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.SyncResp.ConvDelta;

                    /**
                     * Decodes a ConvDelta message from the specified reader or buffer, length delimited.
                     * @param reader Reader or buffer to decode from
                     * @returns ConvDelta
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.SyncResp.ConvDelta;

                    /**
                     * Verifies a ConvDelta message.
                     * @param message Plain object to verify
                     * @returns `null` if valid, otherwise the reason why it is not
                     */
                    public static verify(message: { [k: string]: any }): (string|null);

                    /**
                     * Creates a ConvDelta message from a plain object. Also converts values to their respective internal types.
                     * @param object Plain object
                     * @returns ConvDelta
                     */
                    public static fromObject(object: { [k: string]: any }): im.body.v1.SyncResp.ConvDelta;

                    /**
                     * Creates a plain object from a ConvDelta message. Also converts values to other types if specified.
                     * @param message ConvDelta
                     * @param [options] Conversion options
                     * @returns Plain object
                     */
                    public static toObject(message: im.body.v1.SyncResp.ConvDelta, options?: $protobuf.IConversionOptions): { [k: string]: any };

                    /**
                     * Converts this ConvDelta to JSON.
                     * @returns JSON object
                     */
                    public toJSON(): { [k: string]: any };

                    /**
                     * Gets the default type url for ConvDelta
                     * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns The default type url
                     */
                    public static getTypeUrl(typeUrlPrefix?: string): string;
                }
            }

            /** Properties of a ConvInfo. */
            interface IConvInfo {

                /** ConvInfo convId */
                convId?: (number|Long|null);

                /** ConvInfo type */
                type?: (im.common.v1.ConvType|null);

                /** ConvInfo title */
                title?: (string|null);

                /** ConvInfo avatar */
                avatar?: (string|null);

                /** ConvInfo peerUserId */
                peerUserId?: (number|Long|null);

                /** ConvInfo groupId */
                groupId?: (number|Long|null);

                /** ConvInfo maxSeq */
                maxSeq?: (number|Long|null);

                /** ConvInfo readSeq */
                readSeq?: (number|Long|null);

                /** ConvInfo pinned */
                pinned?: (boolean|null);

                /** ConvInfo muted */
                muted?: (boolean|null);

                /** ConvInfo lastMsgAbstract */
                lastMsgAbstract?: (string|null);

                /** ConvInfo lastMsgTime */
                lastMsgTime?: (number|Long|null);

                /** ConvInfo csStatus */
                csStatus?: (string|null);

                /** ConvInfo deleted */
                deleted?: (boolean|null);
            }

            /** Represents a ConvInfo. */
            class ConvInfo implements IConvInfo {

                /**
                 * Constructs a new ConvInfo.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IConvInfo);

                /** ConvInfo convId. */
                public convId: (number|Long);

                /** ConvInfo type. */
                public type: im.common.v1.ConvType;

                /** ConvInfo title. */
                public title: string;

                /** ConvInfo avatar. */
                public avatar: string;

                /** ConvInfo peerUserId. */
                public peerUserId: (number|Long);

                /** ConvInfo groupId. */
                public groupId: (number|Long);

                /** ConvInfo maxSeq. */
                public maxSeq: (number|Long);

                /** ConvInfo readSeq. */
                public readSeq: (number|Long);

                /** ConvInfo pinned. */
                public pinned: boolean;

                /** ConvInfo muted. */
                public muted: boolean;

                /** ConvInfo lastMsgAbstract. */
                public lastMsgAbstract: string;

                /** ConvInfo lastMsgTime. */
                public lastMsgTime: (number|Long);

                /** ConvInfo csStatus. */
                public csStatus: string;

                /** ConvInfo deleted. */
                public deleted: boolean;

                /**
                 * Creates a new ConvInfo instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ConvInfo instance
                 */
                public static create(properties?: im.body.v1.IConvInfo): im.body.v1.ConvInfo;

                /**
                 * Encodes the specified ConvInfo message. Does not implicitly {@link im.body.v1.ConvInfo.verify|verify} messages.
                 * @param message ConvInfo message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IConvInfo, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ConvInfo message, length delimited. Does not implicitly {@link im.body.v1.ConvInfo.verify|verify} messages.
                 * @param message ConvInfo message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IConvInfo, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a ConvInfo message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ConvInfo
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.ConvInfo;

                /**
                 * Decodes a ConvInfo message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ConvInfo
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.ConvInfo;

                /**
                 * Verifies a ConvInfo message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a ConvInfo message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ConvInfo
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.ConvInfo;

                /**
                 * Creates a plain object from a ConvInfo message. Also converts values to other types if specified.
                 * @param message ConvInfo
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.ConvInfo, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ConvInfo to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ConvInfo
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a ReadReport. */
            interface IReadReport {

                /** ReadReport convId */
                convId?: (number|Long|null);

                /** ReadReport readSeq */
                readSeq?: (number|Long|null);
            }

            /** Represents a ReadReport. */
            class ReadReport implements IReadReport {

                /**
                 * Constructs a new ReadReport.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IReadReport);

                /** ReadReport convId. */
                public convId: (number|Long);

                /** ReadReport readSeq. */
                public readSeq: (number|Long);

                /**
                 * Creates a new ReadReport instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ReadReport instance
                 */
                public static create(properties?: im.body.v1.IReadReport): im.body.v1.ReadReport;

                /**
                 * Encodes the specified ReadReport message. Does not implicitly {@link im.body.v1.ReadReport.verify|verify} messages.
                 * @param message ReadReport message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IReadReport, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ReadReport message, length delimited. Does not implicitly {@link im.body.v1.ReadReport.verify|verify} messages.
                 * @param message ReadReport message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IReadReport, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a ReadReport message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ReadReport
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.ReadReport;

                /**
                 * Decodes a ReadReport message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ReadReport
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.ReadReport;

                /**
                 * Verifies a ReadReport message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a ReadReport message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ReadReport
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.ReadReport;

                /**
                 * Creates a plain object from a ReadReport message. Also converts values to other types if specified.
                 * @param message ReadReport
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.ReadReport, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ReadReport to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ReadReport
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a ReadNotify. */
            interface IReadNotify {

                /** ReadNotify convId */
                convId?: (number|Long|null);

                /** ReadNotify readerUserId */
                readerUserId?: (number|Long|null);

                /** ReadNotify readSeq */
                readSeq?: (number|Long|null);
            }

            /** Represents a ReadNotify. */
            class ReadNotify implements IReadNotify {

                /**
                 * Constructs a new ReadNotify.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IReadNotify);

                /** ReadNotify convId. */
                public convId: (number|Long);

                /** ReadNotify readerUserId. */
                public readerUserId: (number|Long);

                /** ReadNotify readSeq. */
                public readSeq: (number|Long);

                /**
                 * Creates a new ReadNotify instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ReadNotify instance
                 */
                public static create(properties?: im.body.v1.IReadNotify): im.body.v1.ReadNotify;

                /**
                 * Encodes the specified ReadNotify message. Does not implicitly {@link im.body.v1.ReadNotify.verify|verify} messages.
                 * @param message ReadNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IReadNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ReadNotify message, length delimited. Does not implicitly {@link im.body.v1.ReadNotify.verify|verify} messages.
                 * @param message ReadNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IReadNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a ReadNotify message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ReadNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.ReadNotify;

                /**
                 * Decodes a ReadNotify message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ReadNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.ReadNotify;

                /**
                 * Verifies a ReadNotify message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a ReadNotify message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ReadNotify
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.ReadNotify;

                /**
                 * Creates a plain object from a ReadNotify message. Also converts values to other types if specified.
                 * @param message ReadNotify
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.ReadNotify, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ReadNotify to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ReadNotify
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a RevokeNotify. */
            interface IRevokeNotify {

                /** RevokeNotify convId */
                convId?: (number|Long|null);

                /** RevokeNotify seq */
                seq?: (number|Long|null);

                /** RevokeNotify serverMsgId */
                serverMsgId?: (number|Long|null);

                /** RevokeNotify reason */
                reason?: (im.common.v1.RevokeReason|null);

                /** RevokeNotify operatorUserId */
                operatorUserId?: (number|Long|null);
            }

            /** Represents a RevokeNotify. */
            class RevokeNotify implements IRevokeNotify {

                /**
                 * Constructs a new RevokeNotify.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IRevokeNotify);

                /** RevokeNotify convId. */
                public convId: (number|Long);

                /** RevokeNotify seq. */
                public seq: (number|Long);

                /** RevokeNotify serverMsgId. */
                public serverMsgId: (number|Long);

                /** RevokeNotify reason. */
                public reason: im.common.v1.RevokeReason;

                /** RevokeNotify operatorUserId. */
                public operatorUserId: (number|Long);

                /**
                 * Creates a new RevokeNotify instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns RevokeNotify instance
                 */
                public static create(properties?: im.body.v1.IRevokeNotify): im.body.v1.RevokeNotify;

                /**
                 * Encodes the specified RevokeNotify message. Does not implicitly {@link im.body.v1.RevokeNotify.verify|verify} messages.
                 * @param message RevokeNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IRevokeNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified RevokeNotify message, length delimited. Does not implicitly {@link im.body.v1.RevokeNotify.verify|verify} messages.
                 * @param message RevokeNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IRevokeNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a RevokeNotify message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns RevokeNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.RevokeNotify;

                /**
                 * Decodes a RevokeNotify message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns RevokeNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.RevokeNotify;

                /**
                 * Verifies a RevokeNotify message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a RevokeNotify message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns RevokeNotify
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.RevokeNotify;

                /**
                 * Creates a plain object from a RevokeNotify message. Also converts values to other types if specified.
                 * @param message RevokeNotify
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.RevokeNotify, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this RevokeNotify to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for RevokeNotify
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a ConvNotify. */
            interface IConvNotify {

                /** ConvNotify conv */
                conv?: (im.body.v1.IConvInfo|null);

                /** ConvNotify changeType */
                changeType?: (string|null);
            }

            /** Represents a ConvNotify. */
            class ConvNotify implements IConvNotify {

                /**
                 * Constructs a new ConvNotify.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IConvNotify);

                /** ConvNotify conv. */
                public conv?: (im.body.v1.IConvInfo|null);

                /** ConvNotify changeType. */
                public changeType: string;

                /**
                 * Creates a new ConvNotify instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ConvNotify instance
                 */
                public static create(properties?: im.body.v1.IConvNotify): im.body.v1.ConvNotify;

                /**
                 * Encodes the specified ConvNotify message. Does not implicitly {@link im.body.v1.ConvNotify.verify|verify} messages.
                 * @param message ConvNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IConvNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ConvNotify message, length delimited. Does not implicitly {@link im.body.v1.ConvNotify.verify|verify} messages.
                 * @param message ConvNotify message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IConvNotify, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a ConvNotify message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ConvNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.ConvNotify;

                /**
                 * Decodes a ConvNotify message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ConvNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.ConvNotify;

                /**
                 * Verifies a ConvNotify message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a ConvNotify message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ConvNotify
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.ConvNotify;

                /**
                 * Creates a plain object from a ConvNotify message. Also converts values to other types if specified.
                 * @param message ConvNotify
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.ConvNotify, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ConvNotify to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ConvNotify
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of an ErrorBody. */
            interface IErrorBody {

                /** ErrorBody code */
                code?: (number|null);

                /** ErrorBody message */
                message?: (string|null);

                /** ErrorBody reqId */
                reqId?: (number|Long|null);
            }

            /** Represents an ErrorBody. */
            class ErrorBody implements IErrorBody {

                /**
                 * Constructs a new ErrorBody.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.body.v1.IErrorBody);

                /** ErrorBody code. */
                public code: number;

                /** ErrorBody message. */
                public message: string;

                /** ErrorBody reqId. */
                public reqId: (number|Long);

                /**
                 * Creates a new ErrorBody instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ErrorBody instance
                 */
                public static create(properties?: im.body.v1.IErrorBody): im.body.v1.ErrorBody;

                /**
                 * Encodes the specified ErrorBody message. Does not implicitly {@link im.body.v1.ErrorBody.verify|verify} messages.
                 * @param message ErrorBody message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.body.v1.IErrorBody, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ErrorBody message, length delimited. Does not implicitly {@link im.body.v1.ErrorBody.verify|verify} messages.
                 * @param message ErrorBody message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.body.v1.IErrorBody, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes an ErrorBody message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ErrorBody
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.body.v1.ErrorBody;

                /**
                 * Decodes an ErrorBody message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ErrorBody
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.body.v1.ErrorBody;

                /**
                 * Verifies an ErrorBody message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates an ErrorBody message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ErrorBody
                 */
                public static fromObject(object: { [k: string]: any }): im.body.v1.ErrorBody;

                /**
                 * Creates a plain object from an ErrorBody message. Also converts values to other types if specified.
                 * @param message ErrorBody
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.body.v1.ErrorBody, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ErrorBody to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ErrorBody
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }
        }
    }

    /** Namespace common. */
    namespace common {

        /** Namespace v1. */
        namespace v1 {

            /** Platform enum. */
            enum Platform {
                PLATFORM_UNSPECIFIED = 0,
                IOS = 1,
                ANDROID = 2,
                WINDOWS = 3,
                MACOS = 4,
                WEB = 5,
                MINI_PROGRAM = 6
            }

            /** ConvType enum. */
            enum ConvType {
                CONV_TYPE_UNSPECIFIED = 0,
                C2C = 1,
                GROUP = 2,
                CS_SESSION = 3,
                SYSTEM = 4
            }

            /** UserType enum. */
            enum UserType {
                USER_TYPE_UNSPECIFIED = 0,
                MEMBER = 1,
                AGENT = 2,
                VISITOR = 3
            }

            /** VerifiedType enum. */
            enum VerifiedType {
                VERIFIED_NONE = 0,
                PERSONAL = 1,
                ENTERPRISE = 2,
                OFFICIAL_STAFF = 3
            }

            /** MsgStatus enum. */
            enum MsgStatus {
                MSG_STATUS_UNSPECIFIED = 0,
                NORMAL = 1,
                REVOKED = 2
            }

            /** RevokeReason enum. */
            enum RevokeReason {
                REVOKE_REASON_UNSPECIFIED = 0,
                BY_SENDER = 1,
                BY_MODERATION = 2,
                BY_ADMIN = 3
            }

            /** Properties of a MsgContent. */
            interface IMsgContent {

                /** MsgContent text */
                text?: (im.common.v1.ITextContent|null);

                /** MsgContent image */
                image?: (im.common.v1.IImageContent|null);

                /** MsgContent voice */
                voice?: (im.common.v1.IVoiceContent|null);

                /** MsgContent file */
                file?: (im.common.v1.IFileContent|null);

                /** MsgContent notification */
                notification?: (im.common.v1.INotificationContent|null);

                /** MsgContent custom */
                custom?: (im.common.v1.ICustomContent|null);
            }

            /** Represents a MsgContent. */
            class MsgContent implements IMsgContent {

                /**
                 * Constructs a new MsgContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.IMsgContent);

                /** MsgContent text. */
                public text?: (im.common.v1.ITextContent|null);

                /** MsgContent image. */
                public image?: (im.common.v1.IImageContent|null);

                /** MsgContent voice. */
                public voice?: (im.common.v1.IVoiceContent|null);

                /** MsgContent file. */
                public file?: (im.common.v1.IFileContent|null);

                /** MsgContent notification. */
                public notification?: (im.common.v1.INotificationContent|null);

                /** MsgContent custom. */
                public custom?: (im.common.v1.ICustomContent|null);

                /** MsgContent content. */
                public content?: ("text"|"image"|"voice"|"file"|"notification"|"custom");

                /**
                 * Creates a new MsgContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns MsgContent instance
                 */
                public static create(properties?: im.common.v1.IMsgContent): im.common.v1.MsgContent;

                /**
                 * Encodes the specified MsgContent message. Does not implicitly {@link im.common.v1.MsgContent.verify|verify} messages.
                 * @param message MsgContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.IMsgContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified MsgContent message, length delimited. Does not implicitly {@link im.common.v1.MsgContent.verify|verify} messages.
                 * @param message MsgContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.IMsgContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a MsgContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns MsgContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.MsgContent;

                /**
                 * Decodes a MsgContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns MsgContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.MsgContent;

                /**
                 * Verifies a MsgContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a MsgContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns MsgContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.MsgContent;

                /**
                 * Creates a plain object from a MsgContent message. Also converts values to other types if specified.
                 * @param message MsgContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.MsgContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this MsgContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for MsgContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a TextContent. */
            interface ITextContent {

                /** TextContent text */
                text?: (string|null);

                /** TextContent atUserIds */
                atUserIds?: ((number|Long)[]|null);
            }

            /** Represents a TextContent. */
            class TextContent implements ITextContent {

                /**
                 * Constructs a new TextContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.ITextContent);

                /** TextContent text. */
                public text: string;

                /** TextContent atUserIds. */
                public atUserIds: (number|Long)[];

                /**
                 * Creates a new TextContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns TextContent instance
                 */
                public static create(properties?: im.common.v1.ITextContent): im.common.v1.TextContent;

                /**
                 * Encodes the specified TextContent message. Does not implicitly {@link im.common.v1.TextContent.verify|verify} messages.
                 * @param message TextContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.ITextContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified TextContent message, length delimited. Does not implicitly {@link im.common.v1.TextContent.verify|verify} messages.
                 * @param message TextContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.ITextContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a TextContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns TextContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.TextContent;

                /**
                 * Decodes a TextContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns TextContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.TextContent;

                /**
                 * Verifies a TextContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a TextContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns TextContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.TextContent;

                /**
                 * Creates a plain object from a TextContent message. Also converts values to other types if specified.
                 * @param message TextContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.TextContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this TextContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for TextContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of an ImageContent. */
            interface IImageContent {

                /** ImageContent objectKey */
                objectKey?: (string|null);

                /** ImageContent thumbKey */
                thumbKey?: (string|null);

                /** ImageContent width */
                width?: (number|null);

                /** ImageContent height */
                height?: (number|null);

                /** ImageContent size */
                size?: (number|Long|null);

                /** ImageContent mime */
                mime?: (string|null);
            }

            /** Represents an ImageContent. */
            class ImageContent implements IImageContent {

                /**
                 * Constructs a new ImageContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.IImageContent);

                /** ImageContent objectKey. */
                public objectKey: string;

                /** ImageContent thumbKey. */
                public thumbKey: string;

                /** ImageContent width. */
                public width: number;

                /** ImageContent height. */
                public height: number;

                /** ImageContent size. */
                public size: (number|Long);

                /** ImageContent mime. */
                public mime: string;

                /**
                 * Creates a new ImageContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns ImageContent instance
                 */
                public static create(properties?: im.common.v1.IImageContent): im.common.v1.ImageContent;

                /**
                 * Encodes the specified ImageContent message. Does not implicitly {@link im.common.v1.ImageContent.verify|verify} messages.
                 * @param message ImageContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.IImageContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified ImageContent message, length delimited. Does not implicitly {@link im.common.v1.ImageContent.verify|verify} messages.
                 * @param message ImageContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.IImageContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes an ImageContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns ImageContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.ImageContent;

                /**
                 * Decodes an ImageContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns ImageContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.ImageContent;

                /**
                 * Verifies an ImageContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates an ImageContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns ImageContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.ImageContent;

                /**
                 * Creates a plain object from an ImageContent message. Also converts values to other types if specified.
                 * @param message ImageContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.ImageContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this ImageContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for ImageContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a VoiceContent. */
            interface IVoiceContent {

                /** VoiceContent objectKey */
                objectKey?: (string|null);

                /** VoiceContent durationMs */
                durationMs?: (number|null);

                /** VoiceContent size */
                size?: (number|Long|null);

                /** VoiceContent codec */
                codec?: (string|null);
            }

            /** Represents a VoiceContent. */
            class VoiceContent implements IVoiceContent {

                /**
                 * Constructs a new VoiceContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.IVoiceContent);

                /** VoiceContent objectKey. */
                public objectKey: string;

                /** VoiceContent durationMs. */
                public durationMs: number;

                /** VoiceContent size. */
                public size: (number|Long);

                /** VoiceContent codec. */
                public codec: string;

                /**
                 * Creates a new VoiceContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns VoiceContent instance
                 */
                public static create(properties?: im.common.v1.IVoiceContent): im.common.v1.VoiceContent;

                /**
                 * Encodes the specified VoiceContent message. Does not implicitly {@link im.common.v1.VoiceContent.verify|verify} messages.
                 * @param message VoiceContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.IVoiceContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified VoiceContent message, length delimited. Does not implicitly {@link im.common.v1.VoiceContent.verify|verify} messages.
                 * @param message VoiceContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.IVoiceContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a VoiceContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns VoiceContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.VoiceContent;

                /**
                 * Decodes a VoiceContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns VoiceContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.VoiceContent;

                /**
                 * Verifies a VoiceContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a VoiceContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns VoiceContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.VoiceContent;

                /**
                 * Creates a plain object from a VoiceContent message. Also converts values to other types if specified.
                 * @param message VoiceContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.VoiceContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this VoiceContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for VoiceContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a FileContent. */
            interface IFileContent {

                /** FileContent objectKey */
                objectKey?: (string|null);

                /** FileContent fileName */
                fileName?: (string|null);

                /** FileContent size */
                size?: (number|Long|null);

                /** FileContent mime */
                mime?: (string|null);

                /** FileContent thumbKey */
                thumbKey?: (string|null);

                /** FileContent durationMs */
                durationMs?: (number|null);
            }

            /** Represents a FileContent. */
            class FileContent implements IFileContent {

                /**
                 * Constructs a new FileContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.IFileContent);

                /** FileContent objectKey. */
                public objectKey: string;

                /** FileContent fileName. */
                public fileName: string;

                /** FileContent size. */
                public size: (number|Long);

                /** FileContent mime. */
                public mime: string;

                /** FileContent thumbKey. */
                public thumbKey: string;

                /** FileContent durationMs. */
                public durationMs: number;

                /**
                 * Creates a new FileContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns FileContent instance
                 */
                public static create(properties?: im.common.v1.IFileContent): im.common.v1.FileContent;

                /**
                 * Encodes the specified FileContent message. Does not implicitly {@link im.common.v1.FileContent.verify|verify} messages.
                 * @param message FileContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.IFileContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified FileContent message, length delimited. Does not implicitly {@link im.common.v1.FileContent.verify|verify} messages.
                 * @param message FileContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.IFileContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a FileContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns FileContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.FileContent;

                /**
                 * Decodes a FileContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns FileContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.FileContent;

                /**
                 * Verifies a FileContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a FileContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns FileContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.FileContent;

                /**
                 * Creates a plain object from a FileContent message. Also converts values to other types if specified.
                 * @param message FileContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.FileContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this FileContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for FileContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a NotificationContent. */
            interface INotificationContent {

                /** NotificationContent eventType */
                eventType?: (string|null);

                /** NotificationContent payload */
                payload?: (string|null);
            }

            /** Represents a NotificationContent. */
            class NotificationContent implements INotificationContent {

                /**
                 * Constructs a new NotificationContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.INotificationContent);

                /** NotificationContent eventType. */
                public eventType: string;

                /** NotificationContent payload. */
                public payload: string;

                /**
                 * Creates a new NotificationContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns NotificationContent instance
                 */
                public static create(properties?: im.common.v1.INotificationContent): im.common.v1.NotificationContent;

                /**
                 * Encodes the specified NotificationContent message. Does not implicitly {@link im.common.v1.NotificationContent.verify|verify} messages.
                 * @param message NotificationContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.INotificationContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified NotificationContent message, length delimited. Does not implicitly {@link im.common.v1.NotificationContent.verify|verify} messages.
                 * @param message NotificationContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.INotificationContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a NotificationContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns NotificationContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.NotificationContent;

                /**
                 * Decodes a NotificationContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns NotificationContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.NotificationContent;

                /**
                 * Verifies a NotificationContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a NotificationContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns NotificationContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.NotificationContent;

                /**
                 * Creates a plain object from a NotificationContent message. Also converts values to other types if specified.
                 * @param message NotificationContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.NotificationContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this NotificationContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for NotificationContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }

            /** Properties of a CustomContent. */
            interface ICustomContent {

                /** CustomContent customType */
                customType?: (string|null);

                /** CustomContent payload */
                payload?: (string|null);
            }

            /** Represents a CustomContent. */
            class CustomContent implements ICustomContent {

                /**
                 * Constructs a new CustomContent.
                 * @param [properties] Properties to set
                 */
                constructor(properties?: im.common.v1.ICustomContent);

                /** CustomContent customType. */
                public customType: string;

                /** CustomContent payload. */
                public payload: string;

                /**
                 * Creates a new CustomContent instance using the specified properties.
                 * @param [properties] Properties to set
                 * @returns CustomContent instance
                 */
                public static create(properties?: im.common.v1.ICustomContent): im.common.v1.CustomContent;

                /**
                 * Encodes the specified CustomContent message. Does not implicitly {@link im.common.v1.CustomContent.verify|verify} messages.
                 * @param message CustomContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encode(message: im.common.v1.ICustomContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Encodes the specified CustomContent message, length delimited. Does not implicitly {@link im.common.v1.CustomContent.verify|verify} messages.
                 * @param message CustomContent message or plain object to encode
                 * @param [writer] Writer to encode to
                 * @returns Writer
                 */
                public static encodeDelimited(message: im.common.v1.ICustomContent, writer?: $protobuf.Writer): $protobuf.Writer;

                /**
                 * Decodes a CustomContent message from the specified reader or buffer.
                 * @param reader Reader or buffer to decode from
                 * @param [length] Message length if known beforehand
                 * @returns CustomContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decode(reader: ($protobuf.Reader|Uint8Array), length?: number): im.common.v1.CustomContent;

                /**
                 * Decodes a CustomContent message from the specified reader or buffer, length delimited.
                 * @param reader Reader or buffer to decode from
                 * @returns CustomContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                public static decodeDelimited(reader: ($protobuf.Reader|Uint8Array)): im.common.v1.CustomContent;

                /**
                 * Verifies a CustomContent message.
                 * @param message Plain object to verify
                 * @returns `null` if valid, otherwise the reason why it is not
                 */
                public static verify(message: { [k: string]: any }): (string|null);

                /**
                 * Creates a CustomContent message from a plain object. Also converts values to their respective internal types.
                 * @param object Plain object
                 * @returns CustomContent
                 */
                public static fromObject(object: { [k: string]: any }): im.common.v1.CustomContent;

                /**
                 * Creates a plain object from a CustomContent message. Also converts values to other types if specified.
                 * @param message CustomContent
                 * @param [options] Conversion options
                 * @returns Plain object
                 */
                public static toObject(message: im.common.v1.CustomContent, options?: $protobuf.IConversionOptions): { [k: string]: any };

                /**
                 * Converts this CustomContent to JSON.
                 * @returns JSON object
                 */
                public toJSON(): { [k: string]: any };

                /**
                 * Gets the default type url for CustomContent
                 * @param [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns The default type url
                 */
                public static getTypeUrl(typeUrlPrefix?: string): string;
            }
        }
    }
}
