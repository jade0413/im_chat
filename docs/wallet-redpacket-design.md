# 钱包 / 红包 / 转账设计文档（im-wallet-service）

> 状态：待实现 | 讨论日期：2026-07-03 | 关联决策：D46（本文档）、依赖 D18(Outbox)/D26(seq)/D20(content oneof)/D40(状态表与消息解耦)
>
> 实现按本文档执行；如需偏离，先在 CLAUDE.md Open Questions 提出再改文档（协作铁律）。
> 本模块是频道付费创建（channel-discovery-design.md）与开放平台支付（open-platform-design.md）的资金底座，**必须最先实现**。

---

## 1. 定位与边界（Jade 已拍板）

1. **资金形态 = 平台虚拟币（COIN）**：充值单向（法币→COIN），**不承诺提现**。账务仍按真实资金标准做复式记账——形态若未来升级为真实资金，账务层零改动，只换充值/提现 Provider。
2. **钱包地址 = 平台内部地址**：类 TRC20 格式字符串（base58check），仅作平台内收款标识，**不上区块链**。非好友可凭地址转账，收手续费。
3. 能力清单：余额钱包、C2C/群拼手气红包/等额红包、好友转账（确认收款制）、地址转账（直接到账+手续费）、支付密码、内部支付 RPC（供频道创建/开放平台收银台复用）。
4. 明确不做（MVP）：提现、真实支付渠道（Provider 接口预留 + Mock/后台手工入账）、跨租户资金往来（与 D3/D41 同理，租户内闭环）、多币种（`currency` 列预留恒 `COIN`）。

### 模块与依赖

- 新模块 **`im-wallet-service`**（Maven 子模块，挂入 im-bootstrap）。资金域独立成模块是铁律：任何其他模块**禁止直接写钱包表**，一切资金变动走 `WalletRpc`。
- 依赖方向：wallet → ConversationRpc（红包领取资格校验）/UserRpc（好友校验、资料）；message → wallet（红包/转账消息合法性校验 `WalletRpc.VerifyPacketMsg`）；group/open → wallet（`WalletRpc.Pay`）。均走 in-process gRPC（D5 铁律）。
- 所有表首列业务列为 tenant_id，拦截器自动注入（核心约定 1）。

---

## 2. 账务模型（本文档最重要的一节，实现者必须逐条遵守）

### 2.1 铁律

| # | 规则 | 违反后果 |
|---|------|---------|
| W1 | 金额一律 `BIGINT`，单位**分**（COIN 最小单位）。任何层（Java/proto/JSON/Dart）禁止 float/double/BigDecimal 运算金额 | 精度丢失=资损 |
| W2 | 复式记账：每笔业务单（txn）产生 ≥2 条 ledger 分录，**分录金额代数和恒为 0** | 对账不平无法定位 |
| W3 | 余额变动只允许**条件 UPDATE**：`UPDATE wallet_account SET balance=balance-#{x}, version=version+1 WHERE id=#{id} AND balance>=#{x}`，affected=0 即余额不足回滚。禁止 SELECT 后内存算再写 | 并发下负余额 |
| W4 | 所有资金操作幂等：业务单带调用方生成的 `idem_key`，`UNIQUE(tenant_id, idem_key)` 兜底，重复请求返回首次结果 | 重试双扣 |
| W5 | 资金事务**不跨模块、不跨库**：单事务内只写 wallet 域表 + outbox；通知/消息等副作用一律事务提交后经 outbox 异步驱动（D18） | 分布式事务泥潭 |
| W6 | 系统账户**去余额化**：平台科目（手续费/红包在途/转账在途/商户）只写 ledger 不实时 UPDATE balance，实况余额=Σledger，日终对账校验。只有用户账户实时维护 balance | 全平台热点行锁 |
| W7 | 多账户行更新按 `account_id` 升序执行 | 交叉转账死锁 |
| W8 | ledger/txn **只插不改不删**（immutable）；状态流转只发生在业务单表（red_packet/transfer/pay_order）| 审计失效 |

### 2.2 科目（account_type）

| type | 说明 | balance 维护 |
|------|------|--------------|
| 1 USER | 用户钱包（每用户一行，首次资金操作懒开户） | 实时（W3） |
| 2 FEE | 平台手续费收入 | 去余额化（W6） |
| 3 RP_ESCROW | 红包在途（明细真相在 red_packet 行） | 去余额化 |
| 4 TRANSFER_ESCROW | 转账在途（明细真相在 transfer 行） | 去余额化 |
| 5 MERCHANT | 开放平台商户收款（每 app 一行，open-platform 设计引用） | 去余额化 |
| 6 RECHARGE | 充值对手科目（借方，代表外部资金流入） | 去余额化 |

