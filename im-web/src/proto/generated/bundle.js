/*eslint-disable block-scoped-var, id-length, no-control-regex, no-magic-numbers, no-prototype-builtins, no-redeclare, no-shadow, no-var, sort-vars*/
import * as $protobuf from "protobufjs/minimal";

// Common aliases
const $Reader = $protobuf.Reader, $Writer = $protobuf.Writer, $util = $protobuf.util;

// Exported root namespace
const $root = $protobuf.roots["default"] || ($protobuf.roots["default"] = {});

export const im = $root.im = (() => {

    /**
     * Namespace im.
     * @exports im
     * @namespace
     */
    const im = {};

    im.ws = (function() {

        /**
         * Namespace ws.
         * @memberof im
         * @namespace
         */
        const ws = {};

        ws.v1 = (function() {

            /**
             * Namespace v1.
             * @memberof im.ws
             * @namespace
             */
            const v1 = {};

            /**
             * Cmd enum.
             * @name im.ws.v1.Cmd
             * @enum {number}
             * @property {number} CMD_UNSPECIFIED=0 CMD_UNSPECIFIED value
             * @property {number} AUTH=1 AUTH value
             * @property {number} AUTH_ACK=2 AUTH_ACK value
             * @property {number} PING=3 PING value
             * @property {number} PONG=4 PONG value
             * @property {number} KICK=5 KICK value
             * @property {number} MSG_SEND=10 MSG_SEND value
             * @property {number} MSG_SEND_ACK=11 MSG_SEND_ACK value
             * @property {number} MSG_PUSH=12 MSG_PUSH value
             * @property {number} MSG_RECV_ACK=13 MSG_RECV_ACK value
             * @property {number} SYNC_REQ=20 SYNC_REQ value
             * @property {number} SYNC_RESP=21 SYNC_RESP value
             * @property {number} READ_REPORT=22 READ_REPORT value
             * @property {number} READ_NOTIFY=23 READ_NOTIFY value
             * @property {number} REVOKE_NOTIFY=24 REVOKE_NOTIFY value
             * @property {number} CONV_NOTIFY=25 CONV_NOTIFY value
             * @property {number} ERROR=99 ERROR value
             */
            v1.Cmd = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "CMD_UNSPECIFIED"] = 0;
                values[valuesById[1] = "AUTH"] = 1;
                values[valuesById[2] = "AUTH_ACK"] = 2;
                values[valuesById[3] = "PING"] = 3;
                values[valuesById[4] = "PONG"] = 4;
                values[valuesById[5] = "KICK"] = 5;
                values[valuesById[10] = "MSG_SEND"] = 10;
                values[valuesById[11] = "MSG_SEND_ACK"] = 11;
                values[valuesById[12] = "MSG_PUSH"] = 12;
                values[valuesById[13] = "MSG_RECV_ACK"] = 13;
                values[valuesById[20] = "SYNC_REQ"] = 20;
                values[valuesById[21] = "SYNC_RESP"] = 21;
                values[valuesById[22] = "READ_REPORT"] = 22;
                values[valuesById[23] = "READ_NOTIFY"] = 23;
                values[valuesById[24] = "REVOKE_NOTIFY"] = 24;
                values[valuesById[25] = "CONV_NOTIFY"] = 25;
                values[valuesById[99] = "ERROR"] = 99;
                return values;
            })();

            v1.Frame = (function() {

                /**
                 * Properties of a Frame.
                 * @memberof im.ws.v1
                 * @interface IFrame
                 * @property {number|null} [version] Frame version
                 * @property {number|Long|null} [reqId] Frame reqId
                 * @property {im.ws.v1.Cmd|null} [cmd] Frame cmd
                 * @property {Uint8Array|null} [body] Frame body
                 */

                /**
                 * Constructs a new Frame.
                 * @memberof im.ws.v1
                 * @classdesc Represents a Frame.
                 * @implements IFrame
                 * @constructor
                 * @param {im.ws.v1.IFrame=} [properties] Properties to set
                 */
                function Frame(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * Frame version.
                 * @member {number} version
                 * @memberof im.ws.v1.Frame
                 * @instance
                 */
                Frame.prototype.version = 0;

                /**
                 * Frame reqId.
                 * @member {number|Long} reqId
                 * @memberof im.ws.v1.Frame
                 * @instance
                 */
                Frame.prototype.reqId = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

                /**
                 * Frame cmd.
                 * @member {im.ws.v1.Cmd} cmd
                 * @memberof im.ws.v1.Frame
                 * @instance
                 */
                Frame.prototype.cmd = 0;

                /**
                 * Frame body.
                 * @member {Uint8Array} body
                 * @memberof im.ws.v1.Frame
                 * @instance
                 */
                Frame.prototype.body = $util.newBuffer([]);

                /**
                 * Creates a new Frame instance using the specified properties.
                 * @function create
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {im.ws.v1.IFrame=} [properties] Properties to set
                 * @returns {im.ws.v1.Frame} Frame instance
                 */
                Frame.create = function create(properties) {
                    return new Frame(properties);
                };

                /**
                 * Encodes the specified Frame message. Does not implicitly {@link im.ws.v1.Frame.verify|verify} messages.
                 * @function encode
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {im.ws.v1.IFrame} message Frame message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                Frame.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.version != null && Object.hasOwnProperty.call(message, "version"))
                        writer.uint32(/* id 1, wireType 0 =*/8).uint32(message.version);
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        writer.uint32(/* id 2, wireType 0 =*/16).uint64(message.reqId);
                    if (message.cmd != null && Object.hasOwnProperty.call(message, "cmd"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int32(message.cmd);
                    if (message.body != null && Object.hasOwnProperty.call(message, "body"))
                        writer.uint32(/* id 4, wireType 2 =*/34).bytes(message.body);
                    return writer;
                };

                /**
                 * Encodes the specified Frame message, length delimited. Does not implicitly {@link im.ws.v1.Frame.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {im.ws.v1.IFrame} message Frame message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                Frame.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a Frame message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.ws.v1.Frame} Frame
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                Frame.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.ws.v1.Frame();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.version = reader.uint32();
                                break;
                            }
                        case 2: {
                                message.reqId = reader.uint64();
                                break;
                            }
                        case 3: {
                                message.cmd = reader.int32();
                                break;
                            }
                        case 4: {
                                message.body = reader.bytes();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a Frame message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.ws.v1.Frame} Frame
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                Frame.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a Frame message.
                 * @function verify
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                Frame.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.version != null && Object.hasOwnProperty.call(message, "version"))
                        if (!$util.isInteger(message.version))
                            return "version: integer expected";
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        if (!$util.isInteger(message.reqId) && !(message.reqId && $util.isInteger(message.reqId.low) && $util.isInteger(message.reqId.high)))
                            return "reqId: integer|Long expected";
                    if (message.cmd != null && Object.hasOwnProperty.call(message, "cmd"))
                        switch (message.cmd) {
                        default:
                            return "cmd: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                        case 5:
                        case 10:
                        case 11:
                        case 12:
                        case 13:
                        case 20:
                        case 21:
                        case 22:
                        case 23:
                        case 24:
                        case 25:
                        case 99:
                            break;
                        }
                    if (message.body != null && Object.hasOwnProperty.call(message, "body"))
                        if (!(message.body && typeof message.body.length === "number" || $util.isString(message.body)))
                            return "body: buffer expected";
                    return null;
                };

                /**
                 * Creates a Frame message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.ws.v1.Frame} Frame
                 */
                Frame.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.ws.v1.Frame)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.ws.v1.Frame: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.ws.v1.Frame();
                    if (object.version != null)
                        message.version = object.version >>> 0;
                    if (object.reqId != null)
                        if ($util.Long)
                            message.reqId = $util.Long.fromValue(object.reqId, true);
                        else if (typeof object.reqId === "string")
                            message.reqId = parseInt(object.reqId, 10);
                        else if (typeof object.reqId === "number")
                            message.reqId = object.reqId;
                        else if (typeof object.reqId === "object")
                            message.reqId = new $util.LongBits(object.reqId.low >>> 0, object.reqId.high >>> 0).toNumber(true);
                    switch (object.cmd) {
                    default:
                        if (typeof object.cmd === "number") {
                            message.cmd = object.cmd;
                            break;
                        }
                        break;
                    case "CMD_UNSPECIFIED":
                    case 0:
                        message.cmd = 0;
                        break;
                    case "AUTH":
                    case 1:
                        message.cmd = 1;
                        break;
                    case "AUTH_ACK":
                    case 2:
                        message.cmd = 2;
                        break;
                    case "PING":
                    case 3:
                        message.cmd = 3;
                        break;
                    case "PONG":
                    case 4:
                        message.cmd = 4;
                        break;
                    case "KICK":
                    case 5:
                        message.cmd = 5;
                        break;
                    case "MSG_SEND":
                    case 10:
                        message.cmd = 10;
                        break;
                    case "MSG_SEND_ACK":
                    case 11:
                        message.cmd = 11;
                        break;
                    case "MSG_PUSH":
                    case 12:
                        message.cmd = 12;
                        break;
                    case "MSG_RECV_ACK":
                    case 13:
                        message.cmd = 13;
                        break;
                    case "SYNC_REQ":
                    case 20:
                        message.cmd = 20;
                        break;
                    case "SYNC_RESP":
                    case 21:
                        message.cmd = 21;
                        break;
                    case "READ_REPORT":
                    case 22:
                        message.cmd = 22;
                        break;
                    case "READ_NOTIFY":
                    case 23:
                        message.cmd = 23;
                        break;
                    case "REVOKE_NOTIFY":
                    case 24:
                        message.cmd = 24;
                        break;
                    case "CONV_NOTIFY":
                    case 25:
                        message.cmd = 25;
                        break;
                    case "ERROR":
                    case 99:
                        message.cmd = 99;
                        break;
                    }
                    if (object.body != null)
                        if (typeof object.body === "string")
                            $util.base64.decode(object.body, message.body = $util.newBuffer($util.base64.length(object.body)), 0);
                        else if (object.body.length >= 0)
                            message.body = object.body;
                    return message;
                };

                /**
                 * Creates a plain object from a Frame message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {im.ws.v1.Frame} message Frame
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                Frame.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.version = 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, true);
                            object.reqId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.reqId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.cmd = options.enums === String ? "CMD_UNSPECIFIED" : 0;
                        if (options.bytes === String)
                            object.body = "";
                        else {
                            object.body = [];
                            if (options.bytes !== Array)
                                object.body = $util.newBuffer(object.body);
                        }
                    }
                    if (message.version != null && Object.hasOwnProperty.call(message, "version"))
                        object.version = message.version;
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.reqId = typeof message.reqId === "number" ? BigInt(message.reqId) : $util.Long.fromBits(message.reqId.low >>> 0, message.reqId.high >>> 0, true).toBigInt();
                        else if (typeof message.reqId === "number")
                            object.reqId = options.longs === String ? String(message.reqId) : message.reqId;
                        else
                            object.reqId = options.longs === String ? $util.Long.prototype.toString.call(message.reqId) : options.longs === Number ? new $util.LongBits(message.reqId.low >>> 0, message.reqId.high >>> 0).toNumber(true) : message.reqId;
                    if (message.cmd != null && Object.hasOwnProperty.call(message, "cmd"))
                        object.cmd = options.enums === String ? $root.im.ws.v1.Cmd[message.cmd] === undefined ? message.cmd : $root.im.ws.v1.Cmd[message.cmd] : message.cmd;
                    if (message.body != null && Object.hasOwnProperty.call(message, "body"))
                        object.body = options.bytes === String ? $util.base64.encode(message.body, 0, message.body.length) : options.bytes === Array ? Array.prototype.slice.call(message.body) : message.body;
                    return object;
                };

                /**
                 * Converts this Frame to JSON.
                 * @function toJSON
                 * @memberof im.ws.v1.Frame
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                Frame.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for Frame
                 * @function getTypeUrl
                 * @memberof im.ws.v1.Frame
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                Frame.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.ws.v1.Frame";
                };

                return Frame;
            })();

            v1.AuthReq = (function() {

                /**
                 * Properties of an AuthReq.
                 * @memberof im.ws.v1
                 * @interface IAuthReq
                 * @property {string|null} [token] AuthReq token
                 * @property {number|Long|null} [tenantId] AuthReq tenantId
                 * @property {string|null} [deviceId] AuthReq deviceId
                 * @property {number|null} [platform] AuthReq platform
                 * @property {string|null} [appVersion] AuthReq appVersion
                 * @property {number|Long|null} [timestamp] AuthReq timestamp
                 */

                /**
                 * Constructs a new AuthReq.
                 * @memberof im.ws.v1
                 * @classdesc Represents an AuthReq.
                 * @implements IAuthReq
                 * @constructor
                 * @param {im.ws.v1.IAuthReq=} [properties] Properties to set
                 */
                function AuthReq(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * AuthReq token.
                 * @member {string} token
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.token = "";

                /**
                 * AuthReq tenantId.
                 * @member {number|Long} tenantId
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.tenantId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * AuthReq deviceId.
                 * @member {string} deviceId
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.deviceId = "";

                /**
                 * AuthReq platform.
                 * @member {number} platform
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.platform = 0;

                /**
                 * AuthReq appVersion.
                 * @member {string} appVersion
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.appVersion = "";

                /**
                 * AuthReq timestamp.
                 * @member {number|Long} timestamp
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 */
                AuthReq.prototype.timestamp = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new AuthReq instance using the specified properties.
                 * @function create
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {im.ws.v1.IAuthReq=} [properties] Properties to set
                 * @returns {im.ws.v1.AuthReq} AuthReq instance
                 */
                AuthReq.create = function create(properties) {
                    return new AuthReq(properties);
                };

                /**
                 * Encodes the specified AuthReq message. Does not implicitly {@link im.ws.v1.AuthReq.verify|verify} messages.
                 * @function encode
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {im.ws.v1.IAuthReq} message AuthReq message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                AuthReq.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.token != null && Object.hasOwnProperty.call(message, "token"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.token);
                    if (message.tenantId != null && Object.hasOwnProperty.call(message, "tenantId"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.tenantId);
                    if (message.deviceId != null && Object.hasOwnProperty.call(message, "deviceId"))
                        writer.uint32(/* id 3, wireType 2 =*/26).string(message.deviceId);
                    if (message.platform != null && Object.hasOwnProperty.call(message, "platform"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int32(message.platform);
                    if (message.appVersion != null && Object.hasOwnProperty.call(message, "appVersion"))
                        writer.uint32(/* id 5, wireType 2 =*/42).string(message.appVersion);
                    if (message.timestamp != null && Object.hasOwnProperty.call(message, "timestamp"))
                        writer.uint32(/* id 6, wireType 0 =*/48).int64(message.timestamp);
                    return writer;
                };

                /**
                 * Encodes the specified AuthReq message, length delimited. Does not implicitly {@link im.ws.v1.AuthReq.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {im.ws.v1.IAuthReq} message AuthReq message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                AuthReq.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes an AuthReq message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.ws.v1.AuthReq} AuthReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                AuthReq.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.ws.v1.AuthReq();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.token = reader.string();
                                break;
                            }
                        case 2: {
                                message.tenantId = reader.int64();
                                break;
                            }
                        case 3: {
                                message.deviceId = reader.string();
                                break;
                            }
                        case 4: {
                                message.platform = reader.int32();
                                break;
                            }
                        case 5: {
                                message.appVersion = reader.string();
                                break;
                            }
                        case 6: {
                                message.timestamp = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes an AuthReq message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.ws.v1.AuthReq} AuthReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                AuthReq.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies an AuthReq message.
                 * @function verify
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                AuthReq.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.token != null && Object.hasOwnProperty.call(message, "token"))
                        if (!$util.isString(message.token))
                            return "token: string expected";
                    if (message.tenantId != null && Object.hasOwnProperty.call(message, "tenantId"))
                        if (!$util.isInteger(message.tenantId) && !(message.tenantId && $util.isInteger(message.tenantId.low) && $util.isInteger(message.tenantId.high)))
                            return "tenantId: integer|Long expected";
                    if (message.deviceId != null && Object.hasOwnProperty.call(message, "deviceId"))
                        if (!$util.isString(message.deviceId))
                            return "deviceId: string expected";
                    if (message.platform != null && Object.hasOwnProperty.call(message, "platform"))
                        if (!$util.isInteger(message.platform))
                            return "platform: integer expected";
                    if (message.appVersion != null && Object.hasOwnProperty.call(message, "appVersion"))
                        if (!$util.isString(message.appVersion))
                            return "appVersion: string expected";
                    if (message.timestamp != null && Object.hasOwnProperty.call(message, "timestamp"))
                        if (!$util.isInteger(message.timestamp) && !(message.timestamp && $util.isInteger(message.timestamp.low) && $util.isInteger(message.timestamp.high)))
                            return "timestamp: integer|Long expected";
                    return null;
                };

                /**
                 * Creates an AuthReq message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.ws.v1.AuthReq} AuthReq
                 */
                AuthReq.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.ws.v1.AuthReq)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.ws.v1.AuthReq: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.ws.v1.AuthReq();
                    if (object.token != null)
                        message.token = String(object.token);
                    if (object.tenantId != null)
                        if ($util.Long)
                            message.tenantId = $util.Long.fromValue(object.tenantId, false);
                        else if (typeof object.tenantId === "string")
                            message.tenantId = parseInt(object.tenantId, 10);
                        else if (typeof object.tenantId === "number")
                            message.tenantId = object.tenantId;
                        else if (typeof object.tenantId === "object")
                            message.tenantId = new $util.LongBits(object.tenantId.low >>> 0, object.tenantId.high >>> 0).toNumber();
                    if (object.deviceId != null)
                        message.deviceId = String(object.deviceId);
                    if (object.platform != null)
                        message.platform = object.platform | 0;
                    if (object.appVersion != null)
                        message.appVersion = String(object.appVersion);
                    if (object.timestamp != null)
                        if ($util.Long)
                            message.timestamp = $util.Long.fromValue(object.timestamp, false);
                        else if (typeof object.timestamp === "string")
                            message.timestamp = parseInt(object.timestamp, 10);
                        else if (typeof object.timestamp === "number")
                            message.timestamp = object.timestamp;
                        else if (typeof object.timestamp === "object")
                            message.timestamp = new $util.LongBits(object.timestamp.low >>> 0, object.timestamp.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from an AuthReq message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {im.ws.v1.AuthReq} message AuthReq
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                AuthReq.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.token = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.tenantId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.tenantId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.deviceId = "";
                        object.platform = 0;
                        object.appVersion = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.timestamp = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.timestamp = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.token != null && Object.hasOwnProperty.call(message, "token"))
                        object.token = message.token;
                    if (message.tenantId != null && Object.hasOwnProperty.call(message, "tenantId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.tenantId = typeof message.tenantId === "number" ? BigInt(message.tenantId) : $util.Long.fromBits(message.tenantId.low >>> 0, message.tenantId.high >>> 0, false).toBigInt();
                        else if (typeof message.tenantId === "number")
                            object.tenantId = options.longs === String ? String(message.tenantId) : message.tenantId;
                        else
                            object.tenantId = options.longs === String ? $util.Long.prototype.toString.call(message.tenantId) : options.longs === Number ? new $util.LongBits(message.tenantId.low >>> 0, message.tenantId.high >>> 0).toNumber() : message.tenantId;
                    if (message.deviceId != null && Object.hasOwnProperty.call(message, "deviceId"))
                        object.deviceId = message.deviceId;
                    if (message.platform != null && Object.hasOwnProperty.call(message, "platform"))
                        object.platform = message.platform;
                    if (message.appVersion != null && Object.hasOwnProperty.call(message, "appVersion"))
                        object.appVersion = message.appVersion;
                    if (message.timestamp != null && Object.hasOwnProperty.call(message, "timestamp"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.timestamp = typeof message.timestamp === "number" ? BigInt(message.timestamp) : $util.Long.fromBits(message.timestamp.low >>> 0, message.timestamp.high >>> 0, false).toBigInt();
                        else if (typeof message.timestamp === "number")
                            object.timestamp = options.longs === String ? String(message.timestamp) : message.timestamp;
                        else
                            object.timestamp = options.longs === String ? $util.Long.prototype.toString.call(message.timestamp) : options.longs === Number ? new $util.LongBits(message.timestamp.low >>> 0, message.timestamp.high >>> 0).toNumber() : message.timestamp;
                    return object;
                };

                /**
                 * Converts this AuthReq to JSON.
                 * @function toJSON
                 * @memberof im.ws.v1.AuthReq
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                AuthReq.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for AuthReq
                 * @function getTypeUrl
                 * @memberof im.ws.v1.AuthReq
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                AuthReq.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.ws.v1.AuthReq";
                };

                return AuthReq;
            })();

            v1.AuthResp = (function() {

                /**
                 * Properties of an AuthResp.
                 * @memberof im.ws.v1
                 * @interface IAuthResp
                 * @property {number|null} [code] AuthResp code
                 * @property {string|null} [message] AuthResp message
                 * @property {number|Long|null} [userId] AuthResp userId
                 * @property {number|Long|null} [serverTs] AuthResp serverTs
                 * @property {number|null} [heartbeatIntervalSec] AuthResp heartbeatIntervalSec
                 */

                /**
                 * Constructs a new AuthResp.
                 * @memberof im.ws.v1
                 * @classdesc Represents an AuthResp.
                 * @implements IAuthResp
                 * @constructor
                 * @param {im.ws.v1.IAuthResp=} [properties] Properties to set
                 */
                function AuthResp(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * AuthResp code.
                 * @member {number} code
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 */
                AuthResp.prototype.code = 0;

                /**
                 * AuthResp message.
                 * @member {string} message
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 */
                AuthResp.prototype.message = "";

                /**
                 * AuthResp userId.
                 * @member {number|Long} userId
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 */
                AuthResp.prototype.userId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * AuthResp serverTs.
                 * @member {number|Long} serverTs
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 */
                AuthResp.prototype.serverTs = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * AuthResp heartbeatIntervalSec.
                 * @member {number} heartbeatIntervalSec
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 */
                AuthResp.prototype.heartbeatIntervalSec = 0;

                /**
                 * Creates a new AuthResp instance using the specified properties.
                 * @function create
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {im.ws.v1.IAuthResp=} [properties] Properties to set
                 * @returns {im.ws.v1.AuthResp} AuthResp instance
                 */
                AuthResp.create = function create(properties) {
                    return new AuthResp(properties);
                };

                /**
                 * Encodes the specified AuthResp message. Does not implicitly {@link im.ws.v1.AuthResp.verify|verify} messages.
                 * @function encode
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {im.ws.v1.IAuthResp} message AuthResp message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                AuthResp.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int32(message.code);
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.message);
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.userId);
                    if (message.serverTs != null && Object.hasOwnProperty.call(message, "serverTs"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int64(message.serverTs);
                    if (message.heartbeatIntervalSec != null && Object.hasOwnProperty.call(message, "heartbeatIntervalSec"))
                        writer.uint32(/* id 5, wireType 0 =*/40).uint32(message.heartbeatIntervalSec);
                    return writer;
                };

                /**
                 * Encodes the specified AuthResp message, length delimited. Does not implicitly {@link im.ws.v1.AuthResp.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {im.ws.v1.IAuthResp} message AuthResp message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                AuthResp.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes an AuthResp message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.ws.v1.AuthResp} AuthResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                AuthResp.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.ws.v1.AuthResp();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.code = reader.int32();
                                break;
                            }
                        case 2: {
                                message.message = reader.string();
                                break;
                            }
                        case 3: {
                                message.userId = reader.int64();
                                break;
                            }
                        case 4: {
                                message.serverTs = reader.int64();
                                break;
                            }
                        case 5: {
                                message.heartbeatIntervalSec = reader.uint32();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes an AuthResp message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.ws.v1.AuthResp} AuthResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                AuthResp.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies an AuthResp message.
                 * @function verify
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                AuthResp.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        if (!$util.isInteger(message.code))
                            return "code: integer expected";
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        if (!$util.isString(message.message))
                            return "message: string expected";
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        if (!$util.isInteger(message.userId) && !(message.userId && $util.isInteger(message.userId.low) && $util.isInteger(message.userId.high)))
                            return "userId: integer|Long expected";
                    if (message.serverTs != null && Object.hasOwnProperty.call(message, "serverTs"))
                        if (!$util.isInteger(message.serverTs) && !(message.serverTs && $util.isInteger(message.serverTs.low) && $util.isInteger(message.serverTs.high)))
                            return "serverTs: integer|Long expected";
                    if (message.heartbeatIntervalSec != null && Object.hasOwnProperty.call(message, "heartbeatIntervalSec"))
                        if (!$util.isInteger(message.heartbeatIntervalSec))
                            return "heartbeatIntervalSec: integer expected";
                    return null;
                };

                /**
                 * Creates an AuthResp message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.ws.v1.AuthResp} AuthResp
                 */
                AuthResp.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.ws.v1.AuthResp)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.ws.v1.AuthResp: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.ws.v1.AuthResp();
                    if (object.code != null)
                        message.code = object.code | 0;
                    if (object.message != null)
                        message.message = String(object.message);
                    if (object.userId != null)
                        if ($util.Long)
                            message.userId = $util.Long.fromValue(object.userId, false);
                        else if (typeof object.userId === "string")
                            message.userId = parseInt(object.userId, 10);
                        else if (typeof object.userId === "number")
                            message.userId = object.userId;
                        else if (typeof object.userId === "object")
                            message.userId = new $util.LongBits(object.userId.low >>> 0, object.userId.high >>> 0).toNumber();
                    if (object.serverTs != null)
                        if ($util.Long)
                            message.serverTs = $util.Long.fromValue(object.serverTs, false);
                        else if (typeof object.serverTs === "string")
                            message.serverTs = parseInt(object.serverTs, 10);
                        else if (typeof object.serverTs === "number")
                            message.serverTs = object.serverTs;
                        else if (typeof object.serverTs === "object")
                            message.serverTs = new $util.LongBits(object.serverTs.low >>> 0, object.serverTs.high >>> 0).toNumber();
                    if (object.heartbeatIntervalSec != null)
                        message.heartbeatIntervalSec = object.heartbeatIntervalSec >>> 0;
                    return message;
                };

                /**
                 * Creates a plain object from an AuthResp message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {im.ws.v1.AuthResp} message AuthResp
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                AuthResp.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.code = 0;
                        object.message = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.userId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.userId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.serverTs = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.serverTs = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.heartbeatIntervalSec = 0;
                    }
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        object.code = message.code;
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        object.message = message.message;
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.userId = typeof message.userId === "number" ? BigInt(message.userId) : $util.Long.fromBits(message.userId.low >>> 0, message.userId.high >>> 0, false).toBigInt();
                        else if (typeof message.userId === "number")
                            object.userId = options.longs === String ? String(message.userId) : message.userId;
                        else
                            object.userId = options.longs === String ? $util.Long.prototype.toString.call(message.userId) : options.longs === Number ? new $util.LongBits(message.userId.low >>> 0, message.userId.high >>> 0).toNumber() : message.userId;
                    if (message.serverTs != null && Object.hasOwnProperty.call(message, "serverTs"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.serverTs = typeof message.serverTs === "number" ? BigInt(message.serverTs) : $util.Long.fromBits(message.serverTs.low >>> 0, message.serverTs.high >>> 0, false).toBigInt();
                        else if (typeof message.serverTs === "number")
                            object.serverTs = options.longs === String ? String(message.serverTs) : message.serverTs;
                        else
                            object.serverTs = options.longs === String ? $util.Long.prototype.toString.call(message.serverTs) : options.longs === Number ? new $util.LongBits(message.serverTs.low >>> 0, message.serverTs.high >>> 0).toNumber() : message.serverTs;
                    if (message.heartbeatIntervalSec != null && Object.hasOwnProperty.call(message, "heartbeatIntervalSec"))
                        object.heartbeatIntervalSec = message.heartbeatIntervalSec;
                    return object;
                };

                /**
                 * Converts this AuthResp to JSON.
                 * @function toJSON
                 * @memberof im.ws.v1.AuthResp
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                AuthResp.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for AuthResp
                 * @function getTypeUrl
                 * @memberof im.ws.v1.AuthResp
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                AuthResp.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.ws.v1.AuthResp";
                };

                return AuthResp;
            })();

            v1.KickNotify = (function() {

                /**
                 * Properties of a KickNotify.
                 * @memberof im.ws.v1
                 * @interface IKickNotify
                 * @property {im.ws.v1.KickNotify.Reason|null} [reason] KickNotify reason
                 * @property {string|null} [message] KickNotify message
                 */

                /**
                 * Constructs a new KickNotify.
                 * @memberof im.ws.v1
                 * @classdesc Represents a KickNotify.
                 * @implements IKickNotify
                 * @constructor
                 * @param {im.ws.v1.IKickNotify=} [properties] Properties to set
                 */
                function KickNotify(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * KickNotify reason.
                 * @member {im.ws.v1.KickNotify.Reason} reason
                 * @memberof im.ws.v1.KickNotify
                 * @instance
                 */
                KickNotify.prototype.reason = 0;

                /**
                 * KickNotify message.
                 * @member {string} message
                 * @memberof im.ws.v1.KickNotify
                 * @instance
                 */
                KickNotify.prototype.message = "";

                /**
                 * Creates a new KickNotify instance using the specified properties.
                 * @function create
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {im.ws.v1.IKickNotify=} [properties] Properties to set
                 * @returns {im.ws.v1.KickNotify} KickNotify instance
                 */
                KickNotify.create = function create(properties) {
                    return new KickNotify(properties);
                };

                /**
                 * Encodes the specified KickNotify message. Does not implicitly {@link im.ws.v1.KickNotify.verify|verify} messages.
                 * @function encode
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {im.ws.v1.IKickNotify} message KickNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                KickNotify.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int32(message.reason);
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.message);
                    return writer;
                };

                /**
                 * Encodes the specified KickNotify message, length delimited. Does not implicitly {@link im.ws.v1.KickNotify.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {im.ws.v1.IKickNotify} message KickNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                KickNotify.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a KickNotify message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.ws.v1.KickNotify} KickNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                KickNotify.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.ws.v1.KickNotify();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.reason = reader.int32();
                                break;
                            }
                        case 2: {
                                message.message = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a KickNotify message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.ws.v1.KickNotify} KickNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                KickNotify.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a KickNotify message.
                 * @function verify
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                KickNotify.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        switch (message.reason) {
                        default:
                            return "reason: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            break;
                        }
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        if (!$util.isString(message.message))
                            return "message: string expected";
                    return null;
                };

                /**
                 * Creates a KickNotify message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.ws.v1.KickNotify} KickNotify
                 */
                KickNotify.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.ws.v1.KickNotify)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.ws.v1.KickNotify: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.ws.v1.KickNotify();
                    switch (object.reason) {
                    default:
                        if (typeof object.reason === "number") {
                            message.reason = object.reason;
                            break;
                        }
                        break;
                    case "REASON_UNSPECIFIED":
                    case 0:
                        message.reason = 0;
                        break;
                    case "NEW_DEVICE_LOGIN":
                    case 1:
                        message.reason = 1;
                        break;
                    case "TOKEN_EXPIRED":
                    case 2:
                        message.reason = 2;
                        break;
                    case "ADMIN_OFFLINE":
                    case 3:
                        message.reason = 3;
                        break;
                    case "PROTO_TOO_OLD":
                    case 4:
                        message.reason = 4;
                        break;
                    }
                    if (object.message != null)
                        message.message = String(object.message);
                    return message;
                };

                /**
                 * Creates a plain object from a KickNotify message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {im.ws.v1.KickNotify} message KickNotify
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                KickNotify.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.reason = options.enums === String ? "REASON_UNSPECIFIED" : 0;
                        object.message = "";
                    }
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        object.reason = options.enums === String ? $root.im.ws.v1.KickNotify.Reason[message.reason] === undefined ? message.reason : $root.im.ws.v1.KickNotify.Reason[message.reason] : message.reason;
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        object.message = message.message;
                    return object;
                };

                /**
                 * Converts this KickNotify to JSON.
                 * @function toJSON
                 * @memberof im.ws.v1.KickNotify
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                KickNotify.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for KickNotify
                 * @function getTypeUrl
                 * @memberof im.ws.v1.KickNotify
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                KickNotify.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.ws.v1.KickNotify";
                };

                /**
                 * Reason enum.
                 * @name im.ws.v1.KickNotify.Reason
                 * @enum {number}
                 * @property {number} REASON_UNSPECIFIED=0 REASON_UNSPECIFIED value
                 * @property {number} NEW_DEVICE_LOGIN=1 NEW_DEVICE_LOGIN value
                 * @property {number} TOKEN_EXPIRED=2 TOKEN_EXPIRED value
                 * @property {number} ADMIN_OFFLINE=3 ADMIN_OFFLINE value
                 * @property {number} PROTO_TOO_OLD=4 PROTO_TOO_OLD value
                 */
                KickNotify.Reason = (function() {
                    const valuesById = {}, values = Object.create(valuesById);
                    values[valuesById[0] = "REASON_UNSPECIFIED"] = 0;
                    values[valuesById[1] = "NEW_DEVICE_LOGIN"] = 1;
                    values[valuesById[2] = "TOKEN_EXPIRED"] = 2;
                    values[valuesById[3] = "ADMIN_OFFLINE"] = 3;
                    values[valuesById[4] = "PROTO_TOO_OLD"] = 4;
                    return values;
                })();

                return KickNotify;
            })();

            return v1;
        })();

        return ws;
    })();

    im.body = (function() {

        /**
         * Namespace body.
         * @memberof im
         * @namespace
         */
        const body = {};

        body.v1 = (function() {

            /**
             * Namespace v1.
             * @memberof im.body
             * @namespace
             */
            const v1 = {};

            v1.MsgSend = (function() {

                /**
                 * Properties of a MsgSend.
                 * @memberof im.body.v1
                 * @interface IMsgSend
                 * @property {string|null} [clientMsgId] MsgSend clientMsgId
                 * @property {number|Long|null} [toUserId] MsgSend toUserId
                 * @property {number|Long|null} [groupId] MsgSend groupId
                 * @property {number|Long|null} [convId] MsgSend convId
                 * @property {im.common.v1.IMsgContent|null} [content] MsgSend content
                 * @property {Object.<string,string>|null} [ext] MsgSend ext
                 */

                /**
                 * Constructs a new MsgSend.
                 * @memberof im.body.v1
                 * @classdesc Represents a MsgSend.
                 * @implements IMsgSend
                 * @constructor
                 * @param {im.body.v1.IMsgSend=} [properties] Properties to set
                 */
                function MsgSend(properties) {
                    this.ext = {};
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * MsgSend clientMsgId.
                 * @member {string} clientMsgId
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.clientMsgId = "";

                /**
                 * MsgSend toUserId.
                 * @member {number|Long|null|undefined} toUserId
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.toUserId = null;

                /**
                 * MsgSend groupId.
                 * @member {number|Long|null|undefined} groupId
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.groupId = null;

                /**
                 * MsgSend convId.
                 * @member {number|Long|null|undefined} convId
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.convId = null;

                /**
                 * MsgSend content.
                 * @member {im.common.v1.IMsgContent|null|undefined} content
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.content = null;

                /**
                 * MsgSend ext.
                 * @member {Object.<string,string>} ext
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                MsgSend.prototype.ext = $util.emptyObject;

                // OneOf field names bound to virtual getters and setters
                let $oneOfFields;

                /**
                 * MsgSend target.
                 * @member {"toUserId"|"groupId"|"convId"|undefined} target
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 */
                Object.defineProperty(MsgSend.prototype, "target", {
                    get: $util.oneOfGetter($oneOfFields = ["toUserId", "groupId", "convId"]),
                    set: $util.oneOfSetter($oneOfFields)
                });

                /**
                 * Creates a new MsgSend instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {im.body.v1.IMsgSend=} [properties] Properties to set
                 * @returns {im.body.v1.MsgSend} MsgSend instance
                 */
                MsgSend.create = function create(properties) {
                    return new MsgSend(properties);
                };

                /**
                 * Encodes the specified MsgSend message. Does not implicitly {@link im.body.v1.MsgSend.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {im.body.v1.IMsgSend} message MsgSend message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgSend.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.clientMsgId);
                    if (message.toUserId != null && Object.hasOwnProperty.call(message, "toUserId"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.toUserId);
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.groupId);
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int64(message.convId);
                    if (message.content != null && Object.hasOwnProperty.call(message, "content"))
                        $root.im.common.v1.MsgContent.encode(message.content, writer.uint32(/* id 5, wireType 2 =*/42).fork(), q + 1).ldelim();
                    if (message.ext != null && Object.hasOwnProperty.call(message, "ext"))
                        for (let keys = Object.keys(message.ext), i = 0; i < keys.length; ++i)
                            writer.uint32(/* id 6, wireType 2 =*/50).fork().uint32(/* id 1, wireType 2 =*/10).string(keys[i]).uint32(/* id 2, wireType 2 =*/18).string(message.ext[keys[i]]).ldelim();
                    return writer;
                };

                /**
                 * Encodes the specified MsgSend message, length delimited. Does not implicitly {@link im.body.v1.MsgSend.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {im.body.v1.IMsgSend} message MsgSend message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgSend.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a MsgSend message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.MsgSend} MsgSend
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgSend.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.MsgSend(), key, value;
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.clientMsgId = reader.string();
                                break;
                            }
                        case 2: {
                                message.toUserId = reader.int64();
                                break;
                            }
                        case 3: {
                                message.groupId = reader.int64();
                                break;
                            }
                        case 4: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 5: {
                                message.content = $root.im.common.v1.MsgContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 6: {
                                if (message.ext === $util.emptyObject)
                                    message.ext = {};
                                let end2 = reader.uint32() + reader.pos;
                                key = "";
                                value = "";
                                while (reader.pos < end2) {
                                    let tag2 = reader.uint32();
                                    switch (tag2 >>> 3) {
                                    case 1:
                                        key = reader.string();
                                        break;
                                    case 2:
                                        value = reader.string();
                                        break;
                                    default:
                                        reader.skipType(tag2 & 7, long);
                                        break;
                                    }
                                }
                                if (key === "__proto__")
                                    $util.makeProp(message.ext, key);
                                message.ext[key] = value;
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a MsgSend message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.MsgSend} MsgSend
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgSend.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a MsgSend message.
                 * @function verify
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                MsgSend.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    let properties = {};
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        if (!$util.isString(message.clientMsgId))
                            return "clientMsgId: string expected";
                    if (message.toUserId != null && Object.hasOwnProperty.call(message, "toUserId")) {
                        properties.target = 1;
                        if (!$util.isInteger(message.toUserId) && !(message.toUserId && $util.isInteger(message.toUserId.low) && $util.isInteger(message.toUserId.high)))
                            return "toUserId: integer|Long expected";
                    }
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId")) {
                        if (properties.target === 1)
                            return "target: multiple values";
                        properties.target = 1;
                        if (!$util.isInteger(message.groupId) && !(message.groupId && $util.isInteger(message.groupId.low) && $util.isInteger(message.groupId.high)))
                            return "groupId: integer|Long expected";
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId")) {
                        if (properties.target === 1)
                            return "target: multiple values";
                        properties.target = 1;
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    }
                    if (message.content != null && Object.hasOwnProperty.call(message, "content")) {
                        let error = $root.im.common.v1.MsgContent.verify(message.content, long + 1);
                        if (error)
                            return "content." + error;
                    }
                    if (message.ext != null && Object.hasOwnProperty.call(message, "ext")) {
                        if (!$util.isObject(message.ext))
                            return "ext: object expected";
                        let key = Object.keys(message.ext);
                        for (let i = 0; i < key.length; ++i)
                            if (!$util.isString(message.ext[key[i]]))
                                return "ext: string{k:string} expected";
                    }
                    return null;
                };

                /**
                 * Creates a MsgSend message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.MsgSend} MsgSend
                 */
                MsgSend.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.MsgSend)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.MsgSend: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.MsgSend();
                    if (object.clientMsgId != null)
                        message.clientMsgId = String(object.clientMsgId);
                    if (object.toUserId != null)
                        if ($util.Long)
                            message.toUserId = $util.Long.fromValue(object.toUserId, false);
                        else if (typeof object.toUserId === "string")
                            message.toUserId = parseInt(object.toUserId, 10);
                        else if (typeof object.toUserId === "number")
                            message.toUserId = object.toUserId;
                        else if (typeof object.toUserId === "object")
                            message.toUserId = new $util.LongBits(object.toUserId.low >>> 0, object.toUserId.high >>> 0).toNumber();
                    if (object.groupId != null)
                        if ($util.Long)
                            message.groupId = $util.Long.fromValue(object.groupId, false);
                        else if (typeof object.groupId === "string")
                            message.groupId = parseInt(object.groupId, 10);
                        else if (typeof object.groupId === "number")
                            message.groupId = object.groupId;
                        else if (typeof object.groupId === "object")
                            message.groupId = new $util.LongBits(object.groupId.low >>> 0, object.groupId.high >>> 0).toNumber();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    if (object.content != null) {
                        if (!$util.isObject(object.content))
                            throw TypeError(".im.body.v1.MsgSend.content: object expected");
                        message.content = $root.im.common.v1.MsgContent.fromObject(object.content, long + 1);
                    }
                    if (object.ext) {
                        if (!$util.isObject(object.ext))
                            throw TypeError(".im.body.v1.MsgSend.ext: object expected");
                        message.ext = {};
                        for (let keys = Object.keys(object.ext), i = 0; i < keys.length; ++i) {
                            if (keys[i] === "__proto__")
                                $util.makeProp(message.ext, keys[i]);
                            message.ext[keys[i]] = String(object.ext[keys[i]]);
                        }
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a MsgSend message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {im.body.v1.MsgSend} message MsgSend
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                MsgSend.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.objects || options.defaults)
                        object.ext = {};
                    if (options.defaults) {
                        object.clientMsgId = "";
                        object.content = null;
                    }
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        object.clientMsgId = message.clientMsgId;
                    if (message.toUserId != null && Object.hasOwnProperty.call(message, "toUserId")) {
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.toUserId = typeof message.toUserId === "number" ? BigInt(message.toUserId) : $util.Long.fromBits(message.toUserId.low >>> 0, message.toUserId.high >>> 0, false).toBigInt();
                        else if (typeof message.toUserId === "number")
                            object.toUserId = options.longs === String ? String(message.toUserId) : message.toUserId;
                        else
                            object.toUserId = options.longs === String ? $util.Long.prototype.toString.call(message.toUserId) : options.longs === Number ? new $util.LongBits(message.toUserId.low >>> 0, message.toUserId.high >>> 0).toNumber() : message.toUserId;
                        if (options.oneofs)
                            object.target = "toUserId";
                    }
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId")) {
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.groupId = typeof message.groupId === "number" ? BigInt(message.groupId) : $util.Long.fromBits(message.groupId.low >>> 0, message.groupId.high >>> 0, false).toBigInt();
                        else if (typeof message.groupId === "number")
                            object.groupId = options.longs === String ? String(message.groupId) : message.groupId;
                        else
                            object.groupId = options.longs === String ? $util.Long.prototype.toString.call(message.groupId) : options.longs === Number ? new $util.LongBits(message.groupId.low >>> 0, message.groupId.high >>> 0).toNumber() : message.groupId;
                        if (options.oneofs)
                            object.target = "groupId";
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId")) {
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                        if (options.oneofs)
                            object.target = "convId";
                    }
                    if (message.content != null && Object.hasOwnProperty.call(message, "content"))
                        object.content = $root.im.common.v1.MsgContent.toObject(message.content, options, q + 1);
                    let keys2;
                    if (message.ext && (keys2 = Object.keys(message.ext)).length) {
                        object.ext = {};
                        for (let j = 0; j < keys2.length; ++j) {
                            if (keys2[j] === "__proto__")
                                $util.makeProp(object.ext, keys2[j]);
                            object.ext[keys2[j]] = message.ext[keys2[j]];
                        }
                    }
                    return object;
                };

                /**
                 * Converts this MsgSend to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.MsgSend
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                MsgSend.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for MsgSend
                 * @function getTypeUrl
                 * @memberof im.body.v1.MsgSend
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                MsgSend.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.MsgSend";
                };

                return MsgSend;
            })();

            v1.MsgSendAck = (function() {

                /**
                 * Properties of a MsgSendAck.
                 * @memberof im.body.v1
                 * @interface IMsgSendAck
                 * @property {number|null} [code] MsgSendAck code
                 * @property {string|null} [clientMsgId] MsgSendAck clientMsgId
                 * @property {number|Long|null} [serverMsgId] MsgSendAck serverMsgId
                 * @property {number|Long|null} [convId] MsgSendAck convId
                 * @property {number|Long|null} [seq] MsgSendAck seq
                 * @property {number|Long|null} [serverTime] MsgSendAck serverTime
                 */

                /**
                 * Constructs a new MsgSendAck.
                 * @memberof im.body.v1
                 * @classdesc Represents a MsgSendAck.
                 * @implements IMsgSendAck
                 * @constructor
                 * @param {im.body.v1.IMsgSendAck=} [properties] Properties to set
                 */
                function MsgSendAck(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * MsgSendAck code.
                 * @member {number} code
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.code = 0;

                /**
                 * MsgSendAck clientMsgId.
                 * @member {string} clientMsgId
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.clientMsgId = "";

                /**
                 * MsgSendAck serverMsgId.
                 * @member {number|Long} serverMsgId
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.serverMsgId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgSendAck convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgSendAck seq.
                 * @member {number|Long} seq
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.seq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgSendAck serverTime.
                 * @member {number|Long} serverTime
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 */
                MsgSendAck.prototype.serverTime = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new MsgSendAck instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {im.body.v1.IMsgSendAck=} [properties] Properties to set
                 * @returns {im.body.v1.MsgSendAck} MsgSendAck instance
                 */
                MsgSendAck.create = function create(properties) {
                    return new MsgSendAck(properties);
                };

                /**
                 * Encodes the specified MsgSendAck message. Does not implicitly {@link im.body.v1.MsgSendAck.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {im.body.v1.IMsgSendAck} message MsgSendAck message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgSendAck.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int32(message.code);
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.clientMsgId);
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.serverMsgId);
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int64(message.convId);
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        writer.uint32(/* id 5, wireType 0 =*/40).int64(message.seq);
                    if (message.serverTime != null && Object.hasOwnProperty.call(message, "serverTime"))
                        writer.uint32(/* id 6, wireType 0 =*/48).int64(message.serverTime);
                    return writer;
                };

                /**
                 * Encodes the specified MsgSendAck message, length delimited. Does not implicitly {@link im.body.v1.MsgSendAck.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {im.body.v1.IMsgSendAck} message MsgSendAck message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgSendAck.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a MsgSendAck message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.MsgSendAck} MsgSendAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgSendAck.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.MsgSendAck();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.code = reader.int32();
                                break;
                            }
                        case 2: {
                                message.clientMsgId = reader.string();
                                break;
                            }
                        case 3: {
                                message.serverMsgId = reader.int64();
                                break;
                            }
                        case 4: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 5: {
                                message.seq = reader.int64();
                                break;
                            }
                        case 6: {
                                message.serverTime = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a MsgSendAck message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.MsgSendAck} MsgSendAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgSendAck.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a MsgSendAck message.
                 * @function verify
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                MsgSendAck.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        if (!$util.isInteger(message.code))
                            return "code: integer expected";
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        if (!$util.isString(message.clientMsgId))
                            return "clientMsgId: string expected";
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (!$util.isInteger(message.serverMsgId) && !(message.serverMsgId && $util.isInteger(message.serverMsgId.low) && $util.isInteger(message.serverMsgId.high)))
                            return "serverMsgId: integer|Long expected";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (!$util.isInteger(message.seq) && !(message.seq && $util.isInteger(message.seq.low) && $util.isInteger(message.seq.high)))
                            return "seq: integer|Long expected";
                    if (message.serverTime != null && Object.hasOwnProperty.call(message, "serverTime"))
                        if (!$util.isInteger(message.serverTime) && !(message.serverTime && $util.isInteger(message.serverTime.low) && $util.isInteger(message.serverTime.high)))
                            return "serverTime: integer|Long expected";
                    return null;
                };

                /**
                 * Creates a MsgSendAck message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.MsgSendAck} MsgSendAck
                 */
                MsgSendAck.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.MsgSendAck)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.MsgSendAck: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.MsgSendAck();
                    if (object.code != null)
                        message.code = object.code | 0;
                    if (object.clientMsgId != null)
                        message.clientMsgId = String(object.clientMsgId);
                    if (object.serverMsgId != null)
                        if ($util.Long)
                            message.serverMsgId = $util.Long.fromValue(object.serverMsgId, false);
                        else if (typeof object.serverMsgId === "string")
                            message.serverMsgId = parseInt(object.serverMsgId, 10);
                        else if (typeof object.serverMsgId === "number")
                            message.serverMsgId = object.serverMsgId;
                        else if (typeof object.serverMsgId === "object")
                            message.serverMsgId = new $util.LongBits(object.serverMsgId.low >>> 0, object.serverMsgId.high >>> 0).toNumber();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    if (object.seq != null)
                        if ($util.Long)
                            message.seq = $util.Long.fromValue(object.seq, false);
                        else if (typeof object.seq === "string")
                            message.seq = parseInt(object.seq, 10);
                        else if (typeof object.seq === "number")
                            message.seq = object.seq;
                        else if (typeof object.seq === "object")
                            message.seq = new $util.LongBits(object.seq.low >>> 0, object.seq.high >>> 0).toNumber();
                    if (object.serverTime != null)
                        if ($util.Long)
                            message.serverTime = $util.Long.fromValue(object.serverTime, false);
                        else if (typeof object.serverTime === "string")
                            message.serverTime = parseInt(object.serverTime, 10);
                        else if (typeof object.serverTime === "number")
                            message.serverTime = object.serverTime;
                        else if (typeof object.serverTime === "object")
                            message.serverTime = new $util.LongBits(object.serverTime.low >>> 0, object.serverTime.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from a MsgSendAck message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {im.body.v1.MsgSendAck} message MsgSendAck
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                MsgSendAck.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.code = 0;
                        object.clientMsgId = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.serverMsgId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.serverMsgId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.seq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.seq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.serverTime = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.serverTime = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        object.code = message.code;
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        object.clientMsgId = message.clientMsgId;
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.serverMsgId = typeof message.serverMsgId === "number" ? BigInt(message.serverMsgId) : $util.Long.fromBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0, false).toBigInt();
                        else if (typeof message.serverMsgId === "number")
                            object.serverMsgId = options.longs === String ? String(message.serverMsgId) : message.serverMsgId;
                        else
                            object.serverMsgId = options.longs === String ? $util.Long.prototype.toString.call(message.serverMsgId) : options.longs === Number ? new $util.LongBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0).toNumber() : message.serverMsgId;
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.seq = typeof message.seq === "number" ? BigInt(message.seq) : $util.Long.fromBits(message.seq.low >>> 0, message.seq.high >>> 0, false).toBigInt();
                        else if (typeof message.seq === "number")
                            object.seq = options.longs === String ? String(message.seq) : message.seq;
                        else
                            object.seq = options.longs === String ? $util.Long.prototype.toString.call(message.seq) : options.longs === Number ? new $util.LongBits(message.seq.low >>> 0, message.seq.high >>> 0).toNumber() : message.seq;
                    if (message.serverTime != null && Object.hasOwnProperty.call(message, "serverTime"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.serverTime = typeof message.serverTime === "number" ? BigInt(message.serverTime) : $util.Long.fromBits(message.serverTime.low >>> 0, message.serverTime.high >>> 0, false).toBigInt();
                        else if (typeof message.serverTime === "number")
                            object.serverTime = options.longs === String ? String(message.serverTime) : message.serverTime;
                        else
                            object.serverTime = options.longs === String ? $util.Long.prototype.toString.call(message.serverTime) : options.longs === Number ? new $util.LongBits(message.serverTime.low >>> 0, message.serverTime.high >>> 0).toNumber() : message.serverTime;
                    return object;
                };

                /**
                 * Converts this MsgSendAck to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.MsgSendAck
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                MsgSendAck.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for MsgSendAck
                 * @function getTypeUrl
                 * @memberof im.body.v1.MsgSendAck
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                MsgSendAck.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.MsgSendAck";
                };

                return MsgSendAck;
            })();

            v1.MsgPush = (function() {

                /**
                 * Properties of a MsgPush.
                 * @memberof im.body.v1
                 * @interface IMsgPush
                 * @property {number|Long|null} [convId] MsgPush convId
                 * @property {im.common.v1.ConvType|null} [convType] MsgPush convType
                 * @property {number|Long|null} [seq] MsgPush seq
                 * @property {number|Long|null} [serverMsgId] MsgPush serverMsgId
                 * @property {string|null} [clientMsgId] MsgPush clientMsgId
                 * @property {im.body.v1.ISender|null} [sender] MsgPush sender
                 * @property {number|Long|null} [sendTime] MsgPush sendTime
                 * @property {im.common.v1.IMsgContent|null} [content] MsgPush content
                 * @property {Object.<string,string>|null} [ext] MsgPush ext
                 */

                /**
                 * Constructs a new MsgPush.
                 * @memberof im.body.v1
                 * @classdesc Represents a MsgPush.
                 * @implements IMsgPush
                 * @constructor
                 * @param {im.body.v1.IMsgPush=} [properties] Properties to set
                 */
                function MsgPush(properties) {
                    this.ext = {};
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * MsgPush convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgPush convType.
                 * @member {im.common.v1.ConvType} convType
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.convType = 0;

                /**
                 * MsgPush seq.
                 * @member {number|Long} seq
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.seq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgPush serverMsgId.
                 * @member {number|Long} serverMsgId
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.serverMsgId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgPush clientMsgId.
                 * @member {string} clientMsgId
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.clientMsgId = "";

                /**
                 * MsgPush sender.
                 * @member {im.body.v1.ISender|null|undefined} sender
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.sender = null;

                /**
                 * MsgPush sendTime.
                 * @member {number|Long} sendTime
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.sendTime = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * MsgPush content.
                 * @member {im.common.v1.IMsgContent|null|undefined} content
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.content = null;

                /**
                 * MsgPush ext.
                 * @member {Object.<string,string>} ext
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 */
                MsgPush.prototype.ext = $util.emptyObject;

                /**
                 * Creates a new MsgPush instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {im.body.v1.IMsgPush=} [properties] Properties to set
                 * @returns {im.body.v1.MsgPush} MsgPush instance
                 */
                MsgPush.create = function create(properties) {
                    return new MsgPush(properties);
                };

                /**
                 * Encodes the specified MsgPush message. Does not implicitly {@link im.body.v1.MsgPush.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {im.body.v1.IMsgPush} message MsgPush message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgPush.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                    if (message.convType != null && Object.hasOwnProperty.call(message, "convType"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int32(message.convType);
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.seq);
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int64(message.serverMsgId);
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        writer.uint32(/* id 5, wireType 2 =*/42).string(message.clientMsgId);
                    if (message.sender != null && Object.hasOwnProperty.call(message, "sender"))
                        $root.im.body.v1.Sender.encode(message.sender, writer.uint32(/* id 6, wireType 2 =*/50).fork(), q + 1).ldelim();
                    if (message.sendTime != null && Object.hasOwnProperty.call(message, "sendTime"))
                        writer.uint32(/* id 7, wireType 0 =*/56).int64(message.sendTime);
                    if (message.content != null && Object.hasOwnProperty.call(message, "content"))
                        $root.im.common.v1.MsgContent.encode(message.content, writer.uint32(/* id 8, wireType 2 =*/66).fork(), q + 1).ldelim();
                    if (message.ext != null && Object.hasOwnProperty.call(message, "ext"))
                        for (let keys = Object.keys(message.ext), i = 0; i < keys.length; ++i)
                            writer.uint32(/* id 9, wireType 2 =*/74).fork().uint32(/* id 1, wireType 2 =*/10).string(keys[i]).uint32(/* id 2, wireType 2 =*/18).string(message.ext[keys[i]]).ldelim();
                    return writer;
                };

                /**
                 * Encodes the specified MsgPush message, length delimited. Does not implicitly {@link im.body.v1.MsgPush.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {im.body.v1.IMsgPush} message MsgPush message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgPush.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a MsgPush message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.MsgPush} MsgPush
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgPush.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.MsgPush(), key, value;
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.convType = reader.int32();
                                break;
                            }
                        case 3: {
                                message.seq = reader.int64();
                                break;
                            }
                        case 4: {
                                message.serverMsgId = reader.int64();
                                break;
                            }
                        case 5: {
                                message.clientMsgId = reader.string();
                                break;
                            }
                        case 6: {
                                message.sender = $root.im.body.v1.Sender.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 7: {
                                message.sendTime = reader.int64();
                                break;
                            }
                        case 8: {
                                message.content = $root.im.common.v1.MsgContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 9: {
                                if (message.ext === $util.emptyObject)
                                    message.ext = {};
                                let end2 = reader.uint32() + reader.pos;
                                key = "";
                                value = "";
                                while (reader.pos < end2) {
                                    let tag2 = reader.uint32();
                                    switch (tag2 >>> 3) {
                                    case 1:
                                        key = reader.string();
                                        break;
                                    case 2:
                                        value = reader.string();
                                        break;
                                    default:
                                        reader.skipType(tag2 & 7, long);
                                        break;
                                    }
                                }
                                if (key === "__proto__")
                                    $util.makeProp(message.ext, key);
                                message.ext[key] = value;
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a MsgPush message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.MsgPush} MsgPush
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgPush.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a MsgPush message.
                 * @function verify
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                MsgPush.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.convType != null && Object.hasOwnProperty.call(message, "convType"))
                        switch (message.convType) {
                        default:
                            return "convType: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            break;
                        }
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (!$util.isInteger(message.seq) && !(message.seq && $util.isInteger(message.seq.low) && $util.isInteger(message.seq.high)))
                            return "seq: integer|Long expected";
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (!$util.isInteger(message.serverMsgId) && !(message.serverMsgId && $util.isInteger(message.serverMsgId.low) && $util.isInteger(message.serverMsgId.high)))
                            return "serverMsgId: integer|Long expected";
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        if (!$util.isString(message.clientMsgId))
                            return "clientMsgId: string expected";
                    if (message.sender != null && Object.hasOwnProperty.call(message, "sender")) {
                        let error = $root.im.body.v1.Sender.verify(message.sender, long + 1);
                        if (error)
                            return "sender." + error;
                    }
                    if (message.sendTime != null && Object.hasOwnProperty.call(message, "sendTime"))
                        if (!$util.isInteger(message.sendTime) && !(message.sendTime && $util.isInteger(message.sendTime.low) && $util.isInteger(message.sendTime.high)))
                            return "sendTime: integer|Long expected";
                    if (message.content != null && Object.hasOwnProperty.call(message, "content")) {
                        let error = $root.im.common.v1.MsgContent.verify(message.content, long + 1);
                        if (error)
                            return "content." + error;
                    }
                    if (message.ext != null && Object.hasOwnProperty.call(message, "ext")) {
                        if (!$util.isObject(message.ext))
                            return "ext: object expected";
                        let key = Object.keys(message.ext);
                        for (let i = 0; i < key.length; ++i)
                            if (!$util.isString(message.ext[key[i]]))
                                return "ext: string{k:string} expected";
                    }
                    return null;
                };

                /**
                 * Creates a MsgPush message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.MsgPush} MsgPush
                 */
                MsgPush.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.MsgPush)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.MsgPush: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.MsgPush();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    switch (object.convType) {
                    default:
                        if (typeof object.convType === "number") {
                            message.convType = object.convType;
                            break;
                        }
                        break;
                    case "CONV_TYPE_UNSPECIFIED":
                    case 0:
                        message.convType = 0;
                        break;
                    case "C2C":
                    case 1:
                        message.convType = 1;
                        break;
                    case "GROUP":
                    case 2:
                        message.convType = 2;
                        break;
                    case "CS_SESSION":
                    case 3:
                        message.convType = 3;
                        break;
                    case "SYSTEM":
                    case 4:
                        message.convType = 4;
                        break;
                    }
                    if (object.seq != null)
                        if ($util.Long)
                            message.seq = $util.Long.fromValue(object.seq, false);
                        else if (typeof object.seq === "string")
                            message.seq = parseInt(object.seq, 10);
                        else if (typeof object.seq === "number")
                            message.seq = object.seq;
                        else if (typeof object.seq === "object")
                            message.seq = new $util.LongBits(object.seq.low >>> 0, object.seq.high >>> 0).toNumber();
                    if (object.serverMsgId != null)
                        if ($util.Long)
                            message.serverMsgId = $util.Long.fromValue(object.serverMsgId, false);
                        else if (typeof object.serverMsgId === "string")
                            message.serverMsgId = parseInt(object.serverMsgId, 10);
                        else if (typeof object.serverMsgId === "number")
                            message.serverMsgId = object.serverMsgId;
                        else if (typeof object.serverMsgId === "object")
                            message.serverMsgId = new $util.LongBits(object.serverMsgId.low >>> 0, object.serverMsgId.high >>> 0).toNumber();
                    if (object.clientMsgId != null)
                        message.clientMsgId = String(object.clientMsgId);
                    if (object.sender != null) {
                        if (!$util.isObject(object.sender))
                            throw TypeError(".im.body.v1.MsgPush.sender: object expected");
                        message.sender = $root.im.body.v1.Sender.fromObject(object.sender, long + 1);
                    }
                    if (object.sendTime != null)
                        if ($util.Long)
                            message.sendTime = $util.Long.fromValue(object.sendTime, false);
                        else if (typeof object.sendTime === "string")
                            message.sendTime = parseInt(object.sendTime, 10);
                        else if (typeof object.sendTime === "number")
                            message.sendTime = object.sendTime;
                        else if (typeof object.sendTime === "object")
                            message.sendTime = new $util.LongBits(object.sendTime.low >>> 0, object.sendTime.high >>> 0).toNumber();
                    if (object.content != null) {
                        if (!$util.isObject(object.content))
                            throw TypeError(".im.body.v1.MsgPush.content: object expected");
                        message.content = $root.im.common.v1.MsgContent.fromObject(object.content, long + 1);
                    }
                    if (object.ext) {
                        if (!$util.isObject(object.ext))
                            throw TypeError(".im.body.v1.MsgPush.ext: object expected");
                        message.ext = {};
                        for (let keys = Object.keys(object.ext), i = 0; i < keys.length; ++i) {
                            if (keys[i] === "__proto__")
                                $util.makeProp(message.ext, keys[i]);
                            message.ext[keys[i]] = String(object.ext[keys[i]]);
                        }
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a MsgPush message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {im.body.v1.MsgPush} message MsgPush
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                MsgPush.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.objects || options.defaults)
                        object.ext = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.convType = options.enums === String ? "CONV_TYPE_UNSPECIFIED" : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.seq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.seq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.serverMsgId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.serverMsgId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.clientMsgId = "";
                        object.sender = null;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.sendTime = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.sendTime = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.content = null;
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.convType != null && Object.hasOwnProperty.call(message, "convType"))
                        object.convType = options.enums === String ? $root.im.common.v1.ConvType[message.convType] === undefined ? message.convType : $root.im.common.v1.ConvType[message.convType] : message.convType;
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.seq = typeof message.seq === "number" ? BigInt(message.seq) : $util.Long.fromBits(message.seq.low >>> 0, message.seq.high >>> 0, false).toBigInt();
                        else if (typeof message.seq === "number")
                            object.seq = options.longs === String ? String(message.seq) : message.seq;
                        else
                            object.seq = options.longs === String ? $util.Long.prototype.toString.call(message.seq) : options.longs === Number ? new $util.LongBits(message.seq.low >>> 0, message.seq.high >>> 0).toNumber() : message.seq;
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.serverMsgId = typeof message.serverMsgId === "number" ? BigInt(message.serverMsgId) : $util.Long.fromBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0, false).toBigInt();
                        else if (typeof message.serverMsgId === "number")
                            object.serverMsgId = options.longs === String ? String(message.serverMsgId) : message.serverMsgId;
                        else
                            object.serverMsgId = options.longs === String ? $util.Long.prototype.toString.call(message.serverMsgId) : options.longs === Number ? new $util.LongBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0).toNumber() : message.serverMsgId;
                    if (message.clientMsgId != null && Object.hasOwnProperty.call(message, "clientMsgId"))
                        object.clientMsgId = message.clientMsgId;
                    if (message.sender != null && Object.hasOwnProperty.call(message, "sender"))
                        object.sender = $root.im.body.v1.Sender.toObject(message.sender, options, q + 1);
                    if (message.sendTime != null && Object.hasOwnProperty.call(message, "sendTime"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.sendTime = typeof message.sendTime === "number" ? BigInt(message.sendTime) : $util.Long.fromBits(message.sendTime.low >>> 0, message.sendTime.high >>> 0, false).toBigInt();
                        else if (typeof message.sendTime === "number")
                            object.sendTime = options.longs === String ? String(message.sendTime) : message.sendTime;
                        else
                            object.sendTime = options.longs === String ? $util.Long.prototype.toString.call(message.sendTime) : options.longs === Number ? new $util.LongBits(message.sendTime.low >>> 0, message.sendTime.high >>> 0).toNumber() : message.sendTime;
                    if (message.content != null && Object.hasOwnProperty.call(message, "content"))
                        object.content = $root.im.common.v1.MsgContent.toObject(message.content, options, q + 1);
                    let keys2;
                    if (message.ext && (keys2 = Object.keys(message.ext)).length) {
                        object.ext = {};
                        for (let j = 0; j < keys2.length; ++j) {
                            if (keys2[j] === "__proto__")
                                $util.makeProp(object.ext, keys2[j]);
                            object.ext[keys2[j]] = message.ext[keys2[j]];
                        }
                    }
                    return object;
                };

                /**
                 * Converts this MsgPush to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.MsgPush
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                MsgPush.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for MsgPush
                 * @function getTypeUrl
                 * @memberof im.body.v1.MsgPush
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                MsgPush.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.MsgPush";
                };

                return MsgPush;
            })();

            v1.Sender = (function() {

                /**
                 * Properties of a Sender.
                 * @memberof im.body.v1
                 * @interface ISender
                 * @property {number|Long|null} [userId] Sender userId
                 * @property {string|null} [nickname] Sender nickname
                 * @property {string|null} [avatar] Sender avatar
                 * @property {im.common.v1.VerifiedType|null} [verifiedType] Sender verifiedType
                 * @property {im.common.v1.UserType|null} [userType] Sender userType
                 */

                /**
                 * Constructs a new Sender.
                 * @memberof im.body.v1
                 * @classdesc Represents a Sender.
                 * @implements ISender
                 * @constructor
                 * @param {im.body.v1.ISender=} [properties] Properties to set
                 */
                function Sender(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * Sender userId.
                 * @member {number|Long} userId
                 * @memberof im.body.v1.Sender
                 * @instance
                 */
                Sender.prototype.userId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Sender nickname.
                 * @member {string} nickname
                 * @memberof im.body.v1.Sender
                 * @instance
                 */
                Sender.prototype.nickname = "";

                /**
                 * Sender avatar.
                 * @member {string} avatar
                 * @memberof im.body.v1.Sender
                 * @instance
                 */
                Sender.prototype.avatar = "";

                /**
                 * Sender verifiedType.
                 * @member {im.common.v1.VerifiedType} verifiedType
                 * @memberof im.body.v1.Sender
                 * @instance
                 */
                Sender.prototype.verifiedType = 0;

                /**
                 * Sender userType.
                 * @member {im.common.v1.UserType} userType
                 * @memberof im.body.v1.Sender
                 * @instance
                 */
                Sender.prototype.userType = 0;

                /**
                 * Creates a new Sender instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {im.body.v1.ISender=} [properties] Properties to set
                 * @returns {im.body.v1.Sender} Sender instance
                 */
                Sender.create = function create(properties) {
                    return new Sender(properties);
                };

                /**
                 * Encodes the specified Sender message. Does not implicitly {@link im.body.v1.Sender.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {im.body.v1.ISender} message Sender message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                Sender.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.userId);
                    if (message.nickname != null && Object.hasOwnProperty.call(message, "nickname"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.nickname);
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        writer.uint32(/* id 3, wireType 2 =*/26).string(message.avatar);
                    if (message.verifiedType != null && Object.hasOwnProperty.call(message, "verifiedType"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int32(message.verifiedType);
                    if (message.userType != null && Object.hasOwnProperty.call(message, "userType"))
                        writer.uint32(/* id 5, wireType 0 =*/40).int32(message.userType);
                    return writer;
                };

                /**
                 * Encodes the specified Sender message, length delimited. Does not implicitly {@link im.body.v1.Sender.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {im.body.v1.ISender} message Sender message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                Sender.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a Sender message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.Sender} Sender
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                Sender.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.Sender();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.userId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.nickname = reader.string();
                                break;
                            }
                        case 3: {
                                message.avatar = reader.string();
                                break;
                            }
                        case 4: {
                                message.verifiedType = reader.int32();
                                break;
                            }
                        case 5: {
                                message.userType = reader.int32();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a Sender message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.Sender} Sender
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                Sender.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a Sender message.
                 * @function verify
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                Sender.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        if (!$util.isInteger(message.userId) && !(message.userId && $util.isInteger(message.userId.low) && $util.isInteger(message.userId.high)))
                            return "userId: integer|Long expected";
                    if (message.nickname != null && Object.hasOwnProperty.call(message, "nickname"))
                        if (!$util.isString(message.nickname))
                            return "nickname: string expected";
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        if (!$util.isString(message.avatar))
                            return "avatar: string expected";
                    if (message.verifiedType != null && Object.hasOwnProperty.call(message, "verifiedType"))
                        switch (message.verifiedType) {
                        default:
                            return "verifiedType: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                            break;
                        }
                    if (message.userType != null && Object.hasOwnProperty.call(message, "userType"))
                        switch (message.userType) {
                        default:
                            return "userType: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                            break;
                        }
                    return null;
                };

                /**
                 * Creates a Sender message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.Sender} Sender
                 */
                Sender.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.Sender)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.Sender: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.Sender();
                    if (object.userId != null)
                        if ($util.Long)
                            message.userId = $util.Long.fromValue(object.userId, false);
                        else if (typeof object.userId === "string")
                            message.userId = parseInt(object.userId, 10);
                        else if (typeof object.userId === "number")
                            message.userId = object.userId;
                        else if (typeof object.userId === "object")
                            message.userId = new $util.LongBits(object.userId.low >>> 0, object.userId.high >>> 0).toNumber();
                    if (object.nickname != null)
                        message.nickname = String(object.nickname);
                    if (object.avatar != null)
                        message.avatar = String(object.avatar);
                    switch (object.verifiedType) {
                    default:
                        if (typeof object.verifiedType === "number") {
                            message.verifiedType = object.verifiedType;
                            break;
                        }
                        break;
                    case "VERIFIED_NONE":
                    case 0:
                        message.verifiedType = 0;
                        break;
                    case "PERSONAL":
                    case 1:
                        message.verifiedType = 1;
                        break;
                    case "ENTERPRISE":
                    case 2:
                        message.verifiedType = 2;
                        break;
                    case "OFFICIAL_STAFF":
                    case 3:
                        message.verifiedType = 3;
                        break;
                    }
                    switch (object.userType) {
                    default:
                        if (typeof object.userType === "number") {
                            message.userType = object.userType;
                            break;
                        }
                        break;
                    case "USER_TYPE_UNSPECIFIED":
                    case 0:
                        message.userType = 0;
                        break;
                    case "MEMBER":
                    case 1:
                        message.userType = 1;
                        break;
                    case "AGENT":
                    case 2:
                        message.userType = 2;
                        break;
                    case "VISITOR":
                    case 3:
                        message.userType = 3;
                        break;
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a Sender message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {im.body.v1.Sender} message Sender
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                Sender.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.userId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.userId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.nickname = "";
                        object.avatar = "";
                        object.verifiedType = options.enums === String ? "VERIFIED_NONE" : 0;
                        object.userType = options.enums === String ? "USER_TYPE_UNSPECIFIED" : 0;
                    }
                    if (message.userId != null && Object.hasOwnProperty.call(message, "userId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.userId = typeof message.userId === "number" ? BigInt(message.userId) : $util.Long.fromBits(message.userId.low >>> 0, message.userId.high >>> 0, false).toBigInt();
                        else if (typeof message.userId === "number")
                            object.userId = options.longs === String ? String(message.userId) : message.userId;
                        else
                            object.userId = options.longs === String ? $util.Long.prototype.toString.call(message.userId) : options.longs === Number ? new $util.LongBits(message.userId.low >>> 0, message.userId.high >>> 0).toNumber() : message.userId;
                    if (message.nickname != null && Object.hasOwnProperty.call(message, "nickname"))
                        object.nickname = message.nickname;
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        object.avatar = message.avatar;
                    if (message.verifiedType != null && Object.hasOwnProperty.call(message, "verifiedType"))
                        object.verifiedType = options.enums === String ? $root.im.common.v1.VerifiedType[message.verifiedType] === undefined ? message.verifiedType : $root.im.common.v1.VerifiedType[message.verifiedType] : message.verifiedType;
                    if (message.userType != null && Object.hasOwnProperty.call(message, "userType"))
                        object.userType = options.enums === String ? $root.im.common.v1.UserType[message.userType] === undefined ? message.userType : $root.im.common.v1.UserType[message.userType] : message.userType;
                    return object;
                };

                /**
                 * Converts this Sender to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.Sender
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                Sender.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for Sender
                 * @function getTypeUrl
                 * @memberof im.body.v1.Sender
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                Sender.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.Sender";
                };

                return Sender;
            })();

            v1.MsgRecvAck = (function() {

                /**
                 * Properties of a MsgRecvAck.
                 * @memberof im.body.v1
                 * @interface IMsgRecvAck
                 * @property {Array.<im.body.v1.MsgRecvAck.IAckItem>|null} [items] MsgRecvAck items
                 */

                /**
                 * Constructs a new MsgRecvAck.
                 * @memberof im.body.v1
                 * @classdesc Represents a MsgRecvAck.
                 * @implements IMsgRecvAck
                 * @constructor
                 * @param {im.body.v1.IMsgRecvAck=} [properties] Properties to set
                 */
                function MsgRecvAck(properties) {
                    this.items = [];
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * MsgRecvAck items.
                 * @member {Array.<im.body.v1.MsgRecvAck.IAckItem>} items
                 * @memberof im.body.v1.MsgRecvAck
                 * @instance
                 */
                MsgRecvAck.prototype.items = $util.emptyArray;

                /**
                 * Creates a new MsgRecvAck instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {im.body.v1.IMsgRecvAck=} [properties] Properties to set
                 * @returns {im.body.v1.MsgRecvAck} MsgRecvAck instance
                 */
                MsgRecvAck.create = function create(properties) {
                    return new MsgRecvAck(properties);
                };

                /**
                 * Encodes the specified MsgRecvAck message. Does not implicitly {@link im.body.v1.MsgRecvAck.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {im.body.v1.IMsgRecvAck} message MsgRecvAck message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgRecvAck.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.items != null && message.items.length)
                        for (let i = 0; i < message.items.length; ++i)
                            $root.im.body.v1.MsgRecvAck.AckItem.encode(message.items[i], writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                    return writer;
                };

                /**
                 * Encodes the specified MsgRecvAck message, length delimited. Does not implicitly {@link im.body.v1.MsgRecvAck.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {im.body.v1.IMsgRecvAck} message MsgRecvAck message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgRecvAck.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a MsgRecvAck message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.MsgRecvAck} MsgRecvAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgRecvAck.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.MsgRecvAck();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                if (!(message.items && message.items.length))
                                    message.items = [];
                                message.items.push($root.im.body.v1.MsgRecvAck.AckItem.decode(reader, reader.uint32(), undefined, long + 1));
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a MsgRecvAck message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.MsgRecvAck} MsgRecvAck
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgRecvAck.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a MsgRecvAck message.
                 * @function verify
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                MsgRecvAck.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.items != null && Object.hasOwnProperty.call(message, "items")) {
                        if (!Array.isArray(message.items))
                            return "items: array expected";
                        for (let i = 0; i < message.items.length; ++i) {
                            let error = $root.im.body.v1.MsgRecvAck.AckItem.verify(message.items[i], long + 1);
                            if (error)
                                return "items." + error;
                        }
                    }
                    return null;
                };

                /**
                 * Creates a MsgRecvAck message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.MsgRecvAck} MsgRecvAck
                 */
                MsgRecvAck.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.MsgRecvAck)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.MsgRecvAck: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.MsgRecvAck();
                    if (object.items) {
                        if (!Array.isArray(object.items))
                            throw TypeError(".im.body.v1.MsgRecvAck.items: array expected");
                        message.items = [];
                        for (let i = 0; i < object.items.length; ++i) {
                            if (!$util.isObject(object.items[i]))
                                throw TypeError(".im.body.v1.MsgRecvAck.items: object expected");
                            message.items[i] = $root.im.body.v1.MsgRecvAck.AckItem.fromObject(object.items[i], long + 1);
                        }
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a MsgRecvAck message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {im.body.v1.MsgRecvAck} message MsgRecvAck
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                MsgRecvAck.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.arrays || options.defaults)
                        object.items = [];
                    if (message.items && message.items.length) {
                        object.items = [];
                        for (let j = 0; j < message.items.length; ++j)
                            object.items[j] = $root.im.body.v1.MsgRecvAck.AckItem.toObject(message.items[j], options, q + 1);
                    }
                    return object;
                };

                /**
                 * Converts this MsgRecvAck to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.MsgRecvAck
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                MsgRecvAck.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for MsgRecvAck
                 * @function getTypeUrl
                 * @memberof im.body.v1.MsgRecvAck
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                MsgRecvAck.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.MsgRecvAck";
                };

                MsgRecvAck.AckItem = (function() {

                    /**
                     * Properties of an AckItem.
                     * @memberof im.body.v1.MsgRecvAck
                     * @interface IAckItem
                     * @property {number|Long|null} [convId] AckItem convId
                     * @property {number|Long|null} [seq] AckItem seq
                     */

                    /**
                     * Constructs a new AckItem.
                     * @memberof im.body.v1.MsgRecvAck
                     * @classdesc Represents an AckItem.
                     * @implements IAckItem
                     * @constructor
                     * @param {im.body.v1.MsgRecvAck.IAckItem=} [properties] Properties to set
                     */
                    function AckItem(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * AckItem convId.
                     * @member {number|Long} convId
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @instance
                     */
                    AckItem.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * AckItem seq.
                     * @member {number|Long} seq
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @instance
                     */
                    AckItem.prototype.seq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new AckItem instance using the specified properties.
                     * @function create
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {im.body.v1.MsgRecvAck.IAckItem=} [properties] Properties to set
                     * @returns {im.body.v1.MsgRecvAck.AckItem} AckItem instance
                     */
                    AckItem.create = function create(properties) {
                        return new AckItem(properties);
                    };

                    /**
                     * Encodes the specified AckItem message. Does not implicitly {@link im.body.v1.MsgRecvAck.AckItem.verify|verify} messages.
                     * @function encode
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {im.body.v1.MsgRecvAck.IAckItem} message AckItem message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    AckItem.encode = function encode(message, writer, q) {
                        if (!writer)
                            writer = $Writer.create();
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                        if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.seq);
                        return writer;
                    };

                    /**
                     * Encodes the specified AckItem message, length delimited. Does not implicitly {@link im.body.v1.MsgRecvAck.AckItem.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {im.body.v1.MsgRecvAck.IAckItem} message AckItem message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    AckItem.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                    };

                    /**
                     * Decodes an AckItem message from the specified reader or buffer.
                     * @function decode
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {im.body.v1.MsgRecvAck.AckItem} AckItem
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    AckItem.decode = function decode(reader, length, error, long) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (long === undefined)
                            long = 0;
                        if (long > $Reader.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.MsgRecvAck.AckItem();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.convId = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.seq = reader.int64();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7, long);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes an AckItem message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {im.body.v1.MsgRecvAck.AckItem} AckItem
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    AckItem.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies an AckItem message.
                     * @function verify
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    AckItem.verify = function verify(message, long) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            return "maximum nesting depth exceeded";
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                                return "convId: integer|Long expected";
                        if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                            if (!$util.isInteger(message.seq) && !(message.seq && $util.isInteger(message.seq.low) && $util.isInteger(message.seq.high)))
                                return "seq: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates an AckItem message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {im.body.v1.MsgRecvAck.AckItem} AckItem
                     */
                    AckItem.fromObject = function fromObject(object, long) {
                        if (object instanceof $root.im.body.v1.MsgRecvAck.AckItem)
                            return object;
                        if (!$util.isObject(object))
                            throw TypeError(".im.body.v1.MsgRecvAck.AckItem: object expected");
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let message = new $root.im.body.v1.MsgRecvAck.AckItem();
                        if (object.convId != null)
                            if ($util.Long)
                                message.convId = $util.Long.fromValue(object.convId, false);
                            else if (typeof object.convId === "string")
                                message.convId = parseInt(object.convId, 10);
                            else if (typeof object.convId === "number")
                                message.convId = object.convId;
                            else if (typeof object.convId === "object")
                                message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                        if (object.seq != null)
                            if ($util.Long)
                                message.seq = $util.Long.fromValue(object.seq, false);
                            else if (typeof object.seq === "string")
                                message.seq = parseInt(object.seq, 10);
                            else if (typeof object.seq === "number")
                                message.seq = object.seq;
                            else if (typeof object.seq === "object")
                                message.seq = new $util.LongBits(object.seq.low >>> 0, object.seq.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from an AckItem message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {im.body.v1.MsgRecvAck.AckItem} message AckItem
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    AckItem.toObject = function toObject(message, options, q) {
                        if (!options)
                            options = {};
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                            } else
                                object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.seq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                            } else
                                object.seq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        }
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                            else if (typeof message.convId === "number")
                                object.convId = options.longs === String ? String(message.convId) : message.convId;
                            else
                                object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                        if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.seq = typeof message.seq === "number" ? BigInt(message.seq) : $util.Long.fromBits(message.seq.low >>> 0, message.seq.high >>> 0, false).toBigInt();
                            else if (typeof message.seq === "number")
                                object.seq = options.longs === String ? String(message.seq) : message.seq;
                            else
                                object.seq = options.longs === String ? $util.Long.prototype.toString.call(message.seq) : options.longs === Number ? new $util.LongBits(message.seq.low >>> 0, message.seq.high >>> 0).toNumber() : message.seq;
                        return object;
                    };

                    /**
                     * Converts this AckItem to JSON.
                     * @function toJSON
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    AckItem.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for AckItem
                     * @function getTypeUrl
                     * @memberof im.body.v1.MsgRecvAck.AckItem
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    AckItem.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/im.body.v1.MsgRecvAck.AckItem";
                    };

                    return AckItem;
                })();

                return MsgRecvAck;
            })();

            v1.SyncReq = (function() {

                /**
                 * Properties of a SyncReq.
                 * @memberof im.body.v1
                 * @interface ISyncReq
                 * @property {Array.<im.body.v1.SyncReq.IConvVersion>|null} [convVersions] SyncReq convVersions
                 * @property {number|Long|null} [convListVersion] SyncReq convListVersion
                 */

                /**
                 * Constructs a new SyncReq.
                 * @memberof im.body.v1
                 * @classdesc Represents a SyncReq.
                 * @implements ISyncReq
                 * @constructor
                 * @param {im.body.v1.ISyncReq=} [properties] Properties to set
                 */
                function SyncReq(properties) {
                    this.convVersions = [];
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * SyncReq convVersions.
                 * @member {Array.<im.body.v1.SyncReq.IConvVersion>} convVersions
                 * @memberof im.body.v1.SyncReq
                 * @instance
                 */
                SyncReq.prototype.convVersions = $util.emptyArray;

                /**
                 * SyncReq convListVersion.
                 * @member {number|Long} convListVersion
                 * @memberof im.body.v1.SyncReq
                 * @instance
                 */
                SyncReq.prototype.convListVersion = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new SyncReq instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {im.body.v1.ISyncReq=} [properties] Properties to set
                 * @returns {im.body.v1.SyncReq} SyncReq instance
                 */
                SyncReq.create = function create(properties) {
                    return new SyncReq(properties);
                };

                /**
                 * Encodes the specified SyncReq message. Does not implicitly {@link im.body.v1.SyncReq.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {im.body.v1.ISyncReq} message SyncReq message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                SyncReq.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convVersions != null && message.convVersions.length)
                        for (let i = 0; i < message.convVersions.length; ++i)
                            $root.im.body.v1.SyncReq.ConvVersion.encode(message.convVersions[i], writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.convListVersion);
                    return writer;
                };

                /**
                 * Encodes the specified SyncReq message, length delimited. Does not implicitly {@link im.body.v1.SyncReq.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {im.body.v1.ISyncReq} message SyncReq message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                SyncReq.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a SyncReq message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.SyncReq} SyncReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                SyncReq.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.SyncReq();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                if (!(message.convVersions && message.convVersions.length))
                                    message.convVersions = [];
                                message.convVersions.push($root.im.body.v1.SyncReq.ConvVersion.decode(reader, reader.uint32(), undefined, long + 1));
                                break;
                            }
                        case 2: {
                                message.convListVersion = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a SyncReq message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.SyncReq} SyncReq
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                SyncReq.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a SyncReq message.
                 * @function verify
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                SyncReq.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convVersions != null && Object.hasOwnProperty.call(message, "convVersions")) {
                        if (!Array.isArray(message.convVersions))
                            return "convVersions: array expected";
                        for (let i = 0; i < message.convVersions.length; ++i) {
                            let error = $root.im.body.v1.SyncReq.ConvVersion.verify(message.convVersions[i], long + 1);
                            if (error)
                                return "convVersions." + error;
                        }
                    }
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        if (!$util.isInteger(message.convListVersion) && !(message.convListVersion && $util.isInteger(message.convListVersion.low) && $util.isInteger(message.convListVersion.high)))
                            return "convListVersion: integer|Long expected";
                    return null;
                };

                /**
                 * Creates a SyncReq message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.SyncReq} SyncReq
                 */
                SyncReq.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.SyncReq)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.SyncReq: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.SyncReq();
                    if (object.convVersions) {
                        if (!Array.isArray(object.convVersions))
                            throw TypeError(".im.body.v1.SyncReq.convVersions: array expected");
                        message.convVersions = [];
                        for (let i = 0; i < object.convVersions.length; ++i) {
                            if (!$util.isObject(object.convVersions[i]))
                                throw TypeError(".im.body.v1.SyncReq.convVersions: object expected");
                            message.convVersions[i] = $root.im.body.v1.SyncReq.ConvVersion.fromObject(object.convVersions[i], long + 1);
                        }
                    }
                    if (object.convListVersion != null)
                        if ($util.Long)
                            message.convListVersion = $util.Long.fromValue(object.convListVersion, false);
                        else if (typeof object.convListVersion === "string")
                            message.convListVersion = parseInt(object.convListVersion, 10);
                        else if (typeof object.convListVersion === "number")
                            message.convListVersion = object.convListVersion;
                        else if (typeof object.convListVersion === "object")
                            message.convListVersion = new $util.LongBits(object.convListVersion.low >>> 0, object.convListVersion.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from a SyncReq message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {im.body.v1.SyncReq} message SyncReq
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                SyncReq.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.arrays || options.defaults)
                        object.convVersions = [];
                    if (options.defaults)
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convListVersion = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convListVersion = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    if (message.convVersions && message.convVersions.length) {
                        object.convVersions = [];
                        for (let j = 0; j < message.convVersions.length; ++j)
                            object.convVersions[j] = $root.im.body.v1.SyncReq.ConvVersion.toObject(message.convVersions[j], options, q + 1);
                    }
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convListVersion = typeof message.convListVersion === "number" ? BigInt(message.convListVersion) : $util.Long.fromBits(message.convListVersion.low >>> 0, message.convListVersion.high >>> 0, false).toBigInt();
                        else if (typeof message.convListVersion === "number")
                            object.convListVersion = options.longs === String ? String(message.convListVersion) : message.convListVersion;
                        else
                            object.convListVersion = options.longs === String ? $util.Long.prototype.toString.call(message.convListVersion) : options.longs === Number ? new $util.LongBits(message.convListVersion.low >>> 0, message.convListVersion.high >>> 0).toNumber() : message.convListVersion;
                    return object;
                };

                /**
                 * Converts this SyncReq to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.SyncReq
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                SyncReq.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for SyncReq
                 * @function getTypeUrl
                 * @memberof im.body.v1.SyncReq
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                SyncReq.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.SyncReq";
                };

                SyncReq.ConvVersion = (function() {

                    /**
                     * Properties of a ConvVersion.
                     * @memberof im.body.v1.SyncReq
                     * @interface IConvVersion
                     * @property {number|Long|null} [convId] ConvVersion convId
                     * @property {number|Long|null} [localMaxSeq] ConvVersion localMaxSeq
                     */

                    /**
                     * Constructs a new ConvVersion.
                     * @memberof im.body.v1.SyncReq
                     * @classdesc Represents a ConvVersion.
                     * @implements IConvVersion
                     * @constructor
                     * @param {im.body.v1.SyncReq.IConvVersion=} [properties] Properties to set
                     */
                    function ConvVersion(properties) {
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * ConvVersion convId.
                     * @member {number|Long} convId
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @instance
                     */
                    ConvVersion.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * ConvVersion localMaxSeq.
                     * @member {number|Long} localMaxSeq
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @instance
                     */
                    ConvVersion.prototype.localMaxSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * Creates a new ConvVersion instance using the specified properties.
                     * @function create
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {im.body.v1.SyncReq.IConvVersion=} [properties] Properties to set
                     * @returns {im.body.v1.SyncReq.ConvVersion} ConvVersion instance
                     */
                    ConvVersion.create = function create(properties) {
                        return new ConvVersion(properties);
                    };

                    /**
                     * Encodes the specified ConvVersion message. Does not implicitly {@link im.body.v1.SyncReq.ConvVersion.verify|verify} messages.
                     * @function encode
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {im.body.v1.SyncReq.IConvVersion} message ConvVersion message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    ConvVersion.encode = function encode(message, writer, q) {
                        if (!writer)
                            writer = $Writer.create();
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                        if (message.localMaxSeq != null && Object.hasOwnProperty.call(message, "localMaxSeq"))
                            writer.uint32(/* id 2, wireType 0 =*/16).int64(message.localMaxSeq);
                        return writer;
                    };

                    /**
                     * Encodes the specified ConvVersion message, length delimited. Does not implicitly {@link im.body.v1.SyncReq.ConvVersion.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {im.body.v1.SyncReq.IConvVersion} message ConvVersion message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    ConvVersion.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                    };

                    /**
                     * Decodes a ConvVersion message from the specified reader or buffer.
                     * @function decode
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {im.body.v1.SyncReq.ConvVersion} ConvVersion
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    ConvVersion.decode = function decode(reader, length, error, long) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (long === undefined)
                            long = 0;
                        if (long > $Reader.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.SyncReq.ConvVersion();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.convId = reader.int64();
                                    break;
                                }
                            case 2: {
                                    message.localMaxSeq = reader.int64();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7, long);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a ConvVersion message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {im.body.v1.SyncReq.ConvVersion} ConvVersion
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    ConvVersion.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a ConvVersion message.
                     * @function verify
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    ConvVersion.verify = function verify(message, long) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            return "maximum nesting depth exceeded";
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                                return "convId: integer|Long expected";
                        if (message.localMaxSeq != null && Object.hasOwnProperty.call(message, "localMaxSeq"))
                            if (!$util.isInteger(message.localMaxSeq) && !(message.localMaxSeq && $util.isInteger(message.localMaxSeq.low) && $util.isInteger(message.localMaxSeq.high)))
                                return "localMaxSeq: integer|Long expected";
                        return null;
                    };

                    /**
                     * Creates a ConvVersion message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {im.body.v1.SyncReq.ConvVersion} ConvVersion
                     */
                    ConvVersion.fromObject = function fromObject(object, long) {
                        if (object instanceof $root.im.body.v1.SyncReq.ConvVersion)
                            return object;
                        if (!$util.isObject(object))
                            throw TypeError(".im.body.v1.SyncReq.ConvVersion: object expected");
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let message = new $root.im.body.v1.SyncReq.ConvVersion();
                        if (object.convId != null)
                            if ($util.Long)
                                message.convId = $util.Long.fromValue(object.convId, false);
                            else if (typeof object.convId === "string")
                                message.convId = parseInt(object.convId, 10);
                            else if (typeof object.convId === "number")
                                message.convId = object.convId;
                            else if (typeof object.convId === "object")
                                message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                        if (object.localMaxSeq != null)
                            if ($util.Long)
                                message.localMaxSeq = $util.Long.fromValue(object.localMaxSeq, false);
                            else if (typeof object.localMaxSeq === "string")
                                message.localMaxSeq = parseInt(object.localMaxSeq, 10);
                            else if (typeof object.localMaxSeq === "number")
                                message.localMaxSeq = object.localMaxSeq;
                            else if (typeof object.localMaxSeq === "object")
                                message.localMaxSeq = new $util.LongBits(object.localMaxSeq.low >>> 0, object.localMaxSeq.high >>> 0).toNumber();
                        return message;
                    };

                    /**
                     * Creates a plain object from a ConvVersion message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {im.body.v1.SyncReq.ConvVersion} message ConvVersion
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    ConvVersion.toObject = function toObject(message, options, q) {
                        if (!options)
                            options = {};
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        let object = {};
                        if (options.defaults) {
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                            } else
                                object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.localMaxSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                            } else
                                object.localMaxSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        }
                        if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                            else if (typeof message.convId === "number")
                                object.convId = options.longs === String ? String(message.convId) : message.convId;
                            else
                                object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                        if (message.localMaxSeq != null && Object.hasOwnProperty.call(message, "localMaxSeq"))
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.localMaxSeq = typeof message.localMaxSeq === "number" ? BigInt(message.localMaxSeq) : $util.Long.fromBits(message.localMaxSeq.low >>> 0, message.localMaxSeq.high >>> 0, false).toBigInt();
                            else if (typeof message.localMaxSeq === "number")
                                object.localMaxSeq = options.longs === String ? String(message.localMaxSeq) : message.localMaxSeq;
                            else
                                object.localMaxSeq = options.longs === String ? $util.Long.prototype.toString.call(message.localMaxSeq) : options.longs === Number ? new $util.LongBits(message.localMaxSeq.low >>> 0, message.localMaxSeq.high >>> 0).toNumber() : message.localMaxSeq;
                        return object;
                    };

                    /**
                     * Converts this ConvVersion to JSON.
                     * @function toJSON
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    ConvVersion.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for ConvVersion
                     * @function getTypeUrl
                     * @memberof im.body.v1.SyncReq.ConvVersion
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    ConvVersion.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/im.body.v1.SyncReq.ConvVersion";
                    };

                    return ConvVersion;
                })();

                return SyncReq;
            })();

            v1.SyncResp = (function() {

                /**
                 * Properties of a SyncResp.
                 * @memberof im.body.v1
                 * @interface ISyncResp
                 * @property {Array.<im.body.v1.SyncResp.IConvDelta>|null} [deltas] SyncResp deltas
                 * @property {number|Long|null} [convListVersion] SyncResp convListVersion
                 * @property {boolean|null} [fullSync] SyncResp fullSync
                 */

                /**
                 * Constructs a new SyncResp.
                 * @memberof im.body.v1
                 * @classdesc Represents a SyncResp.
                 * @implements ISyncResp
                 * @constructor
                 * @param {im.body.v1.ISyncResp=} [properties] Properties to set
                 */
                function SyncResp(properties) {
                    this.deltas = [];
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * SyncResp deltas.
                 * @member {Array.<im.body.v1.SyncResp.IConvDelta>} deltas
                 * @memberof im.body.v1.SyncResp
                 * @instance
                 */
                SyncResp.prototype.deltas = $util.emptyArray;

                /**
                 * SyncResp convListVersion.
                 * @member {number|Long} convListVersion
                 * @memberof im.body.v1.SyncResp
                 * @instance
                 */
                SyncResp.prototype.convListVersion = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * SyncResp fullSync.
                 * @member {boolean} fullSync
                 * @memberof im.body.v1.SyncResp
                 * @instance
                 */
                SyncResp.prototype.fullSync = false;

                /**
                 * Creates a new SyncResp instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {im.body.v1.ISyncResp=} [properties] Properties to set
                 * @returns {im.body.v1.SyncResp} SyncResp instance
                 */
                SyncResp.create = function create(properties) {
                    return new SyncResp(properties);
                };

                /**
                 * Encodes the specified SyncResp message. Does not implicitly {@link im.body.v1.SyncResp.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {im.body.v1.ISyncResp} message SyncResp message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                SyncResp.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.deltas != null && message.deltas.length)
                        for (let i = 0; i < message.deltas.length; ++i)
                            $root.im.body.v1.SyncResp.ConvDelta.encode(message.deltas[i], writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.convListVersion);
                    if (message.fullSync != null && Object.hasOwnProperty.call(message, "fullSync"))
                        writer.uint32(/* id 3, wireType 0 =*/24).bool(message.fullSync);
                    return writer;
                };

                /**
                 * Encodes the specified SyncResp message, length delimited. Does not implicitly {@link im.body.v1.SyncResp.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {im.body.v1.ISyncResp} message SyncResp message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                SyncResp.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a SyncResp message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.SyncResp} SyncResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                SyncResp.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.SyncResp();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                if (!(message.deltas && message.deltas.length))
                                    message.deltas = [];
                                message.deltas.push($root.im.body.v1.SyncResp.ConvDelta.decode(reader, reader.uint32(), undefined, long + 1));
                                break;
                            }
                        case 2: {
                                message.convListVersion = reader.int64();
                                break;
                            }
                        case 3: {
                                message.fullSync = reader.bool();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a SyncResp message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.SyncResp} SyncResp
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                SyncResp.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a SyncResp message.
                 * @function verify
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                SyncResp.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.deltas != null && Object.hasOwnProperty.call(message, "deltas")) {
                        if (!Array.isArray(message.deltas))
                            return "deltas: array expected";
                        for (let i = 0; i < message.deltas.length; ++i) {
                            let error = $root.im.body.v1.SyncResp.ConvDelta.verify(message.deltas[i], long + 1);
                            if (error)
                                return "deltas." + error;
                        }
                    }
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        if (!$util.isInteger(message.convListVersion) && !(message.convListVersion && $util.isInteger(message.convListVersion.low) && $util.isInteger(message.convListVersion.high)))
                            return "convListVersion: integer|Long expected";
                    if (message.fullSync != null && Object.hasOwnProperty.call(message, "fullSync"))
                        if (typeof message.fullSync !== "boolean")
                            return "fullSync: boolean expected";
                    return null;
                };

                /**
                 * Creates a SyncResp message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.SyncResp} SyncResp
                 */
                SyncResp.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.SyncResp)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.SyncResp: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.SyncResp();
                    if (object.deltas) {
                        if (!Array.isArray(object.deltas))
                            throw TypeError(".im.body.v1.SyncResp.deltas: array expected");
                        message.deltas = [];
                        for (let i = 0; i < object.deltas.length; ++i) {
                            if (!$util.isObject(object.deltas[i]))
                                throw TypeError(".im.body.v1.SyncResp.deltas: object expected");
                            message.deltas[i] = $root.im.body.v1.SyncResp.ConvDelta.fromObject(object.deltas[i], long + 1);
                        }
                    }
                    if (object.convListVersion != null)
                        if ($util.Long)
                            message.convListVersion = $util.Long.fromValue(object.convListVersion, false);
                        else if (typeof object.convListVersion === "string")
                            message.convListVersion = parseInt(object.convListVersion, 10);
                        else if (typeof object.convListVersion === "number")
                            message.convListVersion = object.convListVersion;
                        else if (typeof object.convListVersion === "object")
                            message.convListVersion = new $util.LongBits(object.convListVersion.low >>> 0, object.convListVersion.high >>> 0).toNumber();
                    if (object.fullSync != null)
                        message.fullSync = Boolean(object.fullSync);
                    return message;
                };

                /**
                 * Creates a plain object from a SyncResp message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {im.body.v1.SyncResp} message SyncResp
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                SyncResp.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.arrays || options.defaults)
                        object.deltas = [];
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convListVersion = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convListVersion = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.fullSync = false;
                    }
                    if (message.deltas && message.deltas.length) {
                        object.deltas = [];
                        for (let j = 0; j < message.deltas.length; ++j)
                            object.deltas[j] = $root.im.body.v1.SyncResp.ConvDelta.toObject(message.deltas[j], options, q + 1);
                    }
                    if (message.convListVersion != null && Object.hasOwnProperty.call(message, "convListVersion"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convListVersion = typeof message.convListVersion === "number" ? BigInt(message.convListVersion) : $util.Long.fromBits(message.convListVersion.low >>> 0, message.convListVersion.high >>> 0, false).toBigInt();
                        else if (typeof message.convListVersion === "number")
                            object.convListVersion = options.longs === String ? String(message.convListVersion) : message.convListVersion;
                        else
                            object.convListVersion = options.longs === String ? $util.Long.prototype.toString.call(message.convListVersion) : options.longs === Number ? new $util.LongBits(message.convListVersion.low >>> 0, message.convListVersion.high >>> 0).toNumber() : message.convListVersion;
                    if (message.fullSync != null && Object.hasOwnProperty.call(message, "fullSync"))
                        object.fullSync = message.fullSync;
                    return object;
                };

                /**
                 * Converts this SyncResp to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.SyncResp
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                SyncResp.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for SyncResp
                 * @function getTypeUrl
                 * @memberof im.body.v1.SyncResp
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                SyncResp.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.SyncResp";
                };

                SyncResp.ConvDelta = (function() {

                    /**
                     * Properties of a ConvDelta.
                     * @memberof im.body.v1.SyncResp
                     * @interface IConvDelta
                     * @property {im.body.v1.IConvInfo|null} [conv] ConvDelta conv
                     * @property {Array.<im.body.v1.IMsgPush>|null} [msgs] ConvDelta msgs
                     * @property {number|Long|null} [serverMaxSeq] ConvDelta serverMaxSeq
                     * @property {boolean|null} [hasMore] ConvDelta hasMore
                     */

                    /**
                     * Constructs a new ConvDelta.
                     * @memberof im.body.v1.SyncResp
                     * @classdesc Represents a ConvDelta.
                     * @implements IConvDelta
                     * @constructor
                     * @param {im.body.v1.SyncResp.IConvDelta=} [properties] Properties to set
                     */
                    function ConvDelta(properties) {
                        this.msgs = [];
                        if (properties)
                            for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                                if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                    this[keys[i]] = properties[keys[i]];
                    }

                    /**
                     * ConvDelta conv.
                     * @member {im.body.v1.IConvInfo|null|undefined} conv
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @instance
                     */
                    ConvDelta.prototype.conv = null;

                    /**
                     * ConvDelta msgs.
                     * @member {Array.<im.body.v1.IMsgPush>} msgs
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @instance
                     */
                    ConvDelta.prototype.msgs = $util.emptyArray;

                    /**
                     * ConvDelta serverMaxSeq.
                     * @member {number|Long} serverMaxSeq
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @instance
                     */
                    ConvDelta.prototype.serverMaxSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                    /**
                     * ConvDelta hasMore.
                     * @member {boolean} hasMore
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @instance
                     */
                    ConvDelta.prototype.hasMore = false;

                    /**
                     * Creates a new ConvDelta instance using the specified properties.
                     * @function create
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {im.body.v1.SyncResp.IConvDelta=} [properties] Properties to set
                     * @returns {im.body.v1.SyncResp.ConvDelta} ConvDelta instance
                     */
                    ConvDelta.create = function create(properties) {
                        return new ConvDelta(properties);
                    };

                    /**
                     * Encodes the specified ConvDelta message. Does not implicitly {@link im.body.v1.SyncResp.ConvDelta.verify|verify} messages.
                     * @function encode
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {im.body.v1.SyncResp.IConvDelta} message ConvDelta message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    ConvDelta.encode = function encode(message, writer, q) {
                        if (!writer)
                            writer = $Writer.create();
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        if (message.conv != null && Object.hasOwnProperty.call(message, "conv"))
                            $root.im.body.v1.ConvInfo.encode(message.conv, writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                        if (message.msgs != null && message.msgs.length)
                            for (let i = 0; i < message.msgs.length; ++i)
                                $root.im.body.v1.MsgPush.encode(message.msgs[i], writer.uint32(/* id 2, wireType 2 =*/18).fork(), q + 1).ldelim();
                        if (message.serverMaxSeq != null && Object.hasOwnProperty.call(message, "serverMaxSeq"))
                            writer.uint32(/* id 3, wireType 0 =*/24).int64(message.serverMaxSeq);
                        if (message.hasMore != null && Object.hasOwnProperty.call(message, "hasMore"))
                            writer.uint32(/* id 4, wireType 0 =*/32).bool(message.hasMore);
                        return writer;
                    };

                    /**
                     * Encodes the specified ConvDelta message, length delimited. Does not implicitly {@link im.body.v1.SyncResp.ConvDelta.verify|verify} messages.
                     * @function encodeDelimited
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {im.body.v1.SyncResp.IConvDelta} message ConvDelta message or plain object to encode
                     * @param {$protobuf.Writer} [writer] Writer to encode to
                     * @returns {$protobuf.Writer} Writer
                     */
                    ConvDelta.encodeDelimited = function encodeDelimited(message, writer) {
                        return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                    };

                    /**
                     * Decodes a ConvDelta message from the specified reader or buffer.
                     * @function decode
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @param {number} [length] Message length if known beforehand
                     * @returns {im.body.v1.SyncResp.ConvDelta} ConvDelta
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    ConvDelta.decode = function decode(reader, length, error, long) {
                        if (!(reader instanceof $Reader))
                            reader = $Reader.create(reader);
                        if (long === undefined)
                            long = 0;
                        if (long > $Reader.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.SyncResp.ConvDelta();
                        while (reader.pos < end) {
                            let tag = reader.uint32();
                            if (tag === error)
                                break;
                            switch (tag >>> 3) {
                            case 1: {
                                    message.conv = $root.im.body.v1.ConvInfo.decode(reader, reader.uint32(), undefined, long + 1);
                                    break;
                                }
                            case 2: {
                                    if (!(message.msgs && message.msgs.length))
                                        message.msgs = [];
                                    message.msgs.push($root.im.body.v1.MsgPush.decode(reader, reader.uint32(), undefined, long + 1));
                                    break;
                                }
                            case 3: {
                                    message.serverMaxSeq = reader.int64();
                                    break;
                                }
                            case 4: {
                                    message.hasMore = reader.bool();
                                    break;
                                }
                            default:
                                reader.skipType(tag & 7, long);
                                break;
                            }
                        }
                        return message;
                    };

                    /**
                     * Decodes a ConvDelta message from the specified reader or buffer, length delimited.
                     * @function decodeDelimited
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                     * @returns {im.body.v1.SyncResp.ConvDelta} ConvDelta
                     * @throws {Error} If the payload is not a reader or valid buffer
                     * @throws {$protobuf.util.ProtocolError} If required fields are missing
                     */
                    ConvDelta.decodeDelimited = function decodeDelimited(reader) {
                        if (!(reader instanceof $Reader))
                            reader = new $Reader(reader);
                        return this.decode(reader, reader.uint32());
                    };

                    /**
                     * Verifies a ConvDelta message.
                     * @function verify
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {Object.<string,*>} message Plain object to verify
                     * @returns {string|null} `null` if valid, otherwise the reason why it is not
                     */
                    ConvDelta.verify = function verify(message, long) {
                        if (typeof message !== "object" || message === null)
                            return "object expected";
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            return "maximum nesting depth exceeded";
                        if (message.conv != null && Object.hasOwnProperty.call(message, "conv")) {
                            let error = $root.im.body.v1.ConvInfo.verify(message.conv, long + 1);
                            if (error)
                                return "conv." + error;
                        }
                        if (message.msgs != null && Object.hasOwnProperty.call(message, "msgs")) {
                            if (!Array.isArray(message.msgs))
                                return "msgs: array expected";
                            for (let i = 0; i < message.msgs.length; ++i) {
                                let error = $root.im.body.v1.MsgPush.verify(message.msgs[i], long + 1);
                                if (error)
                                    return "msgs." + error;
                            }
                        }
                        if (message.serverMaxSeq != null && Object.hasOwnProperty.call(message, "serverMaxSeq"))
                            if (!$util.isInteger(message.serverMaxSeq) && !(message.serverMaxSeq && $util.isInteger(message.serverMaxSeq.low) && $util.isInteger(message.serverMaxSeq.high)))
                                return "serverMaxSeq: integer|Long expected";
                        if (message.hasMore != null && Object.hasOwnProperty.call(message, "hasMore"))
                            if (typeof message.hasMore !== "boolean")
                                return "hasMore: boolean expected";
                        return null;
                    };

                    /**
                     * Creates a ConvDelta message from a plain object. Also converts values to their respective internal types.
                     * @function fromObject
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {Object.<string,*>} object Plain object
                     * @returns {im.body.v1.SyncResp.ConvDelta} ConvDelta
                     */
                    ConvDelta.fromObject = function fromObject(object, long) {
                        if (object instanceof $root.im.body.v1.SyncResp.ConvDelta)
                            return object;
                        if (!$util.isObject(object))
                            throw TypeError(".im.body.v1.SyncResp.ConvDelta: object expected");
                        if (long === undefined)
                            long = 0;
                        if (long > $util.recursionLimit)
                            throw Error("maximum nesting depth exceeded");
                        let message = new $root.im.body.v1.SyncResp.ConvDelta();
                        if (object.conv != null) {
                            if (!$util.isObject(object.conv))
                                throw TypeError(".im.body.v1.SyncResp.ConvDelta.conv: object expected");
                            message.conv = $root.im.body.v1.ConvInfo.fromObject(object.conv, long + 1);
                        }
                        if (object.msgs) {
                            if (!Array.isArray(object.msgs))
                                throw TypeError(".im.body.v1.SyncResp.ConvDelta.msgs: array expected");
                            message.msgs = [];
                            for (let i = 0; i < object.msgs.length; ++i) {
                                if (!$util.isObject(object.msgs[i]))
                                    throw TypeError(".im.body.v1.SyncResp.ConvDelta.msgs: object expected");
                                message.msgs[i] = $root.im.body.v1.MsgPush.fromObject(object.msgs[i], long + 1);
                            }
                        }
                        if (object.serverMaxSeq != null)
                            if ($util.Long)
                                message.serverMaxSeq = $util.Long.fromValue(object.serverMaxSeq, false);
                            else if (typeof object.serverMaxSeq === "string")
                                message.serverMaxSeq = parseInt(object.serverMaxSeq, 10);
                            else if (typeof object.serverMaxSeq === "number")
                                message.serverMaxSeq = object.serverMaxSeq;
                            else if (typeof object.serverMaxSeq === "object")
                                message.serverMaxSeq = new $util.LongBits(object.serverMaxSeq.low >>> 0, object.serverMaxSeq.high >>> 0).toNumber();
                        if (object.hasMore != null)
                            message.hasMore = Boolean(object.hasMore);
                        return message;
                    };

                    /**
                     * Creates a plain object from a ConvDelta message. Also converts values to other types if specified.
                     * @function toObject
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {im.body.v1.SyncResp.ConvDelta} message ConvDelta
                     * @param {$protobuf.IConversionOptions} [options] Conversion options
                     * @returns {Object.<string,*>} Plain object
                     */
                    ConvDelta.toObject = function toObject(message, options, q) {
                        if (!options)
                            options = {};
                        if (q === undefined)
                            q = 0;
                        if (q > $util.recursionLimit)
                            throw Error("max depth exceeded");
                        let object = {};
                        if (options.arrays || options.defaults)
                            object.msgs = [];
                        if (options.defaults) {
                            object.conv = null;
                            if ($util.Long) {
                                let long = new $util.Long(0, 0, false);
                                object.serverMaxSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                            } else
                                object.serverMaxSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                            object.hasMore = false;
                        }
                        if (message.conv != null && Object.hasOwnProperty.call(message, "conv"))
                            object.conv = $root.im.body.v1.ConvInfo.toObject(message.conv, options, q + 1);
                        if (message.msgs && message.msgs.length) {
                            object.msgs = [];
                            for (let j = 0; j < message.msgs.length; ++j)
                                object.msgs[j] = $root.im.body.v1.MsgPush.toObject(message.msgs[j], options, q + 1);
                        }
                        if (message.serverMaxSeq != null && Object.hasOwnProperty.call(message, "serverMaxSeq"))
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.serverMaxSeq = typeof message.serverMaxSeq === "number" ? BigInt(message.serverMaxSeq) : $util.Long.fromBits(message.serverMaxSeq.low >>> 0, message.serverMaxSeq.high >>> 0, false).toBigInt();
                            else if (typeof message.serverMaxSeq === "number")
                                object.serverMaxSeq = options.longs === String ? String(message.serverMaxSeq) : message.serverMaxSeq;
                            else
                                object.serverMaxSeq = options.longs === String ? $util.Long.prototype.toString.call(message.serverMaxSeq) : options.longs === Number ? new $util.LongBits(message.serverMaxSeq.low >>> 0, message.serverMaxSeq.high >>> 0).toNumber() : message.serverMaxSeq;
                        if (message.hasMore != null && Object.hasOwnProperty.call(message, "hasMore"))
                            object.hasMore = message.hasMore;
                        return object;
                    };

                    /**
                     * Converts this ConvDelta to JSON.
                     * @function toJSON
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @instance
                     * @returns {Object.<string,*>} JSON object
                     */
                    ConvDelta.prototype.toJSON = function toJSON() {
                        return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                    };

                    /**
                     * Gets the default type url for ConvDelta
                     * @function getTypeUrl
                     * @memberof im.body.v1.SyncResp.ConvDelta
                     * @static
                     * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                     * @returns {string} The default type url
                     */
                    ConvDelta.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                        if (typeUrlPrefix === undefined) {
                            typeUrlPrefix = "type.googleapis.com";
                        }
                        return typeUrlPrefix + "/im.body.v1.SyncResp.ConvDelta";
                    };

                    return ConvDelta;
                })();

                return SyncResp;
            })();

            v1.ConvInfo = (function() {

                /**
                 * Properties of a ConvInfo.
                 * @memberof im.body.v1
                 * @interface IConvInfo
                 * @property {number|Long|null} [convId] ConvInfo convId
                 * @property {im.common.v1.ConvType|null} [type] ConvInfo type
                 * @property {string|null} [title] ConvInfo title
                 * @property {string|null} [avatar] ConvInfo avatar
                 * @property {number|Long|null} [peerUserId] ConvInfo peerUserId
                 * @property {number|Long|null} [groupId] ConvInfo groupId
                 * @property {number|Long|null} [maxSeq] ConvInfo maxSeq
                 * @property {number|Long|null} [readSeq] ConvInfo readSeq
                 * @property {boolean|null} [pinned] ConvInfo pinned
                 * @property {boolean|null} [muted] ConvInfo muted
                 * @property {string|null} [lastMsgAbstract] ConvInfo lastMsgAbstract
                 * @property {number|Long|null} [lastMsgTime] ConvInfo lastMsgTime
                 * @property {string|null} [csStatus] ConvInfo csStatus
                 * @property {boolean|null} [deleted] ConvInfo deleted
                 */

                /**
                 * Constructs a new ConvInfo.
                 * @memberof im.body.v1
                 * @classdesc Represents a ConvInfo.
                 * @implements IConvInfo
                 * @constructor
                 * @param {im.body.v1.IConvInfo=} [properties] Properties to set
                 */
                function ConvInfo(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ConvInfo convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo type.
                 * @member {im.common.v1.ConvType} type
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.type = 0;

                /**
                 * ConvInfo title.
                 * @member {string} title
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.title = "";

                /**
                 * ConvInfo avatar.
                 * @member {string} avatar
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.avatar = "";

                /**
                 * ConvInfo peerUserId.
                 * @member {number|Long} peerUserId
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.peerUserId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo groupId.
                 * @member {number|Long} groupId
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.groupId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo maxSeq.
                 * @member {number|Long} maxSeq
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.maxSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo readSeq.
                 * @member {number|Long} readSeq
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.readSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo pinned.
                 * @member {boolean} pinned
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.pinned = false;

                /**
                 * ConvInfo muted.
                 * @member {boolean} muted
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.muted = false;

                /**
                 * ConvInfo lastMsgAbstract.
                 * @member {string} lastMsgAbstract
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.lastMsgAbstract = "";

                /**
                 * ConvInfo lastMsgTime.
                 * @member {number|Long} lastMsgTime
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.lastMsgTime = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ConvInfo csStatus.
                 * @member {string} csStatus
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.csStatus = "";

                /**
                 * ConvInfo deleted.
                 * @member {boolean} deleted
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 */
                ConvInfo.prototype.deleted = false;

                /**
                 * Creates a new ConvInfo instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {im.body.v1.IConvInfo=} [properties] Properties to set
                 * @returns {im.body.v1.ConvInfo} ConvInfo instance
                 */
                ConvInfo.create = function create(properties) {
                    return new ConvInfo(properties);
                };

                /**
                 * Encodes the specified ConvInfo message. Does not implicitly {@link im.body.v1.ConvInfo.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {im.body.v1.IConvInfo} message ConvInfo message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ConvInfo.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                    if (message.type != null && Object.hasOwnProperty.call(message, "type"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int32(message.type);
                    if (message.title != null && Object.hasOwnProperty.call(message, "title"))
                        writer.uint32(/* id 3, wireType 2 =*/26).string(message.title);
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        writer.uint32(/* id 4, wireType 2 =*/34).string(message.avatar);
                    if (message.peerUserId != null && Object.hasOwnProperty.call(message, "peerUserId"))
                        writer.uint32(/* id 5, wireType 0 =*/40).int64(message.peerUserId);
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId"))
                        writer.uint32(/* id 6, wireType 0 =*/48).int64(message.groupId);
                    if (message.maxSeq != null && Object.hasOwnProperty.call(message, "maxSeq"))
                        writer.uint32(/* id 7, wireType 0 =*/56).int64(message.maxSeq);
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        writer.uint32(/* id 8, wireType 0 =*/64).int64(message.readSeq);
                    if (message.pinned != null && Object.hasOwnProperty.call(message, "pinned"))
                        writer.uint32(/* id 9, wireType 0 =*/72).bool(message.pinned);
                    if (message.muted != null && Object.hasOwnProperty.call(message, "muted"))
                        writer.uint32(/* id 10, wireType 0 =*/80).bool(message.muted);
                    if (message.lastMsgAbstract != null && Object.hasOwnProperty.call(message, "lastMsgAbstract"))
                        writer.uint32(/* id 11, wireType 2 =*/90).string(message.lastMsgAbstract);
                    if (message.lastMsgTime != null && Object.hasOwnProperty.call(message, "lastMsgTime"))
                        writer.uint32(/* id 12, wireType 0 =*/96).int64(message.lastMsgTime);
                    if (message.csStatus != null && Object.hasOwnProperty.call(message, "csStatus"))
                        writer.uint32(/* id 13, wireType 2 =*/106).string(message.csStatus);
                    if (message.deleted != null && Object.hasOwnProperty.call(message, "deleted"))
                        writer.uint32(/* id 14, wireType 0 =*/112).bool(message.deleted);
                    return writer;
                };

                /**
                 * Encodes the specified ConvInfo message, length delimited. Does not implicitly {@link im.body.v1.ConvInfo.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {im.body.v1.IConvInfo} message ConvInfo message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ConvInfo.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a ConvInfo message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.ConvInfo} ConvInfo
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ConvInfo.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.ConvInfo();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.type = reader.int32();
                                break;
                            }
                        case 3: {
                                message.title = reader.string();
                                break;
                            }
                        case 4: {
                                message.avatar = reader.string();
                                break;
                            }
                        case 5: {
                                message.peerUserId = reader.int64();
                                break;
                            }
                        case 6: {
                                message.groupId = reader.int64();
                                break;
                            }
                        case 7: {
                                message.maxSeq = reader.int64();
                                break;
                            }
                        case 8: {
                                message.readSeq = reader.int64();
                                break;
                            }
                        case 9: {
                                message.pinned = reader.bool();
                                break;
                            }
                        case 10: {
                                message.muted = reader.bool();
                                break;
                            }
                        case 11: {
                                message.lastMsgAbstract = reader.string();
                                break;
                            }
                        case 12: {
                                message.lastMsgTime = reader.int64();
                                break;
                            }
                        case 13: {
                                message.csStatus = reader.string();
                                break;
                            }
                        case 14: {
                                message.deleted = reader.bool();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a ConvInfo message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.ConvInfo} ConvInfo
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ConvInfo.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a ConvInfo message.
                 * @function verify
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ConvInfo.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.type != null && Object.hasOwnProperty.call(message, "type"))
                        switch (message.type) {
                        default:
                            return "type: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                        case 4:
                            break;
                        }
                    if (message.title != null && Object.hasOwnProperty.call(message, "title"))
                        if (!$util.isString(message.title))
                            return "title: string expected";
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        if (!$util.isString(message.avatar))
                            return "avatar: string expected";
                    if (message.peerUserId != null && Object.hasOwnProperty.call(message, "peerUserId"))
                        if (!$util.isInteger(message.peerUserId) && !(message.peerUserId && $util.isInteger(message.peerUserId.low) && $util.isInteger(message.peerUserId.high)))
                            return "peerUserId: integer|Long expected";
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId"))
                        if (!$util.isInteger(message.groupId) && !(message.groupId && $util.isInteger(message.groupId.low) && $util.isInteger(message.groupId.high)))
                            return "groupId: integer|Long expected";
                    if (message.maxSeq != null && Object.hasOwnProperty.call(message, "maxSeq"))
                        if (!$util.isInteger(message.maxSeq) && !(message.maxSeq && $util.isInteger(message.maxSeq.low) && $util.isInteger(message.maxSeq.high)))
                            return "maxSeq: integer|Long expected";
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (!$util.isInteger(message.readSeq) && !(message.readSeq && $util.isInteger(message.readSeq.low) && $util.isInteger(message.readSeq.high)))
                            return "readSeq: integer|Long expected";
                    if (message.pinned != null && Object.hasOwnProperty.call(message, "pinned"))
                        if (typeof message.pinned !== "boolean")
                            return "pinned: boolean expected";
                    if (message.muted != null && Object.hasOwnProperty.call(message, "muted"))
                        if (typeof message.muted !== "boolean")
                            return "muted: boolean expected";
                    if (message.lastMsgAbstract != null && Object.hasOwnProperty.call(message, "lastMsgAbstract"))
                        if (!$util.isString(message.lastMsgAbstract))
                            return "lastMsgAbstract: string expected";
                    if (message.lastMsgTime != null && Object.hasOwnProperty.call(message, "lastMsgTime"))
                        if (!$util.isInteger(message.lastMsgTime) && !(message.lastMsgTime && $util.isInteger(message.lastMsgTime.low) && $util.isInteger(message.lastMsgTime.high)))
                            return "lastMsgTime: integer|Long expected";
                    if (message.csStatus != null && Object.hasOwnProperty.call(message, "csStatus"))
                        if (!$util.isString(message.csStatus))
                            return "csStatus: string expected";
                    if (message.deleted != null && Object.hasOwnProperty.call(message, "deleted"))
                        if (typeof message.deleted !== "boolean")
                            return "deleted: boolean expected";
                    return null;
                };

                /**
                 * Creates a ConvInfo message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.ConvInfo} ConvInfo
                 */
                ConvInfo.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.ConvInfo)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.ConvInfo: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.ConvInfo();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    switch (object.type) {
                    default:
                        if (typeof object.type === "number") {
                            message.type = object.type;
                            break;
                        }
                        break;
                    case "CONV_TYPE_UNSPECIFIED":
                    case 0:
                        message.type = 0;
                        break;
                    case "C2C":
                    case 1:
                        message.type = 1;
                        break;
                    case "GROUP":
                    case 2:
                        message.type = 2;
                        break;
                    case "CS_SESSION":
                    case 3:
                        message.type = 3;
                        break;
                    case "SYSTEM":
                    case 4:
                        message.type = 4;
                        break;
                    }
                    if (object.title != null)
                        message.title = String(object.title);
                    if (object.avatar != null)
                        message.avatar = String(object.avatar);
                    if (object.peerUserId != null)
                        if ($util.Long)
                            message.peerUserId = $util.Long.fromValue(object.peerUserId, false);
                        else if (typeof object.peerUserId === "string")
                            message.peerUserId = parseInt(object.peerUserId, 10);
                        else if (typeof object.peerUserId === "number")
                            message.peerUserId = object.peerUserId;
                        else if (typeof object.peerUserId === "object")
                            message.peerUserId = new $util.LongBits(object.peerUserId.low >>> 0, object.peerUserId.high >>> 0).toNumber();
                    if (object.groupId != null)
                        if ($util.Long)
                            message.groupId = $util.Long.fromValue(object.groupId, false);
                        else if (typeof object.groupId === "string")
                            message.groupId = parseInt(object.groupId, 10);
                        else if (typeof object.groupId === "number")
                            message.groupId = object.groupId;
                        else if (typeof object.groupId === "object")
                            message.groupId = new $util.LongBits(object.groupId.low >>> 0, object.groupId.high >>> 0).toNumber();
                    if (object.maxSeq != null)
                        if ($util.Long)
                            message.maxSeq = $util.Long.fromValue(object.maxSeq, false);
                        else if (typeof object.maxSeq === "string")
                            message.maxSeq = parseInt(object.maxSeq, 10);
                        else if (typeof object.maxSeq === "number")
                            message.maxSeq = object.maxSeq;
                        else if (typeof object.maxSeq === "object")
                            message.maxSeq = new $util.LongBits(object.maxSeq.low >>> 0, object.maxSeq.high >>> 0).toNumber();
                    if (object.readSeq != null)
                        if ($util.Long)
                            message.readSeq = $util.Long.fromValue(object.readSeq, false);
                        else if (typeof object.readSeq === "string")
                            message.readSeq = parseInt(object.readSeq, 10);
                        else if (typeof object.readSeq === "number")
                            message.readSeq = object.readSeq;
                        else if (typeof object.readSeq === "object")
                            message.readSeq = new $util.LongBits(object.readSeq.low >>> 0, object.readSeq.high >>> 0).toNumber();
                    if (object.pinned != null)
                        message.pinned = Boolean(object.pinned);
                    if (object.muted != null)
                        message.muted = Boolean(object.muted);
                    if (object.lastMsgAbstract != null)
                        message.lastMsgAbstract = String(object.lastMsgAbstract);
                    if (object.lastMsgTime != null)
                        if ($util.Long)
                            message.lastMsgTime = $util.Long.fromValue(object.lastMsgTime, false);
                        else if (typeof object.lastMsgTime === "string")
                            message.lastMsgTime = parseInt(object.lastMsgTime, 10);
                        else if (typeof object.lastMsgTime === "number")
                            message.lastMsgTime = object.lastMsgTime;
                        else if (typeof object.lastMsgTime === "object")
                            message.lastMsgTime = new $util.LongBits(object.lastMsgTime.low >>> 0, object.lastMsgTime.high >>> 0).toNumber();
                    if (object.csStatus != null)
                        message.csStatus = String(object.csStatus);
                    if (object.deleted != null)
                        message.deleted = Boolean(object.deleted);
                    return message;
                };

                /**
                 * Creates a plain object from a ConvInfo message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {im.body.v1.ConvInfo} message ConvInfo
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ConvInfo.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.type = options.enums === String ? "CONV_TYPE_UNSPECIFIED" : 0;
                        object.title = "";
                        object.avatar = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.peerUserId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.peerUserId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.groupId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.groupId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.maxSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.maxSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.readSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.readSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.pinned = false;
                        object.muted = false;
                        object.lastMsgAbstract = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.lastMsgTime = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.lastMsgTime = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.csStatus = "";
                        object.deleted = false;
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.type != null && Object.hasOwnProperty.call(message, "type"))
                        object.type = options.enums === String ? $root.im.common.v1.ConvType[message.type] === undefined ? message.type : $root.im.common.v1.ConvType[message.type] : message.type;
                    if (message.title != null && Object.hasOwnProperty.call(message, "title"))
                        object.title = message.title;
                    if (message.avatar != null && Object.hasOwnProperty.call(message, "avatar"))
                        object.avatar = message.avatar;
                    if (message.peerUserId != null && Object.hasOwnProperty.call(message, "peerUserId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.peerUserId = typeof message.peerUserId === "number" ? BigInt(message.peerUserId) : $util.Long.fromBits(message.peerUserId.low >>> 0, message.peerUserId.high >>> 0, false).toBigInt();
                        else if (typeof message.peerUserId === "number")
                            object.peerUserId = options.longs === String ? String(message.peerUserId) : message.peerUserId;
                        else
                            object.peerUserId = options.longs === String ? $util.Long.prototype.toString.call(message.peerUserId) : options.longs === Number ? new $util.LongBits(message.peerUserId.low >>> 0, message.peerUserId.high >>> 0).toNumber() : message.peerUserId;
                    if (message.groupId != null && Object.hasOwnProperty.call(message, "groupId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.groupId = typeof message.groupId === "number" ? BigInt(message.groupId) : $util.Long.fromBits(message.groupId.low >>> 0, message.groupId.high >>> 0, false).toBigInt();
                        else if (typeof message.groupId === "number")
                            object.groupId = options.longs === String ? String(message.groupId) : message.groupId;
                        else
                            object.groupId = options.longs === String ? $util.Long.prototype.toString.call(message.groupId) : options.longs === Number ? new $util.LongBits(message.groupId.low >>> 0, message.groupId.high >>> 0).toNumber() : message.groupId;
                    if (message.maxSeq != null && Object.hasOwnProperty.call(message, "maxSeq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.maxSeq = typeof message.maxSeq === "number" ? BigInt(message.maxSeq) : $util.Long.fromBits(message.maxSeq.low >>> 0, message.maxSeq.high >>> 0, false).toBigInt();
                        else if (typeof message.maxSeq === "number")
                            object.maxSeq = options.longs === String ? String(message.maxSeq) : message.maxSeq;
                        else
                            object.maxSeq = options.longs === String ? $util.Long.prototype.toString.call(message.maxSeq) : options.longs === Number ? new $util.LongBits(message.maxSeq.low >>> 0, message.maxSeq.high >>> 0).toNumber() : message.maxSeq;
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.readSeq = typeof message.readSeq === "number" ? BigInt(message.readSeq) : $util.Long.fromBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0, false).toBigInt();
                        else if (typeof message.readSeq === "number")
                            object.readSeq = options.longs === String ? String(message.readSeq) : message.readSeq;
                        else
                            object.readSeq = options.longs === String ? $util.Long.prototype.toString.call(message.readSeq) : options.longs === Number ? new $util.LongBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0).toNumber() : message.readSeq;
                    if (message.pinned != null && Object.hasOwnProperty.call(message, "pinned"))
                        object.pinned = message.pinned;
                    if (message.muted != null && Object.hasOwnProperty.call(message, "muted"))
                        object.muted = message.muted;
                    if (message.lastMsgAbstract != null && Object.hasOwnProperty.call(message, "lastMsgAbstract"))
                        object.lastMsgAbstract = message.lastMsgAbstract;
                    if (message.lastMsgTime != null && Object.hasOwnProperty.call(message, "lastMsgTime"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.lastMsgTime = typeof message.lastMsgTime === "number" ? BigInt(message.lastMsgTime) : $util.Long.fromBits(message.lastMsgTime.low >>> 0, message.lastMsgTime.high >>> 0, false).toBigInt();
                        else if (typeof message.lastMsgTime === "number")
                            object.lastMsgTime = options.longs === String ? String(message.lastMsgTime) : message.lastMsgTime;
                        else
                            object.lastMsgTime = options.longs === String ? $util.Long.prototype.toString.call(message.lastMsgTime) : options.longs === Number ? new $util.LongBits(message.lastMsgTime.low >>> 0, message.lastMsgTime.high >>> 0).toNumber() : message.lastMsgTime;
                    if (message.csStatus != null && Object.hasOwnProperty.call(message, "csStatus"))
                        object.csStatus = message.csStatus;
                    if (message.deleted != null && Object.hasOwnProperty.call(message, "deleted"))
                        object.deleted = message.deleted;
                    return object;
                };

                /**
                 * Converts this ConvInfo to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.ConvInfo
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ConvInfo.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ConvInfo
                 * @function getTypeUrl
                 * @memberof im.body.v1.ConvInfo
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ConvInfo.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.ConvInfo";
                };

                return ConvInfo;
            })();

            v1.ReadReport = (function() {

                /**
                 * Properties of a ReadReport.
                 * @memberof im.body.v1
                 * @interface IReadReport
                 * @property {number|Long|null} [convId] ReadReport convId
                 * @property {number|Long|null} [readSeq] ReadReport readSeq
                 */

                /**
                 * Constructs a new ReadReport.
                 * @memberof im.body.v1
                 * @classdesc Represents a ReadReport.
                 * @implements IReadReport
                 * @constructor
                 * @param {im.body.v1.IReadReport=} [properties] Properties to set
                 */
                function ReadReport(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ReadReport convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.ReadReport
                 * @instance
                 */
                ReadReport.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ReadReport readSeq.
                 * @member {number|Long} readSeq
                 * @memberof im.body.v1.ReadReport
                 * @instance
                 */
                ReadReport.prototype.readSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new ReadReport instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {im.body.v1.IReadReport=} [properties] Properties to set
                 * @returns {im.body.v1.ReadReport} ReadReport instance
                 */
                ReadReport.create = function create(properties) {
                    return new ReadReport(properties);
                };

                /**
                 * Encodes the specified ReadReport message. Does not implicitly {@link im.body.v1.ReadReport.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {im.body.v1.IReadReport} message ReadReport message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ReadReport.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.readSeq);
                    return writer;
                };

                /**
                 * Encodes the specified ReadReport message, length delimited. Does not implicitly {@link im.body.v1.ReadReport.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {im.body.v1.IReadReport} message ReadReport message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ReadReport.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a ReadReport message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.ReadReport} ReadReport
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ReadReport.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.ReadReport();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.readSeq = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a ReadReport message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.ReadReport} ReadReport
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ReadReport.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a ReadReport message.
                 * @function verify
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ReadReport.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (!$util.isInteger(message.readSeq) && !(message.readSeq && $util.isInteger(message.readSeq.low) && $util.isInteger(message.readSeq.high)))
                            return "readSeq: integer|Long expected";
                    return null;
                };

                /**
                 * Creates a ReadReport message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.ReadReport} ReadReport
                 */
                ReadReport.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.ReadReport)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.ReadReport: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.ReadReport();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    if (object.readSeq != null)
                        if ($util.Long)
                            message.readSeq = $util.Long.fromValue(object.readSeq, false);
                        else if (typeof object.readSeq === "string")
                            message.readSeq = parseInt(object.readSeq, 10);
                        else if (typeof object.readSeq === "number")
                            message.readSeq = object.readSeq;
                        else if (typeof object.readSeq === "object")
                            message.readSeq = new $util.LongBits(object.readSeq.low >>> 0, object.readSeq.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from a ReadReport message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {im.body.v1.ReadReport} message ReadReport
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ReadReport.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.readSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.readSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.readSeq = typeof message.readSeq === "number" ? BigInt(message.readSeq) : $util.Long.fromBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0, false).toBigInt();
                        else if (typeof message.readSeq === "number")
                            object.readSeq = options.longs === String ? String(message.readSeq) : message.readSeq;
                        else
                            object.readSeq = options.longs === String ? $util.Long.prototype.toString.call(message.readSeq) : options.longs === Number ? new $util.LongBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0).toNumber() : message.readSeq;
                    return object;
                };

                /**
                 * Converts this ReadReport to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.ReadReport
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ReadReport.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ReadReport
                 * @function getTypeUrl
                 * @memberof im.body.v1.ReadReport
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ReadReport.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.ReadReport";
                };

                return ReadReport;
            })();

            v1.ReadNotify = (function() {

                /**
                 * Properties of a ReadNotify.
                 * @memberof im.body.v1
                 * @interface IReadNotify
                 * @property {number|Long|null} [convId] ReadNotify convId
                 * @property {number|Long|null} [readerUserId] ReadNotify readerUserId
                 * @property {number|Long|null} [readSeq] ReadNotify readSeq
                 */

                /**
                 * Constructs a new ReadNotify.
                 * @memberof im.body.v1
                 * @classdesc Represents a ReadNotify.
                 * @implements IReadNotify
                 * @constructor
                 * @param {im.body.v1.IReadNotify=} [properties] Properties to set
                 */
                function ReadNotify(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ReadNotify convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.ReadNotify
                 * @instance
                 */
                ReadNotify.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ReadNotify readerUserId.
                 * @member {number|Long} readerUserId
                 * @memberof im.body.v1.ReadNotify
                 * @instance
                 */
                ReadNotify.prototype.readerUserId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * ReadNotify readSeq.
                 * @member {number|Long} readSeq
                 * @memberof im.body.v1.ReadNotify
                 * @instance
                 */
                ReadNotify.prototype.readSeq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new ReadNotify instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {im.body.v1.IReadNotify=} [properties] Properties to set
                 * @returns {im.body.v1.ReadNotify} ReadNotify instance
                 */
                ReadNotify.create = function create(properties) {
                    return new ReadNotify(properties);
                };

                /**
                 * Encodes the specified ReadNotify message. Does not implicitly {@link im.body.v1.ReadNotify.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {im.body.v1.IReadNotify} message ReadNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ReadNotify.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                    if (message.readerUserId != null && Object.hasOwnProperty.call(message, "readerUserId"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.readerUserId);
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.readSeq);
                    return writer;
                };

                /**
                 * Encodes the specified ReadNotify message, length delimited. Does not implicitly {@link im.body.v1.ReadNotify.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {im.body.v1.IReadNotify} message ReadNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ReadNotify.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a ReadNotify message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.ReadNotify} ReadNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ReadNotify.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.ReadNotify();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.readerUserId = reader.int64();
                                break;
                            }
                        case 3: {
                                message.readSeq = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a ReadNotify message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.ReadNotify} ReadNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ReadNotify.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a ReadNotify message.
                 * @function verify
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ReadNotify.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.readerUserId != null && Object.hasOwnProperty.call(message, "readerUserId"))
                        if (!$util.isInteger(message.readerUserId) && !(message.readerUserId && $util.isInteger(message.readerUserId.low) && $util.isInteger(message.readerUserId.high)))
                            return "readerUserId: integer|Long expected";
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (!$util.isInteger(message.readSeq) && !(message.readSeq && $util.isInteger(message.readSeq.low) && $util.isInteger(message.readSeq.high)))
                            return "readSeq: integer|Long expected";
                    return null;
                };

                /**
                 * Creates a ReadNotify message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.ReadNotify} ReadNotify
                 */
                ReadNotify.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.ReadNotify)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.ReadNotify: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.ReadNotify();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    if (object.readerUserId != null)
                        if ($util.Long)
                            message.readerUserId = $util.Long.fromValue(object.readerUserId, false);
                        else if (typeof object.readerUserId === "string")
                            message.readerUserId = parseInt(object.readerUserId, 10);
                        else if (typeof object.readerUserId === "number")
                            message.readerUserId = object.readerUserId;
                        else if (typeof object.readerUserId === "object")
                            message.readerUserId = new $util.LongBits(object.readerUserId.low >>> 0, object.readerUserId.high >>> 0).toNumber();
                    if (object.readSeq != null)
                        if ($util.Long)
                            message.readSeq = $util.Long.fromValue(object.readSeq, false);
                        else if (typeof object.readSeq === "string")
                            message.readSeq = parseInt(object.readSeq, 10);
                        else if (typeof object.readSeq === "number")
                            message.readSeq = object.readSeq;
                        else if (typeof object.readSeq === "object")
                            message.readSeq = new $util.LongBits(object.readSeq.low >>> 0, object.readSeq.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from a ReadNotify message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {im.body.v1.ReadNotify} message ReadNotify
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ReadNotify.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.readerUserId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.readerUserId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.readSeq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.readSeq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.readerUserId != null && Object.hasOwnProperty.call(message, "readerUserId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.readerUserId = typeof message.readerUserId === "number" ? BigInt(message.readerUserId) : $util.Long.fromBits(message.readerUserId.low >>> 0, message.readerUserId.high >>> 0, false).toBigInt();
                        else if (typeof message.readerUserId === "number")
                            object.readerUserId = options.longs === String ? String(message.readerUserId) : message.readerUserId;
                        else
                            object.readerUserId = options.longs === String ? $util.Long.prototype.toString.call(message.readerUserId) : options.longs === Number ? new $util.LongBits(message.readerUserId.low >>> 0, message.readerUserId.high >>> 0).toNumber() : message.readerUserId;
                    if (message.readSeq != null && Object.hasOwnProperty.call(message, "readSeq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.readSeq = typeof message.readSeq === "number" ? BigInt(message.readSeq) : $util.Long.fromBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0, false).toBigInt();
                        else if (typeof message.readSeq === "number")
                            object.readSeq = options.longs === String ? String(message.readSeq) : message.readSeq;
                        else
                            object.readSeq = options.longs === String ? $util.Long.prototype.toString.call(message.readSeq) : options.longs === Number ? new $util.LongBits(message.readSeq.low >>> 0, message.readSeq.high >>> 0).toNumber() : message.readSeq;
                    return object;
                };

                /**
                 * Converts this ReadNotify to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.ReadNotify
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ReadNotify.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ReadNotify
                 * @function getTypeUrl
                 * @memberof im.body.v1.ReadNotify
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ReadNotify.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.ReadNotify";
                };

                return ReadNotify;
            })();

            v1.RevokeNotify = (function() {

                /**
                 * Properties of a RevokeNotify.
                 * @memberof im.body.v1
                 * @interface IRevokeNotify
                 * @property {number|Long|null} [convId] RevokeNotify convId
                 * @property {number|Long|null} [seq] RevokeNotify seq
                 * @property {number|Long|null} [serverMsgId] RevokeNotify serverMsgId
                 * @property {im.common.v1.RevokeReason|null} [reason] RevokeNotify reason
                 * @property {number|Long|null} [operatorUserId] RevokeNotify operatorUserId
                 */

                /**
                 * Constructs a new RevokeNotify.
                 * @memberof im.body.v1
                 * @classdesc Represents a RevokeNotify.
                 * @implements IRevokeNotify
                 * @constructor
                 * @param {im.body.v1.IRevokeNotify=} [properties] Properties to set
                 */
                function RevokeNotify(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * RevokeNotify convId.
                 * @member {number|Long} convId
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 */
                RevokeNotify.prototype.convId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * RevokeNotify seq.
                 * @member {number|Long} seq
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 */
                RevokeNotify.prototype.seq = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * RevokeNotify serverMsgId.
                 * @member {number|Long} serverMsgId
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 */
                RevokeNotify.prototype.serverMsgId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * RevokeNotify reason.
                 * @member {im.common.v1.RevokeReason} reason
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 */
                RevokeNotify.prototype.reason = 0;

                /**
                 * RevokeNotify operatorUserId.
                 * @member {number|Long} operatorUserId
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 */
                RevokeNotify.prototype.operatorUserId = $util.Long ? $util.Long.fromBits(0,0,false) : 0;

                /**
                 * Creates a new RevokeNotify instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {im.body.v1.IRevokeNotify=} [properties] Properties to set
                 * @returns {im.body.v1.RevokeNotify} RevokeNotify instance
                 */
                RevokeNotify.create = function create(properties) {
                    return new RevokeNotify(properties);
                };

                /**
                 * Encodes the specified RevokeNotify message. Does not implicitly {@link im.body.v1.RevokeNotify.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {im.body.v1.IRevokeNotify} message RevokeNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                RevokeNotify.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int64(message.convId);
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        writer.uint32(/* id 2, wireType 0 =*/16).int64(message.seq);
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        writer.uint32(/* id 3, wireType 0 =*/24).int64(message.serverMsgId);
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        writer.uint32(/* id 4, wireType 0 =*/32).int32(message.reason);
                    if (message.operatorUserId != null && Object.hasOwnProperty.call(message, "operatorUserId"))
                        writer.uint32(/* id 5, wireType 0 =*/40).int64(message.operatorUserId);
                    return writer;
                };

                /**
                 * Encodes the specified RevokeNotify message, length delimited. Does not implicitly {@link im.body.v1.RevokeNotify.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {im.body.v1.IRevokeNotify} message RevokeNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                RevokeNotify.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a RevokeNotify message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.RevokeNotify} RevokeNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                RevokeNotify.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.RevokeNotify();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.convId = reader.int64();
                                break;
                            }
                        case 2: {
                                message.seq = reader.int64();
                                break;
                            }
                        case 3: {
                                message.serverMsgId = reader.int64();
                                break;
                            }
                        case 4: {
                                message.reason = reader.int32();
                                break;
                            }
                        case 5: {
                                message.operatorUserId = reader.int64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a RevokeNotify message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.RevokeNotify} RevokeNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                RevokeNotify.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a RevokeNotify message.
                 * @function verify
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                RevokeNotify.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (!$util.isInteger(message.convId) && !(message.convId && $util.isInteger(message.convId.low) && $util.isInteger(message.convId.high)))
                            return "convId: integer|Long expected";
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (!$util.isInteger(message.seq) && !(message.seq && $util.isInteger(message.seq.low) && $util.isInteger(message.seq.high)))
                            return "seq: integer|Long expected";
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (!$util.isInteger(message.serverMsgId) && !(message.serverMsgId && $util.isInteger(message.serverMsgId.low) && $util.isInteger(message.serverMsgId.high)))
                            return "serverMsgId: integer|Long expected";
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        switch (message.reason) {
                        default:
                            return "reason: enum value expected";
                        case 0:
                        case 1:
                        case 2:
                        case 3:
                            break;
                        }
                    if (message.operatorUserId != null && Object.hasOwnProperty.call(message, "operatorUserId"))
                        if (!$util.isInteger(message.operatorUserId) && !(message.operatorUserId && $util.isInteger(message.operatorUserId.low) && $util.isInteger(message.operatorUserId.high)))
                            return "operatorUserId: integer|Long expected";
                    return null;
                };

                /**
                 * Creates a RevokeNotify message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.RevokeNotify} RevokeNotify
                 */
                RevokeNotify.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.RevokeNotify)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.RevokeNotify: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.RevokeNotify();
                    if (object.convId != null)
                        if ($util.Long)
                            message.convId = $util.Long.fromValue(object.convId, false);
                        else if (typeof object.convId === "string")
                            message.convId = parseInt(object.convId, 10);
                        else if (typeof object.convId === "number")
                            message.convId = object.convId;
                        else if (typeof object.convId === "object")
                            message.convId = new $util.LongBits(object.convId.low >>> 0, object.convId.high >>> 0).toNumber();
                    if (object.seq != null)
                        if ($util.Long)
                            message.seq = $util.Long.fromValue(object.seq, false);
                        else if (typeof object.seq === "string")
                            message.seq = parseInt(object.seq, 10);
                        else if (typeof object.seq === "number")
                            message.seq = object.seq;
                        else if (typeof object.seq === "object")
                            message.seq = new $util.LongBits(object.seq.low >>> 0, object.seq.high >>> 0).toNumber();
                    if (object.serverMsgId != null)
                        if ($util.Long)
                            message.serverMsgId = $util.Long.fromValue(object.serverMsgId, false);
                        else if (typeof object.serverMsgId === "string")
                            message.serverMsgId = parseInt(object.serverMsgId, 10);
                        else if (typeof object.serverMsgId === "number")
                            message.serverMsgId = object.serverMsgId;
                        else if (typeof object.serverMsgId === "object")
                            message.serverMsgId = new $util.LongBits(object.serverMsgId.low >>> 0, object.serverMsgId.high >>> 0).toNumber();
                    switch (object.reason) {
                    default:
                        if (typeof object.reason === "number") {
                            message.reason = object.reason;
                            break;
                        }
                        break;
                    case "REVOKE_REASON_UNSPECIFIED":
                    case 0:
                        message.reason = 0;
                        break;
                    case "BY_SENDER":
                    case 1:
                        message.reason = 1;
                        break;
                    case "BY_MODERATION":
                    case 2:
                        message.reason = 2;
                        break;
                    case "BY_ADMIN":
                    case 3:
                        message.reason = 3;
                        break;
                    }
                    if (object.operatorUserId != null)
                        if ($util.Long)
                            message.operatorUserId = $util.Long.fromValue(object.operatorUserId, false);
                        else if (typeof object.operatorUserId === "string")
                            message.operatorUserId = parseInt(object.operatorUserId, 10);
                        else if (typeof object.operatorUserId === "number")
                            message.operatorUserId = object.operatorUserId;
                        else if (typeof object.operatorUserId === "object")
                            message.operatorUserId = new $util.LongBits(object.operatorUserId.low >>> 0, object.operatorUserId.high >>> 0).toNumber();
                    return message;
                };

                /**
                 * Creates a plain object from a RevokeNotify message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {im.body.v1.RevokeNotify} message RevokeNotify
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                RevokeNotify.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.convId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.convId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.seq = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.seq = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.serverMsgId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.serverMsgId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.reason = options.enums === String ? "REVOKE_REASON_UNSPECIFIED" : 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, false);
                            object.operatorUserId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.operatorUserId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.convId != null && Object.hasOwnProperty.call(message, "convId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.convId = typeof message.convId === "number" ? BigInt(message.convId) : $util.Long.fromBits(message.convId.low >>> 0, message.convId.high >>> 0, false).toBigInt();
                        else if (typeof message.convId === "number")
                            object.convId = options.longs === String ? String(message.convId) : message.convId;
                        else
                            object.convId = options.longs === String ? $util.Long.prototype.toString.call(message.convId) : options.longs === Number ? new $util.LongBits(message.convId.low >>> 0, message.convId.high >>> 0).toNumber() : message.convId;
                    if (message.seq != null && Object.hasOwnProperty.call(message, "seq"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.seq = typeof message.seq === "number" ? BigInt(message.seq) : $util.Long.fromBits(message.seq.low >>> 0, message.seq.high >>> 0, false).toBigInt();
                        else if (typeof message.seq === "number")
                            object.seq = options.longs === String ? String(message.seq) : message.seq;
                        else
                            object.seq = options.longs === String ? $util.Long.prototype.toString.call(message.seq) : options.longs === Number ? new $util.LongBits(message.seq.low >>> 0, message.seq.high >>> 0).toNumber() : message.seq;
                    if (message.serverMsgId != null && Object.hasOwnProperty.call(message, "serverMsgId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.serverMsgId = typeof message.serverMsgId === "number" ? BigInt(message.serverMsgId) : $util.Long.fromBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0, false).toBigInt();
                        else if (typeof message.serverMsgId === "number")
                            object.serverMsgId = options.longs === String ? String(message.serverMsgId) : message.serverMsgId;
                        else
                            object.serverMsgId = options.longs === String ? $util.Long.prototype.toString.call(message.serverMsgId) : options.longs === Number ? new $util.LongBits(message.serverMsgId.low >>> 0, message.serverMsgId.high >>> 0).toNumber() : message.serverMsgId;
                    if (message.reason != null && Object.hasOwnProperty.call(message, "reason"))
                        object.reason = options.enums === String ? $root.im.common.v1.RevokeReason[message.reason] === undefined ? message.reason : $root.im.common.v1.RevokeReason[message.reason] : message.reason;
                    if (message.operatorUserId != null && Object.hasOwnProperty.call(message, "operatorUserId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.operatorUserId = typeof message.operatorUserId === "number" ? BigInt(message.operatorUserId) : $util.Long.fromBits(message.operatorUserId.low >>> 0, message.operatorUserId.high >>> 0, false).toBigInt();
                        else if (typeof message.operatorUserId === "number")
                            object.operatorUserId = options.longs === String ? String(message.operatorUserId) : message.operatorUserId;
                        else
                            object.operatorUserId = options.longs === String ? $util.Long.prototype.toString.call(message.operatorUserId) : options.longs === Number ? new $util.LongBits(message.operatorUserId.low >>> 0, message.operatorUserId.high >>> 0).toNumber() : message.operatorUserId;
                    return object;
                };

                /**
                 * Converts this RevokeNotify to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.RevokeNotify
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                RevokeNotify.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for RevokeNotify
                 * @function getTypeUrl
                 * @memberof im.body.v1.RevokeNotify
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                RevokeNotify.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.RevokeNotify";
                };

                return RevokeNotify;
            })();

            v1.ConvNotify = (function() {

                /**
                 * Properties of a ConvNotify.
                 * @memberof im.body.v1
                 * @interface IConvNotify
                 * @property {im.body.v1.IConvInfo|null} [conv] ConvNotify conv
                 * @property {string|null} [changeType] ConvNotify changeType
                 */

                /**
                 * Constructs a new ConvNotify.
                 * @memberof im.body.v1
                 * @classdesc Represents a ConvNotify.
                 * @implements IConvNotify
                 * @constructor
                 * @param {im.body.v1.IConvNotify=} [properties] Properties to set
                 */
                function ConvNotify(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ConvNotify conv.
                 * @member {im.body.v1.IConvInfo|null|undefined} conv
                 * @memberof im.body.v1.ConvNotify
                 * @instance
                 */
                ConvNotify.prototype.conv = null;

                /**
                 * ConvNotify changeType.
                 * @member {string} changeType
                 * @memberof im.body.v1.ConvNotify
                 * @instance
                 */
                ConvNotify.prototype.changeType = "";

                /**
                 * Creates a new ConvNotify instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {im.body.v1.IConvNotify=} [properties] Properties to set
                 * @returns {im.body.v1.ConvNotify} ConvNotify instance
                 */
                ConvNotify.create = function create(properties) {
                    return new ConvNotify(properties);
                };

                /**
                 * Encodes the specified ConvNotify message. Does not implicitly {@link im.body.v1.ConvNotify.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {im.body.v1.IConvNotify} message ConvNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ConvNotify.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.conv != null && Object.hasOwnProperty.call(message, "conv"))
                        $root.im.body.v1.ConvInfo.encode(message.conv, writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                    if (message.changeType != null && Object.hasOwnProperty.call(message, "changeType"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.changeType);
                    return writer;
                };

                /**
                 * Encodes the specified ConvNotify message, length delimited. Does not implicitly {@link im.body.v1.ConvNotify.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {im.body.v1.IConvNotify} message ConvNotify message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ConvNotify.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a ConvNotify message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.ConvNotify} ConvNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ConvNotify.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.ConvNotify();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.conv = $root.im.body.v1.ConvInfo.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 2: {
                                message.changeType = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a ConvNotify message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.ConvNotify} ConvNotify
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ConvNotify.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a ConvNotify message.
                 * @function verify
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ConvNotify.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.conv != null && Object.hasOwnProperty.call(message, "conv")) {
                        let error = $root.im.body.v1.ConvInfo.verify(message.conv, long + 1);
                        if (error)
                            return "conv." + error;
                    }
                    if (message.changeType != null && Object.hasOwnProperty.call(message, "changeType"))
                        if (!$util.isString(message.changeType))
                            return "changeType: string expected";
                    return null;
                };

                /**
                 * Creates a ConvNotify message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.ConvNotify} ConvNotify
                 */
                ConvNotify.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.ConvNotify)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.ConvNotify: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.ConvNotify();
                    if (object.conv != null) {
                        if (!$util.isObject(object.conv))
                            throw TypeError(".im.body.v1.ConvNotify.conv: object expected");
                        message.conv = $root.im.body.v1.ConvInfo.fromObject(object.conv, long + 1);
                    }
                    if (object.changeType != null)
                        message.changeType = String(object.changeType);
                    return message;
                };

                /**
                 * Creates a plain object from a ConvNotify message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {im.body.v1.ConvNotify} message ConvNotify
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ConvNotify.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.conv = null;
                        object.changeType = "";
                    }
                    if (message.conv != null && Object.hasOwnProperty.call(message, "conv"))
                        object.conv = $root.im.body.v1.ConvInfo.toObject(message.conv, options, q + 1);
                    if (message.changeType != null && Object.hasOwnProperty.call(message, "changeType"))
                        object.changeType = message.changeType;
                    return object;
                };

                /**
                 * Converts this ConvNotify to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.ConvNotify
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ConvNotify.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ConvNotify
                 * @function getTypeUrl
                 * @memberof im.body.v1.ConvNotify
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ConvNotify.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.ConvNotify";
                };

                return ConvNotify;
            })();

            v1.ErrorBody = (function() {

                /**
                 * Properties of an ErrorBody.
                 * @memberof im.body.v1
                 * @interface IErrorBody
                 * @property {number|null} [code] ErrorBody code
                 * @property {string|null} [message] ErrorBody message
                 * @property {number|Long|null} [reqId] ErrorBody reqId
                 */

                /**
                 * Constructs a new ErrorBody.
                 * @memberof im.body.v1
                 * @classdesc Represents an ErrorBody.
                 * @implements IErrorBody
                 * @constructor
                 * @param {im.body.v1.IErrorBody=} [properties] Properties to set
                 */
                function ErrorBody(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ErrorBody code.
                 * @member {number} code
                 * @memberof im.body.v1.ErrorBody
                 * @instance
                 */
                ErrorBody.prototype.code = 0;

                /**
                 * ErrorBody message.
                 * @member {string} message
                 * @memberof im.body.v1.ErrorBody
                 * @instance
                 */
                ErrorBody.prototype.message = "";

                /**
                 * ErrorBody reqId.
                 * @member {number|Long} reqId
                 * @memberof im.body.v1.ErrorBody
                 * @instance
                 */
                ErrorBody.prototype.reqId = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

                /**
                 * Creates a new ErrorBody instance using the specified properties.
                 * @function create
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {im.body.v1.IErrorBody=} [properties] Properties to set
                 * @returns {im.body.v1.ErrorBody} ErrorBody instance
                 */
                ErrorBody.create = function create(properties) {
                    return new ErrorBody(properties);
                };

                /**
                 * Encodes the specified ErrorBody message. Does not implicitly {@link im.body.v1.ErrorBody.verify|verify} messages.
                 * @function encode
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {im.body.v1.IErrorBody} message ErrorBody message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ErrorBody.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        writer.uint32(/* id 1, wireType 0 =*/8).int32(message.code);
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.message);
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        writer.uint32(/* id 3, wireType 0 =*/24).uint64(message.reqId);
                    return writer;
                };

                /**
                 * Encodes the specified ErrorBody message, length delimited. Does not implicitly {@link im.body.v1.ErrorBody.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {im.body.v1.IErrorBody} message ErrorBody message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ErrorBody.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes an ErrorBody message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.body.v1.ErrorBody} ErrorBody
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ErrorBody.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.body.v1.ErrorBody();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.code = reader.int32();
                                break;
                            }
                        case 2: {
                                message.message = reader.string();
                                break;
                            }
                        case 3: {
                                message.reqId = reader.uint64();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes an ErrorBody message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.body.v1.ErrorBody} ErrorBody
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ErrorBody.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies an ErrorBody message.
                 * @function verify
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ErrorBody.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        if (!$util.isInteger(message.code))
                            return "code: integer expected";
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        if (!$util.isString(message.message))
                            return "message: string expected";
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        if (!$util.isInteger(message.reqId) && !(message.reqId && $util.isInteger(message.reqId.low) && $util.isInteger(message.reqId.high)))
                            return "reqId: integer|Long expected";
                    return null;
                };

                /**
                 * Creates an ErrorBody message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.body.v1.ErrorBody} ErrorBody
                 */
                ErrorBody.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.body.v1.ErrorBody)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.body.v1.ErrorBody: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.body.v1.ErrorBody();
                    if (object.code != null)
                        message.code = object.code | 0;
                    if (object.message != null)
                        message.message = String(object.message);
                    if (object.reqId != null)
                        if ($util.Long)
                            message.reqId = $util.Long.fromValue(object.reqId, true);
                        else if (typeof object.reqId === "string")
                            message.reqId = parseInt(object.reqId, 10);
                        else if (typeof object.reqId === "number")
                            message.reqId = object.reqId;
                        else if (typeof object.reqId === "object")
                            message.reqId = new $util.LongBits(object.reqId.low >>> 0, object.reqId.high >>> 0).toNumber(true);
                    return message;
                };

                /**
                 * Creates a plain object from an ErrorBody message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {im.body.v1.ErrorBody} message ErrorBody
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ErrorBody.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.code = 0;
                        object.message = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, true);
                            object.reqId = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.reqId = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                    }
                    if (message.code != null && Object.hasOwnProperty.call(message, "code"))
                        object.code = message.code;
                    if (message.message != null && Object.hasOwnProperty.call(message, "message"))
                        object.message = message.message;
                    if (message.reqId != null && Object.hasOwnProperty.call(message, "reqId"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.reqId = typeof message.reqId === "number" ? BigInt(message.reqId) : $util.Long.fromBits(message.reqId.low >>> 0, message.reqId.high >>> 0, true).toBigInt();
                        else if (typeof message.reqId === "number")
                            object.reqId = options.longs === String ? String(message.reqId) : message.reqId;
                        else
                            object.reqId = options.longs === String ? $util.Long.prototype.toString.call(message.reqId) : options.longs === Number ? new $util.LongBits(message.reqId.low >>> 0, message.reqId.high >>> 0).toNumber(true) : message.reqId;
                    return object;
                };

                /**
                 * Converts this ErrorBody to JSON.
                 * @function toJSON
                 * @memberof im.body.v1.ErrorBody
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ErrorBody.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ErrorBody
                 * @function getTypeUrl
                 * @memberof im.body.v1.ErrorBody
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ErrorBody.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.body.v1.ErrorBody";
                };

                return ErrorBody;
            })();

            return v1;
        })();

        return body;
    })();

    im.common = (function() {

        /**
         * Namespace common.
         * @memberof im
         * @namespace
         */
        const common = {};

        common.v1 = (function() {

            /**
             * Namespace v1.
             * @memberof im.common
             * @namespace
             */
            const v1 = {};

            /**
             * Platform enum.
             * @name im.common.v1.Platform
             * @enum {number}
             * @property {number} PLATFORM_UNSPECIFIED=0 PLATFORM_UNSPECIFIED value
             * @property {number} IOS=1 IOS value
             * @property {number} ANDROID=2 ANDROID value
             * @property {number} WINDOWS=3 WINDOWS value
             * @property {number} MACOS=4 MACOS value
             * @property {number} WEB=5 WEB value
             * @property {number} MINI_PROGRAM=6 MINI_PROGRAM value
             */
            v1.Platform = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "PLATFORM_UNSPECIFIED"] = 0;
                values[valuesById[1] = "IOS"] = 1;
                values[valuesById[2] = "ANDROID"] = 2;
                values[valuesById[3] = "WINDOWS"] = 3;
                values[valuesById[4] = "MACOS"] = 4;
                values[valuesById[5] = "WEB"] = 5;
                values[valuesById[6] = "MINI_PROGRAM"] = 6;
                return values;
            })();

            /**
             * ConvType enum.
             * @name im.common.v1.ConvType
             * @enum {number}
             * @property {number} CONV_TYPE_UNSPECIFIED=0 CONV_TYPE_UNSPECIFIED value
             * @property {number} C2C=1 C2C value
             * @property {number} GROUP=2 GROUP value
             * @property {number} CS_SESSION=3 CS_SESSION value
             * @property {number} SYSTEM=4 SYSTEM value
             */
            v1.ConvType = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "CONV_TYPE_UNSPECIFIED"] = 0;
                values[valuesById[1] = "C2C"] = 1;
                values[valuesById[2] = "GROUP"] = 2;
                values[valuesById[3] = "CS_SESSION"] = 3;
                values[valuesById[4] = "SYSTEM"] = 4;
                return values;
            })();

            /**
             * UserType enum.
             * @name im.common.v1.UserType
             * @enum {number}
             * @property {number} USER_TYPE_UNSPECIFIED=0 USER_TYPE_UNSPECIFIED value
             * @property {number} MEMBER=1 MEMBER value
             * @property {number} AGENT=2 AGENT value
             * @property {number} VISITOR=3 VISITOR value
             */
            v1.UserType = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "USER_TYPE_UNSPECIFIED"] = 0;
                values[valuesById[1] = "MEMBER"] = 1;
                values[valuesById[2] = "AGENT"] = 2;
                values[valuesById[3] = "VISITOR"] = 3;
                return values;
            })();

            /**
             * VerifiedType enum.
             * @name im.common.v1.VerifiedType
             * @enum {number}
             * @property {number} VERIFIED_NONE=0 VERIFIED_NONE value
             * @property {number} PERSONAL=1 PERSONAL value
             * @property {number} ENTERPRISE=2 ENTERPRISE value
             * @property {number} OFFICIAL_STAFF=3 OFFICIAL_STAFF value
             */
            v1.VerifiedType = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "VERIFIED_NONE"] = 0;
                values[valuesById[1] = "PERSONAL"] = 1;
                values[valuesById[2] = "ENTERPRISE"] = 2;
                values[valuesById[3] = "OFFICIAL_STAFF"] = 3;
                return values;
            })();

            /**
             * MsgStatus enum.
             * @name im.common.v1.MsgStatus
             * @enum {number}
             * @property {number} MSG_STATUS_UNSPECIFIED=0 MSG_STATUS_UNSPECIFIED value
             * @property {number} NORMAL=1 NORMAL value
             * @property {number} REVOKED=2 REVOKED value
             */
            v1.MsgStatus = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "MSG_STATUS_UNSPECIFIED"] = 0;
                values[valuesById[1] = "NORMAL"] = 1;
                values[valuesById[2] = "REVOKED"] = 2;
                return values;
            })();

            /**
             * RevokeReason enum.
             * @name im.common.v1.RevokeReason
             * @enum {number}
             * @property {number} REVOKE_REASON_UNSPECIFIED=0 REVOKE_REASON_UNSPECIFIED value
             * @property {number} BY_SENDER=1 BY_SENDER value
             * @property {number} BY_MODERATION=2 BY_MODERATION value
             * @property {number} BY_ADMIN=3 BY_ADMIN value
             */
            v1.RevokeReason = (function() {
                const valuesById = {}, values = Object.create(valuesById);
                values[valuesById[0] = "REVOKE_REASON_UNSPECIFIED"] = 0;
                values[valuesById[1] = "BY_SENDER"] = 1;
                values[valuesById[2] = "BY_MODERATION"] = 2;
                values[valuesById[3] = "BY_ADMIN"] = 3;
                return values;
            })();

            v1.MsgContent = (function() {

                /**
                 * Properties of a MsgContent.
                 * @memberof im.common.v1
                 * @interface IMsgContent
                 * @property {im.common.v1.ITextContent|null} [text] MsgContent text
                 * @property {im.common.v1.IImageContent|null} [image] MsgContent image
                 * @property {im.common.v1.IVoiceContent|null} [voice] MsgContent voice
                 * @property {im.common.v1.IFileContent|null} [file] MsgContent file
                 * @property {im.common.v1.INotificationContent|null} [notification] MsgContent notification
                 * @property {im.common.v1.ICustomContent|null} [custom] MsgContent custom
                 */

                /**
                 * Constructs a new MsgContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a MsgContent.
                 * @implements IMsgContent
                 * @constructor
                 * @param {im.common.v1.IMsgContent=} [properties] Properties to set
                 */
                function MsgContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * MsgContent text.
                 * @member {im.common.v1.ITextContent|null|undefined} text
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.text = null;

                /**
                 * MsgContent image.
                 * @member {im.common.v1.IImageContent|null|undefined} image
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.image = null;

                /**
                 * MsgContent voice.
                 * @member {im.common.v1.IVoiceContent|null|undefined} voice
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.voice = null;

                /**
                 * MsgContent file.
                 * @member {im.common.v1.IFileContent|null|undefined} file
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.file = null;

                /**
                 * MsgContent notification.
                 * @member {im.common.v1.INotificationContent|null|undefined} notification
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.notification = null;

                /**
                 * MsgContent custom.
                 * @member {im.common.v1.ICustomContent|null|undefined} custom
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                MsgContent.prototype.custom = null;

                // OneOf field names bound to virtual getters and setters
                let $oneOfFields;

                /**
                 * MsgContent content.
                 * @member {"text"|"image"|"voice"|"file"|"notification"|"custom"|undefined} content
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 */
                Object.defineProperty(MsgContent.prototype, "content", {
                    get: $util.oneOfGetter($oneOfFields = ["text", "image", "voice", "file", "notification", "custom"]),
                    set: $util.oneOfSetter($oneOfFields)
                });

                /**
                 * Creates a new MsgContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {im.common.v1.IMsgContent=} [properties] Properties to set
                 * @returns {im.common.v1.MsgContent} MsgContent instance
                 */
                MsgContent.create = function create(properties) {
                    return new MsgContent(properties);
                };

                /**
                 * Encodes the specified MsgContent message. Does not implicitly {@link im.common.v1.MsgContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {im.common.v1.IMsgContent} message MsgContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.text != null && Object.hasOwnProperty.call(message, "text"))
                        $root.im.common.v1.TextContent.encode(message.text, writer.uint32(/* id 1, wireType 2 =*/10).fork(), q + 1).ldelim();
                    if (message.image != null && Object.hasOwnProperty.call(message, "image"))
                        $root.im.common.v1.ImageContent.encode(message.image, writer.uint32(/* id 2, wireType 2 =*/18).fork(), q + 1).ldelim();
                    if (message.voice != null && Object.hasOwnProperty.call(message, "voice"))
                        $root.im.common.v1.VoiceContent.encode(message.voice, writer.uint32(/* id 3, wireType 2 =*/26).fork(), q + 1).ldelim();
                    if (message.file != null && Object.hasOwnProperty.call(message, "file"))
                        $root.im.common.v1.FileContent.encode(message.file, writer.uint32(/* id 4, wireType 2 =*/34).fork(), q + 1).ldelim();
                    if (message.notification != null && Object.hasOwnProperty.call(message, "notification"))
                        $root.im.common.v1.NotificationContent.encode(message.notification, writer.uint32(/* id 10, wireType 2 =*/82).fork(), q + 1).ldelim();
                    if (message.custom != null && Object.hasOwnProperty.call(message, "custom"))
                        $root.im.common.v1.CustomContent.encode(message.custom, writer.uint32(/* id 20, wireType 2 =*/162).fork(), q + 1).ldelim();
                    return writer;
                };

                /**
                 * Encodes the specified MsgContent message, length delimited. Does not implicitly {@link im.common.v1.MsgContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {im.common.v1.IMsgContent} message MsgContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                MsgContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a MsgContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.MsgContent} MsgContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.MsgContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.text = $root.im.common.v1.TextContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 2: {
                                message.image = $root.im.common.v1.ImageContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 3: {
                                message.voice = $root.im.common.v1.VoiceContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 4: {
                                message.file = $root.im.common.v1.FileContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 10: {
                                message.notification = $root.im.common.v1.NotificationContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        case 20: {
                                message.custom = $root.im.common.v1.CustomContent.decode(reader, reader.uint32(), undefined, long + 1);
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a MsgContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.MsgContent} MsgContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                MsgContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a MsgContent message.
                 * @function verify
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                MsgContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    let properties = {};
                    if (message.text != null && Object.hasOwnProperty.call(message, "text")) {
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.TextContent.verify(message.text, long + 1);
                            if (error)
                                return "text." + error;
                        }
                    }
                    if (message.image != null && Object.hasOwnProperty.call(message, "image")) {
                        if (properties.content === 1)
                            return "content: multiple values";
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.ImageContent.verify(message.image, long + 1);
                            if (error)
                                return "image." + error;
                        }
                    }
                    if (message.voice != null && Object.hasOwnProperty.call(message, "voice")) {
                        if (properties.content === 1)
                            return "content: multiple values";
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.VoiceContent.verify(message.voice, long + 1);
                            if (error)
                                return "voice." + error;
                        }
                    }
                    if (message.file != null && Object.hasOwnProperty.call(message, "file")) {
                        if (properties.content === 1)
                            return "content: multiple values";
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.FileContent.verify(message.file, long + 1);
                            if (error)
                                return "file." + error;
                        }
                    }
                    if (message.notification != null && Object.hasOwnProperty.call(message, "notification")) {
                        if (properties.content === 1)
                            return "content: multiple values";
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.NotificationContent.verify(message.notification, long + 1);
                            if (error)
                                return "notification." + error;
                        }
                    }
                    if (message.custom != null && Object.hasOwnProperty.call(message, "custom")) {
                        if (properties.content === 1)
                            return "content: multiple values";
                        properties.content = 1;
                        {
                            let error = $root.im.common.v1.CustomContent.verify(message.custom, long + 1);
                            if (error)
                                return "custom." + error;
                        }
                    }
                    return null;
                };

                /**
                 * Creates a MsgContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.MsgContent} MsgContent
                 */
                MsgContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.MsgContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.MsgContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.MsgContent();
                    if (object.text != null) {
                        if (!$util.isObject(object.text))
                            throw TypeError(".im.common.v1.MsgContent.text: object expected");
                        message.text = $root.im.common.v1.TextContent.fromObject(object.text, long + 1);
                    }
                    if (object.image != null) {
                        if (!$util.isObject(object.image))
                            throw TypeError(".im.common.v1.MsgContent.image: object expected");
                        message.image = $root.im.common.v1.ImageContent.fromObject(object.image, long + 1);
                    }
                    if (object.voice != null) {
                        if (!$util.isObject(object.voice))
                            throw TypeError(".im.common.v1.MsgContent.voice: object expected");
                        message.voice = $root.im.common.v1.VoiceContent.fromObject(object.voice, long + 1);
                    }
                    if (object.file != null) {
                        if (!$util.isObject(object.file))
                            throw TypeError(".im.common.v1.MsgContent.file: object expected");
                        message.file = $root.im.common.v1.FileContent.fromObject(object.file, long + 1);
                    }
                    if (object.notification != null) {
                        if (!$util.isObject(object.notification))
                            throw TypeError(".im.common.v1.MsgContent.notification: object expected");
                        message.notification = $root.im.common.v1.NotificationContent.fromObject(object.notification, long + 1);
                    }
                    if (object.custom != null) {
                        if (!$util.isObject(object.custom))
                            throw TypeError(".im.common.v1.MsgContent.custom: object expected");
                        message.custom = $root.im.common.v1.CustomContent.fromObject(object.custom, long + 1);
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a MsgContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {im.common.v1.MsgContent} message MsgContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                MsgContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (message.text != null && Object.hasOwnProperty.call(message, "text")) {
                        object.text = $root.im.common.v1.TextContent.toObject(message.text, options, q + 1);
                        if (options.oneofs)
                            object.content = "text";
                    }
                    if (message.image != null && Object.hasOwnProperty.call(message, "image")) {
                        object.image = $root.im.common.v1.ImageContent.toObject(message.image, options, q + 1);
                        if (options.oneofs)
                            object.content = "image";
                    }
                    if (message.voice != null && Object.hasOwnProperty.call(message, "voice")) {
                        object.voice = $root.im.common.v1.VoiceContent.toObject(message.voice, options, q + 1);
                        if (options.oneofs)
                            object.content = "voice";
                    }
                    if (message.file != null && Object.hasOwnProperty.call(message, "file")) {
                        object.file = $root.im.common.v1.FileContent.toObject(message.file, options, q + 1);
                        if (options.oneofs)
                            object.content = "file";
                    }
                    if (message.notification != null && Object.hasOwnProperty.call(message, "notification")) {
                        object.notification = $root.im.common.v1.NotificationContent.toObject(message.notification, options, q + 1);
                        if (options.oneofs)
                            object.content = "notification";
                    }
                    if (message.custom != null && Object.hasOwnProperty.call(message, "custom")) {
                        object.custom = $root.im.common.v1.CustomContent.toObject(message.custom, options, q + 1);
                        if (options.oneofs)
                            object.content = "custom";
                    }
                    return object;
                };

                /**
                 * Converts this MsgContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.MsgContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                MsgContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for MsgContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.MsgContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                MsgContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.MsgContent";
                };

                return MsgContent;
            })();

            v1.TextContent = (function() {

                /**
                 * Properties of a TextContent.
                 * @memberof im.common.v1
                 * @interface ITextContent
                 * @property {string|null} [text] TextContent text
                 * @property {Array.<number|Long>|null} [atUserIds] TextContent atUserIds
                 */

                /**
                 * Constructs a new TextContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a TextContent.
                 * @implements ITextContent
                 * @constructor
                 * @param {im.common.v1.ITextContent=} [properties] Properties to set
                 */
                function TextContent(properties) {
                    this.atUserIds = [];
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * TextContent text.
                 * @member {string} text
                 * @memberof im.common.v1.TextContent
                 * @instance
                 */
                TextContent.prototype.text = "";

                /**
                 * TextContent atUserIds.
                 * @member {Array.<number|Long>} atUserIds
                 * @memberof im.common.v1.TextContent
                 * @instance
                 */
                TextContent.prototype.atUserIds = $util.emptyArray;

                /**
                 * Creates a new TextContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {im.common.v1.ITextContent=} [properties] Properties to set
                 * @returns {im.common.v1.TextContent} TextContent instance
                 */
                TextContent.create = function create(properties) {
                    return new TextContent(properties);
                };

                /**
                 * Encodes the specified TextContent message. Does not implicitly {@link im.common.v1.TextContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {im.common.v1.ITextContent} message TextContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                TextContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.text != null && Object.hasOwnProperty.call(message, "text"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.text);
                    if (message.atUserIds != null && message.atUserIds.length) {
                        writer.uint32(/* id 2, wireType 2 =*/18).fork();
                        for (let i = 0; i < message.atUserIds.length; ++i)
                            writer.int64(message.atUserIds[i]);
                        writer.ldelim();
                    }
                    return writer;
                };

                /**
                 * Encodes the specified TextContent message, length delimited. Does not implicitly {@link im.common.v1.TextContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {im.common.v1.ITextContent} message TextContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                TextContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a TextContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.TextContent} TextContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                TextContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.TextContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.text = reader.string();
                                break;
                            }
                        case 2: {
                                if (!(message.atUserIds && message.atUserIds.length))
                                    message.atUserIds = [];
                                if ((tag & 7) === 2) {
                                    let end2 = reader.uint32() + reader.pos;
                                    while (reader.pos < end2)
                                        message.atUserIds.push(reader.int64());
                                } else
                                    message.atUserIds.push(reader.int64());
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a TextContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.TextContent} TextContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                TextContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a TextContent message.
                 * @function verify
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                TextContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.text != null && Object.hasOwnProperty.call(message, "text"))
                        if (!$util.isString(message.text))
                            return "text: string expected";
                    if (message.atUserIds != null && Object.hasOwnProperty.call(message, "atUserIds")) {
                        if (!Array.isArray(message.atUserIds))
                            return "atUserIds: array expected";
                        for (let i = 0; i < message.atUserIds.length; ++i)
                            if (!$util.isInteger(message.atUserIds[i]) && !(message.atUserIds[i] && $util.isInteger(message.atUserIds[i].low) && $util.isInteger(message.atUserIds[i].high)))
                                return "atUserIds: integer|Long[] expected";
                    }
                    return null;
                };

                /**
                 * Creates a TextContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.TextContent} TextContent
                 */
                TextContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.TextContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.TextContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.TextContent();
                    if (object.text != null)
                        message.text = String(object.text);
                    if (object.atUserIds) {
                        if (!Array.isArray(object.atUserIds))
                            throw TypeError(".im.common.v1.TextContent.atUserIds: array expected");
                        message.atUserIds = [];
                        for (let i = 0; i < object.atUserIds.length; ++i)
                            if ($util.Long)
                                message.atUserIds[i] = $util.Long.fromValue(object.atUserIds[i], false);
                            else if (typeof object.atUserIds[i] === "string")
                                message.atUserIds[i] = parseInt(object.atUserIds[i], 10);
                            else if (typeof object.atUserIds[i] === "number")
                                message.atUserIds[i] = object.atUserIds[i];
                            else if (typeof object.atUserIds[i] === "object")
                                message.atUserIds[i] = new $util.LongBits(object.atUserIds[i].low >>> 0, object.atUserIds[i].high >>> 0).toNumber();
                    }
                    return message;
                };

                /**
                 * Creates a plain object from a TextContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {im.common.v1.TextContent} message TextContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                TextContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.arrays || options.defaults)
                        object.atUserIds = [];
                    if (options.defaults)
                        object.text = "";
                    if (message.text != null && Object.hasOwnProperty.call(message, "text"))
                        object.text = message.text;
                    if (message.atUserIds && message.atUserIds.length) {
                        object.atUserIds = [];
                        for (let j = 0; j < message.atUserIds.length; ++j)
                            if (typeof BigInt !== "undefined" && options.longs === BigInt)
                                object.atUserIds[j] = typeof message.atUserIds[j] === "number" ? BigInt(message.atUserIds[j]) : $util.Long.fromBits(message.atUserIds[j].low >>> 0, message.atUserIds[j].high >>> 0, false).toBigInt();
                            else if (typeof message.atUserIds[j] === "number")
                                object.atUserIds[j] = options.longs === String ? String(message.atUserIds[j]) : message.atUserIds[j];
                            else
                                object.atUserIds[j] = options.longs === String ? $util.Long.prototype.toString.call(message.atUserIds[j]) : options.longs === Number ? new $util.LongBits(message.atUserIds[j].low >>> 0, message.atUserIds[j].high >>> 0).toNumber() : message.atUserIds[j];
                    }
                    return object;
                };

                /**
                 * Converts this TextContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.TextContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                TextContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for TextContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.TextContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                TextContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.TextContent";
                };

                return TextContent;
            })();

            v1.ImageContent = (function() {

                /**
                 * Properties of an ImageContent.
                 * @memberof im.common.v1
                 * @interface IImageContent
                 * @property {string|null} [objectKey] ImageContent objectKey
                 * @property {string|null} [thumbKey] ImageContent thumbKey
                 * @property {number|null} [width] ImageContent width
                 * @property {number|null} [height] ImageContent height
                 * @property {number|Long|null} [size] ImageContent size
                 * @property {string|null} [mime] ImageContent mime
                 */

                /**
                 * Constructs a new ImageContent.
                 * @memberof im.common.v1
                 * @classdesc Represents an ImageContent.
                 * @implements IImageContent
                 * @constructor
                 * @param {im.common.v1.IImageContent=} [properties] Properties to set
                 */
                function ImageContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * ImageContent objectKey.
                 * @member {string} objectKey
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.objectKey = "";

                /**
                 * ImageContent thumbKey.
                 * @member {string} thumbKey
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.thumbKey = "";

                /**
                 * ImageContent width.
                 * @member {number} width
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.width = 0;

                /**
                 * ImageContent height.
                 * @member {number} height
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.height = 0;

                /**
                 * ImageContent size.
                 * @member {number|Long} size
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.size = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

                /**
                 * ImageContent mime.
                 * @member {string} mime
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 */
                ImageContent.prototype.mime = "";

                /**
                 * Creates a new ImageContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {im.common.v1.IImageContent=} [properties] Properties to set
                 * @returns {im.common.v1.ImageContent} ImageContent instance
                 */
                ImageContent.create = function create(properties) {
                    return new ImageContent(properties);
                };

                /**
                 * Encodes the specified ImageContent message. Does not implicitly {@link im.common.v1.ImageContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {im.common.v1.IImageContent} message ImageContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ImageContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.objectKey);
                    if (message.thumbKey != null && Object.hasOwnProperty.call(message, "thumbKey"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.thumbKey);
                    if (message.width != null && Object.hasOwnProperty.call(message, "width"))
                        writer.uint32(/* id 3, wireType 0 =*/24).uint32(message.width);
                    if (message.height != null && Object.hasOwnProperty.call(message, "height"))
                        writer.uint32(/* id 4, wireType 0 =*/32).uint32(message.height);
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        writer.uint32(/* id 5, wireType 0 =*/40).uint64(message.size);
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        writer.uint32(/* id 6, wireType 2 =*/50).string(message.mime);
                    return writer;
                };

                /**
                 * Encodes the specified ImageContent message, length delimited. Does not implicitly {@link im.common.v1.ImageContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {im.common.v1.IImageContent} message ImageContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                ImageContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes an ImageContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.ImageContent} ImageContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ImageContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.ImageContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.objectKey = reader.string();
                                break;
                            }
                        case 2: {
                                message.thumbKey = reader.string();
                                break;
                            }
                        case 3: {
                                message.width = reader.uint32();
                                break;
                            }
                        case 4: {
                                message.height = reader.uint32();
                                break;
                            }
                        case 5: {
                                message.size = reader.uint64();
                                break;
                            }
                        case 6: {
                                message.mime = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes an ImageContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.ImageContent} ImageContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                ImageContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies an ImageContent message.
                 * @function verify
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                ImageContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        if (!$util.isString(message.objectKey))
                            return "objectKey: string expected";
                    if (message.thumbKey != null && Object.hasOwnProperty.call(message, "thumbKey"))
                        if (!$util.isString(message.thumbKey))
                            return "thumbKey: string expected";
                    if (message.width != null && Object.hasOwnProperty.call(message, "width"))
                        if (!$util.isInteger(message.width))
                            return "width: integer expected";
                    if (message.height != null && Object.hasOwnProperty.call(message, "height"))
                        if (!$util.isInteger(message.height))
                            return "height: integer expected";
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (!$util.isInteger(message.size) && !(message.size && $util.isInteger(message.size.low) && $util.isInteger(message.size.high)))
                            return "size: integer|Long expected";
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        if (!$util.isString(message.mime))
                            return "mime: string expected";
                    return null;
                };

                /**
                 * Creates an ImageContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.ImageContent} ImageContent
                 */
                ImageContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.ImageContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.ImageContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.ImageContent();
                    if (object.objectKey != null)
                        message.objectKey = String(object.objectKey);
                    if (object.thumbKey != null)
                        message.thumbKey = String(object.thumbKey);
                    if (object.width != null)
                        message.width = object.width >>> 0;
                    if (object.height != null)
                        message.height = object.height >>> 0;
                    if (object.size != null)
                        if ($util.Long)
                            message.size = $util.Long.fromValue(object.size, true);
                        else if (typeof object.size === "string")
                            message.size = parseInt(object.size, 10);
                        else if (typeof object.size === "number")
                            message.size = object.size;
                        else if (typeof object.size === "object")
                            message.size = new $util.LongBits(object.size.low >>> 0, object.size.high >>> 0).toNumber(true);
                    if (object.mime != null)
                        message.mime = String(object.mime);
                    return message;
                };

                /**
                 * Creates a plain object from an ImageContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {im.common.v1.ImageContent} message ImageContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                ImageContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.objectKey = "";
                        object.thumbKey = "";
                        object.width = 0;
                        object.height = 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, true);
                            object.size = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.size = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.mime = "";
                    }
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        object.objectKey = message.objectKey;
                    if (message.thumbKey != null && Object.hasOwnProperty.call(message, "thumbKey"))
                        object.thumbKey = message.thumbKey;
                    if (message.width != null && Object.hasOwnProperty.call(message, "width"))
                        object.width = message.width;
                    if (message.height != null && Object.hasOwnProperty.call(message, "height"))
                        object.height = message.height;
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.size = typeof message.size === "number" ? BigInt(message.size) : $util.Long.fromBits(message.size.low >>> 0, message.size.high >>> 0, true).toBigInt();
                        else if (typeof message.size === "number")
                            object.size = options.longs === String ? String(message.size) : message.size;
                        else
                            object.size = options.longs === String ? $util.Long.prototype.toString.call(message.size) : options.longs === Number ? new $util.LongBits(message.size.low >>> 0, message.size.high >>> 0).toNumber(true) : message.size;
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        object.mime = message.mime;
                    return object;
                };

                /**
                 * Converts this ImageContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.ImageContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                ImageContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for ImageContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.ImageContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                ImageContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.ImageContent";
                };

                return ImageContent;
            })();

            v1.VoiceContent = (function() {

                /**
                 * Properties of a VoiceContent.
                 * @memberof im.common.v1
                 * @interface IVoiceContent
                 * @property {string|null} [objectKey] VoiceContent objectKey
                 * @property {number|null} [durationMs] VoiceContent durationMs
                 * @property {number|Long|null} [size] VoiceContent size
                 * @property {string|null} [codec] VoiceContent codec
                 */

                /**
                 * Constructs a new VoiceContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a VoiceContent.
                 * @implements IVoiceContent
                 * @constructor
                 * @param {im.common.v1.IVoiceContent=} [properties] Properties to set
                 */
                function VoiceContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * VoiceContent objectKey.
                 * @member {string} objectKey
                 * @memberof im.common.v1.VoiceContent
                 * @instance
                 */
                VoiceContent.prototype.objectKey = "";

                /**
                 * VoiceContent durationMs.
                 * @member {number} durationMs
                 * @memberof im.common.v1.VoiceContent
                 * @instance
                 */
                VoiceContent.prototype.durationMs = 0;

                /**
                 * VoiceContent size.
                 * @member {number|Long} size
                 * @memberof im.common.v1.VoiceContent
                 * @instance
                 */
                VoiceContent.prototype.size = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

                /**
                 * VoiceContent codec.
                 * @member {string} codec
                 * @memberof im.common.v1.VoiceContent
                 * @instance
                 */
                VoiceContent.prototype.codec = "";

                /**
                 * Creates a new VoiceContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {im.common.v1.IVoiceContent=} [properties] Properties to set
                 * @returns {im.common.v1.VoiceContent} VoiceContent instance
                 */
                VoiceContent.create = function create(properties) {
                    return new VoiceContent(properties);
                };

                /**
                 * Encodes the specified VoiceContent message. Does not implicitly {@link im.common.v1.VoiceContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {im.common.v1.IVoiceContent} message VoiceContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                VoiceContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.objectKey);
                    if (message.durationMs != null && Object.hasOwnProperty.call(message, "durationMs"))
                        writer.uint32(/* id 2, wireType 0 =*/16).uint32(message.durationMs);
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        writer.uint32(/* id 3, wireType 0 =*/24).uint64(message.size);
                    if (message.codec != null && Object.hasOwnProperty.call(message, "codec"))
                        writer.uint32(/* id 4, wireType 2 =*/34).string(message.codec);
                    return writer;
                };

                /**
                 * Encodes the specified VoiceContent message, length delimited. Does not implicitly {@link im.common.v1.VoiceContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {im.common.v1.IVoiceContent} message VoiceContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                VoiceContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a VoiceContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.VoiceContent} VoiceContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                VoiceContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.VoiceContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.objectKey = reader.string();
                                break;
                            }
                        case 2: {
                                message.durationMs = reader.uint32();
                                break;
                            }
                        case 3: {
                                message.size = reader.uint64();
                                break;
                            }
                        case 4: {
                                message.codec = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a VoiceContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.VoiceContent} VoiceContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                VoiceContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a VoiceContent message.
                 * @function verify
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                VoiceContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        if (!$util.isString(message.objectKey))
                            return "objectKey: string expected";
                    if (message.durationMs != null && Object.hasOwnProperty.call(message, "durationMs"))
                        if (!$util.isInteger(message.durationMs))
                            return "durationMs: integer expected";
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (!$util.isInteger(message.size) && !(message.size && $util.isInteger(message.size.low) && $util.isInteger(message.size.high)))
                            return "size: integer|Long expected";
                    if (message.codec != null && Object.hasOwnProperty.call(message, "codec"))
                        if (!$util.isString(message.codec))
                            return "codec: string expected";
                    return null;
                };

                /**
                 * Creates a VoiceContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.VoiceContent} VoiceContent
                 */
                VoiceContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.VoiceContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.VoiceContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.VoiceContent();
                    if (object.objectKey != null)
                        message.objectKey = String(object.objectKey);
                    if (object.durationMs != null)
                        message.durationMs = object.durationMs >>> 0;
                    if (object.size != null)
                        if ($util.Long)
                            message.size = $util.Long.fromValue(object.size, true);
                        else if (typeof object.size === "string")
                            message.size = parseInt(object.size, 10);
                        else if (typeof object.size === "number")
                            message.size = object.size;
                        else if (typeof object.size === "object")
                            message.size = new $util.LongBits(object.size.low >>> 0, object.size.high >>> 0).toNumber(true);
                    if (object.codec != null)
                        message.codec = String(object.codec);
                    return message;
                };

                /**
                 * Creates a plain object from a VoiceContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {im.common.v1.VoiceContent} message VoiceContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                VoiceContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.objectKey = "";
                        object.durationMs = 0;
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, true);
                            object.size = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.size = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.codec = "";
                    }
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        object.objectKey = message.objectKey;
                    if (message.durationMs != null && Object.hasOwnProperty.call(message, "durationMs"))
                        object.durationMs = message.durationMs;
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.size = typeof message.size === "number" ? BigInt(message.size) : $util.Long.fromBits(message.size.low >>> 0, message.size.high >>> 0, true).toBigInt();
                        else if (typeof message.size === "number")
                            object.size = options.longs === String ? String(message.size) : message.size;
                        else
                            object.size = options.longs === String ? $util.Long.prototype.toString.call(message.size) : options.longs === Number ? new $util.LongBits(message.size.low >>> 0, message.size.high >>> 0).toNumber(true) : message.size;
                    if (message.codec != null && Object.hasOwnProperty.call(message, "codec"))
                        object.codec = message.codec;
                    return object;
                };

                /**
                 * Converts this VoiceContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.VoiceContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                VoiceContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for VoiceContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.VoiceContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                VoiceContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.VoiceContent";
                };

                return VoiceContent;
            })();

            v1.FileContent = (function() {

                /**
                 * Properties of a FileContent.
                 * @memberof im.common.v1
                 * @interface IFileContent
                 * @property {string|null} [objectKey] FileContent objectKey
                 * @property {string|null} [fileName] FileContent fileName
                 * @property {number|Long|null} [size] FileContent size
                 * @property {string|null} [mime] FileContent mime
                 */

                /**
                 * Constructs a new FileContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a FileContent.
                 * @implements IFileContent
                 * @constructor
                 * @param {im.common.v1.IFileContent=} [properties] Properties to set
                 */
                function FileContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * FileContent objectKey.
                 * @member {string} objectKey
                 * @memberof im.common.v1.FileContent
                 * @instance
                 */
                FileContent.prototype.objectKey = "";

                /**
                 * FileContent fileName.
                 * @member {string} fileName
                 * @memberof im.common.v1.FileContent
                 * @instance
                 */
                FileContent.prototype.fileName = "";

                /**
                 * FileContent size.
                 * @member {number|Long} size
                 * @memberof im.common.v1.FileContent
                 * @instance
                 */
                FileContent.prototype.size = $util.Long ? $util.Long.fromBits(0,0,true) : 0;

                /**
                 * FileContent mime.
                 * @member {string} mime
                 * @memberof im.common.v1.FileContent
                 * @instance
                 */
                FileContent.prototype.mime = "";

                /**
                 * Creates a new FileContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {im.common.v1.IFileContent=} [properties] Properties to set
                 * @returns {im.common.v1.FileContent} FileContent instance
                 */
                FileContent.create = function create(properties) {
                    return new FileContent(properties);
                };

                /**
                 * Encodes the specified FileContent message. Does not implicitly {@link im.common.v1.FileContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {im.common.v1.IFileContent} message FileContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                FileContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.objectKey);
                    if (message.fileName != null && Object.hasOwnProperty.call(message, "fileName"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.fileName);
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        writer.uint32(/* id 3, wireType 0 =*/24).uint64(message.size);
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        writer.uint32(/* id 4, wireType 2 =*/34).string(message.mime);
                    return writer;
                };

                /**
                 * Encodes the specified FileContent message, length delimited. Does not implicitly {@link im.common.v1.FileContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {im.common.v1.IFileContent} message FileContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                FileContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a FileContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.FileContent} FileContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                FileContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.FileContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.objectKey = reader.string();
                                break;
                            }
                        case 2: {
                                message.fileName = reader.string();
                                break;
                            }
                        case 3: {
                                message.size = reader.uint64();
                                break;
                            }
                        case 4: {
                                message.mime = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a FileContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.FileContent} FileContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                FileContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a FileContent message.
                 * @function verify
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                FileContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        if (!$util.isString(message.objectKey))
                            return "objectKey: string expected";
                    if (message.fileName != null && Object.hasOwnProperty.call(message, "fileName"))
                        if (!$util.isString(message.fileName))
                            return "fileName: string expected";
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (!$util.isInteger(message.size) && !(message.size && $util.isInteger(message.size.low) && $util.isInteger(message.size.high)))
                            return "size: integer|Long expected";
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        if (!$util.isString(message.mime))
                            return "mime: string expected";
                    return null;
                };

                /**
                 * Creates a FileContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.FileContent} FileContent
                 */
                FileContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.FileContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.FileContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.FileContent();
                    if (object.objectKey != null)
                        message.objectKey = String(object.objectKey);
                    if (object.fileName != null)
                        message.fileName = String(object.fileName);
                    if (object.size != null)
                        if ($util.Long)
                            message.size = $util.Long.fromValue(object.size, true);
                        else if (typeof object.size === "string")
                            message.size = parseInt(object.size, 10);
                        else if (typeof object.size === "number")
                            message.size = object.size;
                        else if (typeof object.size === "object")
                            message.size = new $util.LongBits(object.size.low >>> 0, object.size.high >>> 0).toNumber(true);
                    if (object.mime != null)
                        message.mime = String(object.mime);
                    return message;
                };

                /**
                 * Creates a plain object from a FileContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {im.common.v1.FileContent} message FileContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                FileContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.objectKey = "";
                        object.fileName = "";
                        if ($util.Long) {
                            let long = new $util.Long(0, 0, true);
                            object.size = options.longs === String ? long.toString() : options.longs === Number ? long.toNumber() : typeof BigInt !== "undefined" && options.longs === BigInt ? long.toBigInt() : long;
                        } else
                            object.size = options.longs === String ? "0" : typeof BigInt !== "undefined" && options.longs === BigInt ? BigInt("0") : 0;
                        object.mime = "";
                    }
                    if (message.objectKey != null && Object.hasOwnProperty.call(message, "objectKey"))
                        object.objectKey = message.objectKey;
                    if (message.fileName != null && Object.hasOwnProperty.call(message, "fileName"))
                        object.fileName = message.fileName;
                    if (message.size != null && Object.hasOwnProperty.call(message, "size"))
                        if (typeof BigInt !== "undefined" && options.longs === BigInt)
                            object.size = typeof message.size === "number" ? BigInt(message.size) : $util.Long.fromBits(message.size.low >>> 0, message.size.high >>> 0, true).toBigInt();
                        else if (typeof message.size === "number")
                            object.size = options.longs === String ? String(message.size) : message.size;
                        else
                            object.size = options.longs === String ? $util.Long.prototype.toString.call(message.size) : options.longs === Number ? new $util.LongBits(message.size.low >>> 0, message.size.high >>> 0).toNumber(true) : message.size;
                    if (message.mime != null && Object.hasOwnProperty.call(message, "mime"))
                        object.mime = message.mime;
                    return object;
                };

                /**
                 * Converts this FileContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.FileContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                FileContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for FileContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.FileContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                FileContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.FileContent";
                };

                return FileContent;
            })();

            v1.NotificationContent = (function() {

                /**
                 * Properties of a NotificationContent.
                 * @memberof im.common.v1
                 * @interface INotificationContent
                 * @property {string|null} [eventType] NotificationContent eventType
                 * @property {string|null} [payload] NotificationContent payload
                 */

                /**
                 * Constructs a new NotificationContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a NotificationContent.
                 * @implements INotificationContent
                 * @constructor
                 * @param {im.common.v1.INotificationContent=} [properties] Properties to set
                 */
                function NotificationContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * NotificationContent eventType.
                 * @member {string} eventType
                 * @memberof im.common.v1.NotificationContent
                 * @instance
                 */
                NotificationContent.prototype.eventType = "";

                /**
                 * NotificationContent payload.
                 * @member {string} payload
                 * @memberof im.common.v1.NotificationContent
                 * @instance
                 */
                NotificationContent.prototype.payload = "";

                /**
                 * Creates a new NotificationContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {im.common.v1.INotificationContent=} [properties] Properties to set
                 * @returns {im.common.v1.NotificationContent} NotificationContent instance
                 */
                NotificationContent.create = function create(properties) {
                    return new NotificationContent(properties);
                };

                /**
                 * Encodes the specified NotificationContent message. Does not implicitly {@link im.common.v1.NotificationContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {im.common.v1.INotificationContent} message NotificationContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                NotificationContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.eventType != null && Object.hasOwnProperty.call(message, "eventType"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.eventType);
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.payload);
                    return writer;
                };

                /**
                 * Encodes the specified NotificationContent message, length delimited. Does not implicitly {@link im.common.v1.NotificationContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {im.common.v1.INotificationContent} message NotificationContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                NotificationContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a NotificationContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.NotificationContent} NotificationContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                NotificationContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.NotificationContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.eventType = reader.string();
                                break;
                            }
                        case 2: {
                                message.payload = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a NotificationContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.NotificationContent} NotificationContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                NotificationContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a NotificationContent message.
                 * @function verify
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                NotificationContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.eventType != null && Object.hasOwnProperty.call(message, "eventType"))
                        if (!$util.isString(message.eventType))
                            return "eventType: string expected";
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        if (!$util.isString(message.payload))
                            return "payload: string expected";
                    return null;
                };

                /**
                 * Creates a NotificationContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.NotificationContent} NotificationContent
                 */
                NotificationContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.NotificationContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.NotificationContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.NotificationContent();
                    if (object.eventType != null)
                        message.eventType = String(object.eventType);
                    if (object.payload != null)
                        message.payload = String(object.payload);
                    return message;
                };

                /**
                 * Creates a plain object from a NotificationContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {im.common.v1.NotificationContent} message NotificationContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                NotificationContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.eventType = "";
                        object.payload = "";
                    }
                    if (message.eventType != null && Object.hasOwnProperty.call(message, "eventType"))
                        object.eventType = message.eventType;
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        object.payload = message.payload;
                    return object;
                };

                /**
                 * Converts this NotificationContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.NotificationContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                NotificationContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for NotificationContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.NotificationContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                NotificationContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.NotificationContent";
                };

                return NotificationContent;
            })();

            v1.CustomContent = (function() {

                /**
                 * Properties of a CustomContent.
                 * @memberof im.common.v1
                 * @interface ICustomContent
                 * @property {string|null} [customType] CustomContent customType
                 * @property {string|null} [payload] CustomContent payload
                 */

                /**
                 * Constructs a new CustomContent.
                 * @memberof im.common.v1
                 * @classdesc Represents a CustomContent.
                 * @implements ICustomContent
                 * @constructor
                 * @param {im.common.v1.ICustomContent=} [properties] Properties to set
                 */
                function CustomContent(properties) {
                    if (properties)
                        for (let keys = Object.keys(properties), i = 0; i < keys.length; ++i)
                            if (properties[keys[i]] != null && keys[i] !== "__proto__")
                                this[keys[i]] = properties[keys[i]];
                }

                /**
                 * CustomContent customType.
                 * @member {string} customType
                 * @memberof im.common.v1.CustomContent
                 * @instance
                 */
                CustomContent.prototype.customType = "";

                /**
                 * CustomContent payload.
                 * @member {string} payload
                 * @memberof im.common.v1.CustomContent
                 * @instance
                 */
                CustomContent.prototype.payload = "";

                /**
                 * Creates a new CustomContent instance using the specified properties.
                 * @function create
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {im.common.v1.ICustomContent=} [properties] Properties to set
                 * @returns {im.common.v1.CustomContent} CustomContent instance
                 */
                CustomContent.create = function create(properties) {
                    return new CustomContent(properties);
                };

                /**
                 * Encodes the specified CustomContent message. Does not implicitly {@link im.common.v1.CustomContent.verify|verify} messages.
                 * @function encode
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {im.common.v1.ICustomContent} message CustomContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                CustomContent.encode = function encode(message, writer, q) {
                    if (!writer)
                        writer = $Writer.create();
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    if (message.customType != null && Object.hasOwnProperty.call(message, "customType"))
                        writer.uint32(/* id 1, wireType 2 =*/10).string(message.customType);
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        writer.uint32(/* id 2, wireType 2 =*/18).string(message.payload);
                    return writer;
                };

                /**
                 * Encodes the specified CustomContent message, length delimited. Does not implicitly {@link im.common.v1.CustomContent.verify|verify} messages.
                 * @function encodeDelimited
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {im.common.v1.ICustomContent} message CustomContent message or plain object to encode
                 * @param {$protobuf.Writer} [writer] Writer to encode to
                 * @returns {$protobuf.Writer} Writer
                 */
                CustomContent.encodeDelimited = function encodeDelimited(message, writer) {
                    return this.encode(message, writer && writer.len ? writer.fork() : writer).ldelim();
                };

                /**
                 * Decodes a CustomContent message from the specified reader or buffer.
                 * @function decode
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @param {number} [length] Message length if known beforehand
                 * @returns {im.common.v1.CustomContent} CustomContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                CustomContent.decode = function decode(reader, length, error, long) {
                    if (!(reader instanceof $Reader))
                        reader = $Reader.create(reader);
                    if (long === undefined)
                        long = 0;
                    if (long > $Reader.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let end = length === undefined ? reader.len : reader.pos + length, message = new $root.im.common.v1.CustomContent();
                    while (reader.pos < end) {
                        let tag = reader.uint32();
                        if (tag === error)
                            break;
                        switch (tag >>> 3) {
                        case 1: {
                                message.customType = reader.string();
                                break;
                            }
                        case 2: {
                                message.payload = reader.string();
                                break;
                            }
                        default:
                            reader.skipType(tag & 7, long);
                            break;
                        }
                    }
                    return message;
                };

                /**
                 * Decodes a CustomContent message from the specified reader or buffer, length delimited.
                 * @function decodeDelimited
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {$protobuf.Reader|Uint8Array} reader Reader or buffer to decode from
                 * @returns {im.common.v1.CustomContent} CustomContent
                 * @throws {Error} If the payload is not a reader or valid buffer
                 * @throws {$protobuf.util.ProtocolError} If required fields are missing
                 */
                CustomContent.decodeDelimited = function decodeDelimited(reader) {
                    if (!(reader instanceof $Reader))
                        reader = new $Reader(reader);
                    return this.decode(reader, reader.uint32());
                };

                /**
                 * Verifies a CustomContent message.
                 * @function verify
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {Object.<string,*>} message Plain object to verify
                 * @returns {string|null} `null` if valid, otherwise the reason why it is not
                 */
                CustomContent.verify = function verify(message, long) {
                    if (typeof message !== "object" || message === null)
                        return "object expected";
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        return "maximum nesting depth exceeded";
                    if (message.customType != null && Object.hasOwnProperty.call(message, "customType"))
                        if (!$util.isString(message.customType))
                            return "customType: string expected";
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        if (!$util.isString(message.payload))
                            return "payload: string expected";
                    return null;
                };

                /**
                 * Creates a CustomContent message from a plain object. Also converts values to their respective internal types.
                 * @function fromObject
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {Object.<string,*>} object Plain object
                 * @returns {im.common.v1.CustomContent} CustomContent
                 */
                CustomContent.fromObject = function fromObject(object, long) {
                    if (object instanceof $root.im.common.v1.CustomContent)
                        return object;
                    if (!$util.isObject(object))
                        throw TypeError(".im.common.v1.CustomContent: object expected");
                    if (long === undefined)
                        long = 0;
                    if (long > $util.recursionLimit)
                        throw Error("maximum nesting depth exceeded");
                    let message = new $root.im.common.v1.CustomContent();
                    if (object.customType != null)
                        message.customType = String(object.customType);
                    if (object.payload != null)
                        message.payload = String(object.payload);
                    return message;
                };

                /**
                 * Creates a plain object from a CustomContent message. Also converts values to other types if specified.
                 * @function toObject
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {im.common.v1.CustomContent} message CustomContent
                 * @param {$protobuf.IConversionOptions} [options] Conversion options
                 * @returns {Object.<string,*>} Plain object
                 */
                CustomContent.toObject = function toObject(message, options, q) {
                    if (!options)
                        options = {};
                    if (q === undefined)
                        q = 0;
                    if (q > $util.recursionLimit)
                        throw Error("max depth exceeded");
                    let object = {};
                    if (options.defaults) {
                        object.customType = "";
                        object.payload = "";
                    }
                    if (message.customType != null && Object.hasOwnProperty.call(message, "customType"))
                        object.customType = message.customType;
                    if (message.payload != null && Object.hasOwnProperty.call(message, "payload"))
                        object.payload = message.payload;
                    return object;
                };

                /**
                 * Converts this CustomContent to JSON.
                 * @function toJSON
                 * @memberof im.common.v1.CustomContent
                 * @instance
                 * @returns {Object.<string,*>} JSON object
                 */
                CustomContent.prototype.toJSON = function toJSON() {
                    return this.constructor.toObject(this, $protobuf.util.toJSONOptions);
                };

                /**
                 * Gets the default type url for CustomContent
                 * @function getTypeUrl
                 * @memberof im.common.v1.CustomContent
                 * @static
                 * @param {string} [typeUrlPrefix] your custom typeUrlPrefix(default "type.googleapis.com")
                 * @returns {string} The default type url
                 */
                CustomContent.getTypeUrl = function getTypeUrl(typeUrlPrefix) {
                    if (typeUrlPrefix === undefined) {
                        typeUrlPrefix = "type.googleapis.com";
                    }
                    return typeUrlPrefix + "/im.common.v1.CustomContent";
                };

                return CustomContent;
            })();

            return v1;
        })();

        return common;
    })();

    return im;
})();

export { $root as default };