### 2.3 对账不变量（daily job 校验，见 §8）

- I1：∀txn，Σledger.amount = 0
- I2：∀USER 账户，balance = Σledger.amount（按 account 汇总）
- I3：∀red_packet，total_amount = Σclaim.amount + refund_amount + remaining_amount
- I4：Σ所有科目 ledger = 0（全局平衡）

---

## 3. 数据模型（V17__wallet.sql，编号以实现时最新为准）

```sql
CREATE TABLE wallet_account (
  id           BIGINT      NOT NULL,               -- Snowflake
  tenant_id    BIGINT      NOT NULL,
  account_type TINYINT     NOT NULL DEFAULT 1,     -- §2.2
  owner_id     BIGINT      NOT NULL DEFAULT 0,     -- USER=user_id / MERCHANT=app 主键 / 系统科目=0
  currency     VARCHAR(8)  NOT NULL DEFAULT 'COIN',
  balance      BIGINT      NOT NULL DEFAULT 0,     -- 分；CHECK(balance>=0)
  version      BIGINT      NOT NULL DEFAULT 0,
  status       TINYINT     NOT NULL DEFAULT 0,     -- 0 normal / 1 frozen（风控冻结，禁支出允收入）
  create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_owner (tenant_id, account_type, owner_id, currency)
);

CREATE TABLE wallet_txn (                          -- 业务单（一次资金操作一行）
  id          BIGINT      NOT NULL,                -- Snowflake，即 txn_id
  tenant_id   BIGINT      NOT NULL,
  biz_type    VARCHAR(32) NOT NULL,  -- rp.send/rp.claim/rp.refund/transfer.send/transfer.receive/
                                     -- transfer.refund/addr.transfer/recharge/pay.order/pay.refund/channel.create
  biz_id      BIGINT      NOT NULL DEFAULT 0,      -- 关联 red_packet.id / transfer.id / pay_order.id
  idem_key    VARCHAR(64) NOT NULL,                -- W4 幂等键，调用方生成
  from_account_id BIGINT  NOT NULL,
  to_account_id   BIGINT  NOT NULL,
  amount      BIGINT      NOT NULL,                -- 主金额（不含费）
  fee         BIGINT      NOT NULL DEFAULT 0,
  status      TINYINT     NOT NULL DEFAULT 1,      -- 1 success（单事务内完成即成功；无中间态）
  remark      VARCHAR(128) NOT NULL DEFAULT '',
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_idem (tenant_id, idem_key),
  KEY idx_biz (tenant_id, biz_type, biz_id)
);

CREATE TABLE wallet_ledger (                       -- 分录（immutable, W8）
  id          BIGINT      NOT NULL,
  tenant_id   BIGINT      NOT NULL,
  txn_id      BIGINT      NOT NULL,
  account_id  BIGINT      NOT NULL,
  amount      BIGINT      NOT NULL,                -- 有符号：入账+ 出账-
  balance_after BIGINT    NULL,                    -- USER 账户记扣/入后余额；去余额化科目为 NULL
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_txn (txn_id),
  KEY idx_account (tenant_id, account_id, id)      -- 用户账单分页
);

CREATE TABLE red_packet (
  id           BIGINT      NOT NULL,
  tenant_id    BIGINT      NOT NULL,
  sender_id    BIGINT      NOT NULL,
  conv_id      BIGINT      NOT NULL,               -- 创建时即绑定目标会话（防挪用，§5.1）
  kind         TINYINT     NOT NULL,               -- 1 LUCKY 拼手气 / 2 FIXED 等额
  greeting     VARCHAR(64) NOT NULL DEFAULT '恭喜发财，大吉大利',
  total_amount BIGINT      NOT NULL,
  total_count  INT         NOT NULL,
  remaining_amount BIGINT  NOT NULL,
  remaining_count  INT     NOT NULL,
  refund_amount    BIGINT  NOT NULL DEFAULT 0,
  status       TINYINT     NOT NULL DEFAULT 1,     -- 1 ACTIVE / 2 FINISHED / 3 EXPIRED(已退回)
  expire_time  DATETIME(3) NOT NULL,               -- create+24h
  create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_expire (status, expire_time)             -- sweeper 扫描
);

CREATE TABLE red_packet_claim (
  id          BIGINT      NOT NULL,
  tenant_id   BIGINT      NOT NULL,
  packet_id   BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  amount      BIGINT      NOT NULL,
  is_best     TINYINT(1)  NOT NULL DEFAULT 0,      -- 手气最佳（红包领完后由结算逻辑回填）
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_claim (packet_id, user_id),        -- 防重复领取的唯一真相
  KEY idx_user (tenant_id, user_id, id)
);

CREATE TABLE transfer (
  id          BIGINT      NOT NULL,
  tenant_id   BIGINT      NOT NULL,
  from_user_id BIGINT     NOT NULL,
  to_user_id  BIGINT      NOT NULL,
  conv_id     BIGINT      NOT NULL DEFAULT 0,      -- 好友转账绑定 C2C 会话；地址转账=0
  channel     TINYINT     NOT NULL,                -- 1 FRIEND(确认收款) / 2 ADDRESS(直接到账)
  amount      BIGINT      NOT NULL,
  fee         BIGINT      NOT NULL DEFAULT 0,      -- 仅 ADDRESS
  remark      VARCHAR(64) NOT NULL DEFAULT '',
  status      TINYINT     NOT NULL DEFAULT 1,      -- 1 PENDING / 2 RECEIVED / 3 REFUNDED；ADDRESS 直接=2
  expire_time DATETIME(3) NULL,                    -- FRIEND: create+24h；ADDRESS: NULL
  receive_time DATETIME(3) NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_expire (status, expire_time),
  KEY idx_user (tenant_id, from_user_id, id), KEY idx_to (tenant_id, to_user_id, id)
);

CREATE TABLE wallet_address (
  address     VARCHAR(40) NOT NULL,                -- base58check，'T' 开头 34 字符
  tenant_id   BIGINT      NOT NULL,
  user_id     BIGINT      NOT NULL,
  status      TINYINT     NOT NULL DEFAULT 0,      -- 0 active；历史地址永久有效可收款
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (address),
  KEY idx_user (tenant_id, user_id)
);

CREATE TABLE wallet_security (
  tenant_id    BIGINT     NOT NULL,
  user_id      BIGINT     NOT NULL,
  pin_hash     VARCHAR(80) NOT NULL,               -- bcrypt(6 位数字 PIN)
  fail_count   INT        NOT NULL DEFAULT 0,
  locked_until DATETIME(3) NULL,                   -- 连错 5 次锁 15 分钟
  update_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (tenant_id, user_id)
);
```

租户级配置（`tenant_config.plan_features` JSON 内扩展，读侧带缓存）：
`rp_max_amount`(单红包上限，默认 20000_00)、`rp_max_count`(单红包个数上限，默认 100)、`daily_out_limit`(单用户日支出上限，默认 50000_00)、`addr_fee_rate`(地址转账费率，千分位，默认 1‰、最低 1 分)、`transfer_expire_hours`(24)、`rp_expire_hours`(24)。

---

## 4. 协议改动（走核心约定 5 流程：改 im-proto → 生成 → Rust+Java 同过）

### 4.1 content.proto —— 新增两个强类型分支（字段号取预留区 30/31）

红包/转账是资金消息，**必须强类型**（Custom JSON 可被客户端伪造任意渲染，资金语义不允许）。学 D40：**消息不可变，可变状态（是否领完/是否收款）唯一真相在表**，消息体只带 id，客户端查状态渲染。

```proto
message MsgContent {
  oneof content {
    // ...现有 1/2/3/4/10/20 不动...
    RedPacketContent red_packet = 30;
    TransferContent  transfer   = 31;
  }
}
message RedPacketContent {
  int64  packet_id = 1;
  int32  kind      = 2;   // 1 LUCKY / 2 FIXED
  string greeting  = 3;
  // 故意不带金额：总额是发送者隐私（与微信一致）
}
message TransferContent {
  int64  transfer_id = 1;
  int64  amount      = 2;  // 分；转账金额双方可见
  string remark      = 3;
}
```

### 4.2 error.proto —— 新增 8xxx 钱包段

```proto
// 8xxx 钱包/红包/转账
INSUFFICIENT_BALANCE = 8001; PIN_REQUIRED = 8002; PIN_INVALID = 8003; PIN_LOCKED = 8004;
WALLET_LIMIT_EXCEEDED = 8005; WALLET_FROZEN = 8006;
RP_NOT_FOUND = 8101; RP_FINISHED = 8102;          // 手慢了，红包派完了
RP_EXPIRED = 8103; RP_ALREADY_CLAIMED = 8104; RP_NOT_CONV_MEMBER = 8105;
TRANSFER_NOT_FOUND = 8201; TRANSFER_STATE_INVALID = 8202; TRANSFER_NOT_PAYEE = 8203;
ADDRESS_INVALID = 8301; ADDRESS_NOT_FOUND = 8302; SELF_TRANSFER_DENIED = 8303;
```

### 4.3 rpc/internal.proto —— 新增 WalletRpc + MessageRpc.SendConvNotification

```proto
service WalletRpc {
  // 通用内部支付：用户账户 → 平台科目/商户科目。频道创建、开放平台收银台共用
  rpc Pay (PayReq) returns (PayResp);
  rpc RefundPay (RefundPayReq) returns (PayResp);           // 按原 txn 冲正（生成反向分录，不改原分录）
  rpc GetBalance (GetBalanceReq) returns (GetBalanceResp);
  // message 模块发送链路校验红包/转账消息合法性（§5.1 步骤 3）
  rpc VerifyMoneyMsg (VerifyMoneyMsgReq) returns (VerifyMoneyMsgResp);
}
message PayReq {
  int64 tenant_id = 1; int64 user_id = 2;
  int32 to_account_type = 3;      // 2 FEE / 5 MERCHANT
  int64 to_owner_id = 4;          // MERCHANT 时=app 主键
  int64 amount = 5;               // 分
  string biz_type = 6; int64 biz_id = 7;
  string idem_key = 8;            // 调用方生成，W4
  string pin = 9;                 // 需验密场景传入；内部免密场景（已在 REST 层验过）传空
}
message PayResp { int32 code = 1; int64 txn_id = 2; }
message RefundPayReq { int64 tenant_id = 1; int64 txn_id = 2; string idem_key = 3; }
message GetBalanceReq { int64 tenant_id = 1; int64 user_id = 2; }
message GetBalanceResp { int64 balance = 1; string currency = 2; }
message VerifyMoneyMsgReq {
  int64 tenant_id = 1; int64 sender_id = 2; int64 conv_id = 3;
  oneof ref { int64 packet_id = 4; int64 transfer_id = 5; }
}
message VerifyMoneyMsgResp { int32 code = 1; }    // 0=合法；否则 8xxx

// MessageRpc 增加：向会话内追加系统灰条（红包领取/转账退回/群事件通用）
rpc SendConvNotification (SendConvNotificationReq) returns (SendSystemNotificationResp);
message SendConvNotificationReq {
  int64  tenant_id = 1; int64 conv_id = 2;
  string event_type = 3; string payload = 4;      // JSON
  string idem_suffix = 5;                          // client_msg_id = "sys:"+event_type+":"+idem_suffix
}
```

`SendConvNotification` 实现与 D40 §9.2 `SendSystemNotification` 同构：sender_id=0、分配 seq、走 outbox→push，差别仅是目标会话由调用方指定而非 SYSTEM 会话。**新增业务帧为零**：红包/转账消息走现有 MSG_SEND/MSG_PUSH，领取/收款动作走 REST（同步语义强、失败可重试，不占 WS 信道）。网关零改动（D19 红利）。

### 4.4 NotificationContent 新增 event_type（登记入 protocol.md 附录 A）

| event_type | 进入哪条会话 | payload |
|---|---|---|
| `wallet.rp.claimed` | 红包所在会话（灰条：「A 领取了 B 的红包」） | `{packet_id, claimer_id, claimer_nickname, sender_id, finished(bool)}` |
| `wallet.rp.expired` | 发送者 SYSTEM 会话（「你的红包已过期，X 元已退回」） | `{packet_id, refund_amount}` |
| `wallet.transfer.received` | 转账所在 C2C 会话 | `{transfer_id, amount}` |
| `wallet.transfer.refunded` | 转账所在 C2C 会话 + 双方 SYSTEM | `{transfer_id, amount, reason(expired/rejected)}` |
| `wallet.addr.received` | 收款方 SYSTEM 会话（地址转账无会话） | `{transfer_id, amount, from_masked}` |

---

## 5. 核心流程

### 5.1 发红包（REST 建单 + WS 发消息，两段解耦）

```
① POST /api/v1/wallet/red-packets {conv_id, kind, total_amount, total_count, greeting, pin, client_txn_id}
   wallet 模块单事务：
   1. 验 PIN（§7.1）→ 验限额 → 验会话成员资格（ConversationRpc.GetMembers 含 sender；
      C2C 会话 total_count 强制=1；GROUP 会话 total_count<=min(成员数, rp_max_count)）
   2. 条件扣款 sender USER 账户（W3）
   3. INSERT red_packet(ACTIVE, remaining=total) + wallet_txn(rp.send, idem_key=client_txn_id)
      + ledger 2 分录（user -total / RP_ESCROW +total）
   返回 {packet_id}
② 客户端走正常 MSG_SEND 发 RedPacketContent{packet_id,...}
③ message 模块发送链路：解出 content 是 red_packet/transfer 分支 → 调 WalletRpc.VerifyMoneyMsg
   校验：单据存在 && sender_id 匹配 && conv_id 匹配 && 状态 ACTIVE/PENDING → 不合法拒发（防伪造他人红包）
④ 消息发送失败：客户端可重发（同 client_msg_id 幂等）；始终没发出去 → 24h 过期 sweeper 退款兜底
```

> ⚠️ 注意：**不做「建单+发消息」跨模块事务**。红包钱先离开用户账户进在途，消息只是「通知领取入口」，丢失的最坏后果=24h 后退回，符合 W5。

### 5.2 抢红包 `POST /api/v1/wallet/red-packets/{id}/claim`

```
0. Redis SETNX rp:claim:{pid}:{uid} EX 3600 —— 挡重复点击（非真相，真相是 uk_claim）
1. 资格：ConversationRpc.GetMembers(red_packet.conv_id) 含当前用户；不满足 → 8105
2. DB 事务（串行点=red_packet 行锁，单红包 ≤500 并发短事务，D4 规模无压力）：
   a. SELECT ... FOR UPDATE red_packet WHERE id=? ；status!=ACTIVE → 8102/8103
   b. 算金额 X：
      FIXED: X = total_amount / total_count（建单时保证整除）
      LUCKY 二倍均值：remaining_count==1 ? X=remaining_amount
        : X = random(1, remaining_amount*2/remaining_count)，下限 1 分；
        并保证 remaining_amount-X >= remaining_count-1（每个剩余份额至少 1 分）
   c. INSERT red_packet_claim —— 撞 uk_claim → 8104（并发真相）
   d. UPDATE red_packet SET remaining_amount-=X, remaining_count-=1,
        status=IF(remaining_count=0, FINISHED, ACTIVE)
   e. 用户账户 balance+=X（无条件加款也走 UPDATE 行锁）；txn(rp.claim) + ledger（ESCROW -X / user +X）
   f. FINISHED 时回填 is_best（LUCKY 且 total_count>1：UPDATE claim SET is_best=1 WHERE amount=max）
   g. outbox: wallet.rp.claimed 事件
3. 提交后消费者调 MessageRpc.SendConvNotification 发会话灰条（idem_suffix=claim.id）
4. 响应 {amount, finished}；客户端弹开红包动画后跳详情
```

### 5.3 好友转账（确认收款制，微信语义）

```
发起 POST /api/v1/wallet/transfers {to_user_id, amount, remark, pin, client_txn_id}
  校验：好友关系（UserRpc.CheckRelation，非好友 → FRIEND_REQUIRED 2002）、限额、PIN
  事务：扣 from → TRANSFER_ESCROW；INSERT transfer(FRIEND, PENDING, expire=+24h) + txn + ledger
  返回 transfer_id → 客户端 MSG_SEND 发 TransferContent（③ 同 5.1 校验）
收款 POST /api/v1/wallet/transfers/{id}/receive （仅 to_user 本人；不需要 PIN）
  事务：CAS UPDATE transfer PENDING→RECEIVED；ESCROW → to 账户；txn(transfer.receive)+ledger；outbox
  灰条 wallet.transfer.received；转账气泡双端由「待收款」变「已收款」（状态查表渲染，D40 模式）
退还 POST /api/v1/wallet/transfers/{id}/refund （to_user 主动退，或 sweeper 24h 超时）
  事务：CAS PENDING→REFUNDED；ESCROW → from；outbox → 灰条+SYSTEM 通知
```

### 5.4 地址转账（非好友，直接到账 + 手续费）

```
查询 GET /api/v1/wallet/addresses/resolve?address= → {nickname_masked, avatar}  // 确认页展示，防误转
转账 POST /api/v1/wallet/transfers {to_address, amount, remark, pin, client_txn_id}
  1. base58check 校验（本地验 checksum，格式非法 → 8301）→ 查 wallet_address → 8302
  2. 目标是自己 → 8303
  3. fee = max(amount * addr_fee_rate / 1000, 1)
  4. 单事务：条件扣 from (amount+fee) → to 账户 +amount、FEE 科目 +fee
     txn(addr.transfer, fee) + ledger 3 分录（from -(amount+fee) / to +amount / FEE +fee）
     transfer 行(channel=ADDRESS, status=RECEIVED, conv_id=0)
  5. outbox → 收款方 SYSTEM 通知（wallet.addr.received，from 脱敏）
```

地址生成 `POST /api/v1/wallet/addresses`：`payload = 0x41 || random16 || user_id 低 4 字节混淆`，`checksum = sha256(sha256(payload))[0..4]`，`address = base58(payload || checksum)`（'T' 开头 34 字符）。撞 PRIMARY KEY 重试。每用户可持有多地址，历史地址永久有效（收款标识不回收）。

### 5.5 充值（Provider 抽象，MVP Mock）

接口 `RechargeProvider { createOrder(user, amount) → payUrl; handleCallback(raw) → RechargeResult }`。
MVP 实现：`AdminManualRechargeProvider`——管理后台入账（admin API 加币，txn: RECHARGE 科目 → 用户，biz=recharge，审计日志必留操作人）。真实渠道（支付宝/微信/Stripe）二阶段接入，回调必须：验签 → 幂等（idem_key=渠道单号）→ 金额以服务端订单为准。

---

## 6. REST 汇总（wallet 模块，鉴权 Bearer JWT，均要求 user_type=member）

```
GET  /api/v1/wallet                          → {balance, currency, has_pin}
GET  /api/v1/wallet/ledger?cursor=&limit=    → 账单流水（按 ledger idx_account 分页）
POST /api/v1/wallet/pin {pin}                → 设置支付密码（首次）
PUT  /api/v1/wallet/pin {old_pin, new_pin}   → 修改
POST /api/v1/wallet/red-packets              → §5.1
GET  /api/v1/wallet/red-packets/{id}         → 详情：状态/领取列表/自己领取额（会话成员可看）
POST /api/v1/wallet/red-packets/{id}/claim   → §5.2
POST /api/v1/wallet/transfers                → §5.3/§5.4（to_user_id 与 to_address 二选一）
GET  /api/v1/wallet/transfers/{id}           → 状态（气泡渲染用，仅双方可查）
POST /api/v1/wallet/transfers/{id}/receive | /refund
GET  /api/v1/wallet/addresses  POST /api/v1/wallet/addresses
GET  /api/v1/wallet/addresses/resolve?address=
```

---

## 7. 安全与风控

1. **PIN**：6 位数字，bcrypt 存储；错 5 次锁 15 分钟（`locked_until`）；锁定期一律 8004。校验在 wallet 模块内完成，**REST 层不缓存"已验密"状态**——每个支出请求独立带 PIN（客户端弹密码键盘）。免密小额二阶段。PIN 找回依赖实名/短信，列 Open Question，MVP 仅支持旧密改新密。
2. **限额**：单笔红包 ≤ rp_max_amount；日支出（Redis `wallet:day:{t}:{uid}:{yyyymmdd}` INCRBY，事务成功后累计，超限拒绝 8005）。
3. **冻结**：`wallet_account.status=frozen` → 支出全拒（8006），收入允许；管理后台风控操作。
4. **越权**：红包详情仅会话成员；转账单仅双方；claim/receive 的 user 取自 JWT，**任何接口不信任 body 里的 user_id**。
5. **防伪造消息**：§5.1 步骤③ 是必做项——没有它，任何客户端可发 RedPacketContent{任意 packet_id} 钓鱼。
6. **日志**：所有资金操作打结构化审计日志（trace_id 贯穿，13.4）；金额字段入日志明文可，PIN 绝不入日志。
7. **随机数**：拼手气用 `SecureRandom`（种子不可预测，防"手气最佳"套利）。

---

## 8. 后台任务（与 OutboxPoller/CallSweeper 同构：DB 扫描 + 原子 claim，多实例安全）

| 任务 | 周期 | 逻辑 |
|------|------|------|
| RpExpireSweeper | 60s | `SELECT FROM red_packet WHERE status=ACTIVE AND expire_time<now LIMIT 100`，逐个事务：CAS→EXPIRED，ESCROW→sender 退 remaining_amount，refund_amount 回填，outbox 通知 |
| TransferExpireSweeper | 60s | PENDING 且超时 → 走 §5.3 refund 路径（reason=expired） |
| ReconcileJob | 每日 04:00 | 校验 §2.3 I1~I4；不平 → 告警（Prometheus alert）+ 停用可疑账户，**绝不自动修数** |

---

## 9. im-app（Flutter）实现要点

- `data/wallet/`：`WalletApi`（REST）+ `WalletRepository`；金额全程 `int`（分），展示层 `formatCoin()` 统一格式化，**Dart 侧禁 double**。
- `features/wallet/`：钱包页（余额/账单/地址/收款码——地址二维码 `qr_flutter`）、PIN 键盘组件（自绘 6 格，PIN 不进普通 TextField 避免键盘缓存）、红包发送页、开红包弹窗（动画 → claim → 结果页）、红包详情页、转账页/确认收款页。
- 聊天气泡：`RedPacketContent`/`TransferContent` 专属气泡；**状态渲染查表**——进入会话时对可见红包/转账气泡批量拉状态（新增批量接口可实现期加），点击时二次确认最新状态；已领完/已过期显示蒙层态。
- 灰条：`wallet.rp.claimed` 等 event_type 按现有 NotificationContent 灰条通道渲染。
- 幂等：client_txn_id = uuid，发送失败重试**必须复用同一 id**。

---

## 10. 与现有决策一致性核对

| 关注点 | 结论 |
|--------|------|
| D19 网关 | 零改动（无新 Cmd；领取/收款走 REST） |
| D20 content | 新增 oneof 30/31，符合「预留 30+ 只增不改」 |
| D40 模式 | 可变状态在表、消息只带 id、灰条走消息管道复用 seq/多端/离线 |
| D18/D26 | 资金事务+outbox 同库同事务；通知异步 |
| D3/D25 | 全表 tenant_id 打头；TenantContext 常规注入；outbox 写入带显式 tenant_id |
| D16 审核 | greeting/remark 长度短，仍过 DFA 词库（发送链路同步校验即可，非消息路径） |
| D5 模块铁律 | 新模块 im-wallet-service；跨模块全走 gRPC；他模块禁写钱包表 |

## 11. 实现注意事项（易错清单，Review 时逐条对照）

1. `total_amount / total_count` FIXED 红包建单时校验整除，否则拒绝（防分余数）。
2. LUCKY 下限保护：`X = clamp(rand, 1, remaining_amount - (remaining_count-1))`——漏了会出现后领者 0 分。
3. claim 事务里 **INSERT claim 必须在 UPDATE red_packet 之前**（先撞唯一键快速失败，减少行锁持有时间）。
4. `balance_after` 只在 USER 分录回填；实现时从条件 UPDATE 后 `SELECT balance` 同事务读取。
5. Redis 日限额累计放**事务提交后**；若累计失败仅记日志（限额是风控非账务，允许少算不允许错账）。
6. sweeper 与 claim 并发：EXPIRED CAS 和 claim 的 `status=ACTIVE` 条件互斥，谁先拿到行锁谁赢，无双花。
7. 所有 REST 错误返回统一 `{code, message}`，code 用 error.proto 数值，Flutter 按码弹文案。
8. 转账 `receive`/`refund` 的 CAS 必须带原状态条件，重复点击返回 8202 而非二次入账。
9. 对 `WalletRpc.Pay` 的调用方（group/open 模块）：**幂等键由调用方持久化**（如 channel_id），崩溃重试传同 key。
10. 单测底线：二倍均值分布测试（10万次采样无越界）、并发 claim 压测（500 线程无超发）、对账不变量测试、幂等重放测试。

## 12. 二阶段 / Open Questions（登记 CLAUDE.md）

- 真实充值渠道选型与提现合规（若形态升级）
- PIN 找回（实名/短信依赖）、免密小额
- 商户（MERCHANT）结算/出金语义——开放平台文档关联
- 红包高吞吐路径：Redis 预拆分 list（当前 DB 行锁在 D4 规模够用，升级前重评一致性）
- 跨租户资金（与 D41 同期）
