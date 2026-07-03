# 开放平台设计文档（im-open-service：H5 容器 + JSSDK + 网页授权 + 支付）

> 状态：待实现 | 讨论日期：2026-07-03 | 关联决策：D49（本文档）、依赖 D46(WalletRpc.Pay/MERCHANT 科目)
>
> 实现按本文档执行；如需偏离，先在 CLAUDE.md Open Questions 提出再改文档（协作铁律）。

---

## 1. 定位与边界（Jade 已拍板：H5 容器 + JSSDK）

仿微信公众号网页生态（**不做**小程序自定义运行时）：

1. 发现页九宫格配置第三方 H5 入口（机票/游戏/网站），App 内 WebView 打开。
2. **JSSDK**：注入 `imsdk` bridge——网页授权(getAuthCode)、拉起支付(pay)、分享到会话(share)、基础能力(close/getEnv)。
3. **网页授权**：OAuth2 授权码简化流，第三方后端用 code 换 access_token + open_id。
4. **支付**：第三方服务端统一下单 → H5 拉起原生收银台 → 用户 PIN 确认 → COIN 从用户账户划入该 app 的 MERCHANT 科目 → 服务端回调通知第三方。
5. MVP 不做：开发者自助注册后台（管理后台/admin API 录入 app）、消息推送给用户（模板消息）、商户结算出金（MERCHANT 余额语义列 Open Question）、小程序运行时。

### 模块

新模块 **`im-open-service`**。依赖：WalletRpc.Pay（收银台扣款）、UserRpc.GetUsers（授权用户资料）。open 域接口分两组：
- `/api/v1/open/*`：App 内用户态（JWT 鉴权），供 JSSDK/原生调用。
- `/open/api/*`：第三方服务端 server-to-server（app 签名鉴权，**无用户 JWT**）。

---

## 2. 数据模型（V20__open_platform.sql）

```sql
CREATE TABLE open_app (
  id            BIGINT       NOT NULL,             -- 内部主键，MERCHANT 科目 owner_id 用它
  tenant_id     BIGINT       NOT NULL,
  app_id        VARCHAR(32)  NOT NULL,             -- 对外 "oa_" + 16 hex，随机生成
  app_secret_hash VARCHAR(80) NOT NULL,            -- bcrypt；明文仅创建时展示一次
  name          VARCHAR(64)  NOT NULL,
  icon          VARCHAR(255) NOT NULL DEFAULT '',
  entry_url     VARCHAR(512) NOT NULL,             -- 九宫格入口 URL，必须 https
  domain_whitelist JSON      NOT NULL,             -- ["h5.example.com"]，bridge 调用校验（§5.1）
  scopes        JSON         NOT NULL,             -- ["snsapi_base","snsapi_userinfo","pay","share"]
  notify_url    VARCHAR(512) NOT NULL DEFAULT '',  -- 支付回调
  fee_rate_bp   INT          NOT NULL DEFAULT 0,   -- 平台抽成基点（预留，结算二阶段）
  owner_user_id BIGINT       NOT NULL DEFAULT 0,
  status        TINYINT      NOT NULL DEFAULT 0,   -- 0 上架 / 1 下架 / 2 封禁
  create_time   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id), UNIQUE KEY uk_appid (app_id)
);

CREATE TABLE open_app_user (                        -- open_id 映射：不泄露真实 user_id
  tenant_id  BIGINT      NOT NULL,
  app_pk     BIGINT      NOT NULL,                  -- open_app.id
  user_id    BIGINT      NOT NULL,
  open_id    VARCHAR(40) NOT NULL,                  -- "ou_" + 24 随机 hex，与 user_id 无算法关联
  authorized_scopes JSON  NOT NULL,
  create_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (app_pk, user_id),
  UNIQUE KEY uk_openid (open_id)
);

CREATE TABLE pay_order (
  id           BIGINT      NOT NULL,                -- 即 prepay_id 的内部形态
  tenant_id    BIGINT      NOT NULL,
  app_pk       BIGINT      NOT NULL,
  out_trade_no VARCHAR(64) NOT NULL,                -- 第三方订单号
  user_id      BIGINT      NOT NULL DEFAULT 0,      -- 支付时回填
  amount       BIGINT      NOT NULL,                -- 分
  subject      VARCHAR(128) NOT NULL,
  status       TINYINT     NOT NULL DEFAULT 0,      -- 0 CREATED / 1 PAID / 2 CLOSED(超时) / 3 REFUNDED
  txn_id       BIGINT      NOT NULL DEFAULT 0,      -- wallet_txn.id
  expire_time  DATETIME(3) NOT NULL,                -- create+15min
  paid_time    DATETIME(3) NULL,
  notify_status TINYINT    NOT NULL DEFAULT 0,      -- 0 未通知 / 1 成功 / 2 重试耗尽
  notify_retry INT         NOT NULL DEFAULT 0,
  create_time  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_trade (app_pk, out_trade_no),       -- 第三方侧幂等
  KEY idx_expire (status, expire_time),
  KEY idx_notify (notify_status, status)
);

CREATE TABLE discovery_entry (                      -- 发现页九宫格（tenant 级运营配置）
  id        BIGINT       NOT NULL,
  tenant_id BIGINT       NOT NULL,
  title     VARCHAR(32)  NOT NULL,
  icon      VARCHAR(255) NOT NULL,
  app_pk    BIGINT       NOT NULL DEFAULT 0,        -- 关联 open_app（打开其 entry_url）；0=纯链接
  url       VARCHAR(512) NOT NULL DEFAULT '',       -- app_pk=0 时的直链（无 bridge 能力）
  sort      INT          NOT NULL DEFAULT 0,
  min_app_version VARCHAR(16) NOT NULL DEFAULT '',
  status    TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (id), KEY idx_tenant (tenant_id, status, sort)
);
```

Redis（授权态短命数据不落库）：
- `open:code:{code}` → `{app_pk, user_id, scope}`，TTL 300s，**GETDEL 一次性消费**。
- `open:token:{access_token}` → `{app_pk, open_id, scope}`，TTL 7200s。access_token = "ot_" + 32 随机 hex。

---

## 3. 协议改动

WS/消息协议**零改动**（分享卡片走 `CustomContent`，D20 逃生口的正确用例——第三方内容非平台核心语义，不进强类型）。

error.proto 新增 9xxx 开放平台段：

```proto
// 9xxx 开放平台
APP_NOT_FOUND = 9001; APP_SUSPENDED = 9002; SCOPE_DENIED = 9003;
AUTH_CODE_INVALID = 9004;               // 过期/已用/伪造
OPEN_SIGN_INVALID = 9005; OPEN_REPLAY = 9006;
ORDER_NOT_FOUND = 9101; ORDER_STATE_INVALID = 9102; ORDER_EXPIRED = 9103;
DOMAIN_NOT_ALLOWED = 9201;              // bridge 调用页面不在 app 白名单
```

internal.proto 无新增 service（open 只消费 WalletRpc/UserRpc 现有+D46 新增接口）。

分享卡片 CustomContent 约定（登记 protocol.md 附录）：
`custom_type = "open.app.card"`，`payload = {app_id, title, desc, icon, url}`。

---

## 4. 网页授权（OAuth2 授权码简化流）

```
H5 页面                     im-app 原生                      im-open-service              第三方后端
  │ imsdk.getAuthCode({scope})  │                                │                          │
  │────────────────────────────>│ 首次授权弹原生确认面板            │                          │
  │                             │ POST /api/v1/open/authorize     │                          │
  │                             │  {app_id, scope} (JWT)          │                          │
  │                             │────────────────────────────────>│ 校验 app/scope/status     │
  │                             │                                 │ 建 open_app_user(懒)      │
  │                             │<──────────── {code} ────────────│ 写 Redis code(300s)      │
  │<──── callback(code) ────────│                                 │                          │
  │────────────── 业务请求带 code ──────────────────────────────────────────────────────────>│
  │                             │                                 │<─ POST /open/api/oauth/token
  │                             │                                 │   {app_id, secret, code} │
  │                             │                                 │─ {access_token, open_id, │
  │                             │                                 │    expires_in, scope} ──>│
  │                             │                                 │<─ GET /open/api/user/info │
  │                             │                                 │   Bearer access_token     │
  │                             │                                 │─ {open_id, nickname, avatar}
```

规则：

1. code **一次性**（GETDEL），300s 过期；code 与 app 绑定，跨 app 使用 → 9004。
2. `snsapi_base`：仅换 open_id（静默，不弹面板）；`snsapi_userinfo`：首次弹原生授权面板，用户同意后记入 authorized_scopes，后续静默。
3. user/info 返回 open_id、昵称、头像；**手机号/username/真实 user_id 永不下发**。
4. secret 校验 = bcrypt 比对；错误率限流（每 app 10 次/分钟失败即熔断 5 分钟），防爆破。
5. 同一用户在不同 app 的 open_id 不同且不可互推（uk_openid 随机生成）。

---

## 5. JSSDK 与 WebView 容器（im-app 侧核心）

### 5.1 容器安全边界（安全审查重点，逐条落实）

1. WebView 用 `flutter_inappwebview`，bridge 通过 `addJavaScriptHandler` 单通道 `imsdk.invoke(api, params) → Future`；**注入的 JS 只暴露 postMessage 封装，不暴露任何原生对象**。
2. **每次 bridge 调用**取 WebView 当前 URL：非 https → 拒；host 不在该 app `domain_whitelist` → 9201。app 内跳转到白名单外域名 = 降级纯浏览模式（bridge 全部拒绝，导航栏显示警示）。
3. 敏感 API 矩阵：`getAuthCode`/`pay`/`share` 需 scope 授权 + 用户手势触发（防静默拉起）；`close`/`getEnv` 免费。
4. **支付金额展示以服务端为准**：收银台原生 UI 展示的金额/商户名从 `GET /api/v1/open/orders/{prepay_id}` 拉取，**绝不信任 H5 传参**（H5 只传 prepay_id）。
5. 容器禁用：file:// 与自定义 scheme（仅放行 https + 内部回调 scheme）、长按保存页面注入、JS 弹窗伪装原生（alert 标注来源域名）。
6. Cookie/存储按 app 隔离（inappwebview incognito 或按 app 分区），防 app 间横向读取。
7. 打开来源限制：仅 discovery_entry 与 `open.app.card` 卡片可进入容器；卡片 url 必须命中该 app 白名单，否则按普通外链浏览器打开（无 bridge）。

### 5.2 JSSDK API 面（v1）

```js
imsdk.getEnv()                     → {inApp:true, appVersion, platform}
imsdk.getAuthCode({scope})         → {code}                    // §4
imsdk.pay({prepayId})              → {status:'paid'|'cancel'}  // §6
imsdk.share({title,desc,icon,url}) → {status:'sent'|'cancel'}  // 原生会话选择器发 open.app.card
imsdk.close()
```

SDK 以静态 js 文件发布（`https://{host}/open/js/imsdk-1.0.0.js`），内部仅做 bridge 封装 + 非容器环境降级报错。**MVP 不做 wx.config 式 URL 签名**：能力控制靠 scope + 域名白名单已闭环（签名机制防的是「白名单页面被 XSS 后盗刷」，列二阶段）。

---

## 6. 支付（依赖 D46 WalletRpc）

### 6.1 统一下单（第三方服务端 → 平台）

```
POST /open/api/pay/unified-order
Headers: X-App-Id, X-Timestamp, X-Nonce, X-Sign
Body: {out_trade_no, amount, subject, notify_url?}
→ {prepay_id, expire_time}
```

签名：`sign = HMAC-SHA256(app_secret_plain, method + path + timestamp + nonce + sha256(body))`。
> 注意：server-to-server 签名需要明文 secret 参与，而库里只存 bcrypt hash——**裁决：secret 生成时同时派生 `sign_key = HKDF(secret)` 落库存储**（sign_key 泄露不等于 secret 泄露，secret 仍只展示一次）。时间戳 ±5min 防重放 + nonce Redis SETNX 10min（9006）。

幂等：`uk_trade(app_pk, out_trade_no)` 撞键返回原单（金额不一致则报 9102）。

### 6.2 支付执行（App 内）

```
H5: imsdk.pay({prepayId})
→ 原生收银台：GET /api/v1/open/orders/{prepay_id}（金额/subject/商户名，服务端数据）
→ 用户输 PIN → POST /api/v1/open/orders/{prepay_id}/pay {pin}
   open 模块：CAS pay_order CREATED→PAID 前置检查（过期 9103）
   → WalletRpc.Pay(user→MERCHANT(app_pk), amount, biz=pay.order, biz_id=order.id,
                    idem_key="payorder:"+order.id, pin)
   → 成功：事务回填 status=PAID/txn_id/paid_time/user_id → 触发回调（§6.3）
   → 失败：透传 8xxx（余额不足/PIN 错），订单留在 CREATED 可重试
→ bridge 返回 H5 {status:'paid'}（前端信号仅供跳转，业务发货以服务端回调为准——文档必须向第三方强调）
```

顺序说明：先 CAS 订单再扣款，扣款失败回滚订单 CAS（同一 open 库事务包不住 wallet 事务——**实现为：先调 Pay（幂等），成功后再 CAS 订单**；若 CAS 时发现已 PAID（并发双击）则调 RefundPay 冲正，幂等键 "payfix:"+order.id。Review 时重点核对这段并发路径）。

### 6.3 回调通知（平台 → 第三方服务端）

`POST notify_url`，body `{prepay_id, out_trade_no, amount, status:'PAID', paid_time, nonce, timestamp}` + 同 §6.1 签名（sign_key）。
成功=第三方返回 `{"code":0}`；否则 NotifySweeper 重试：15s/1m/5m/30m/2h 共 5 次，耗尽置 notify_status=2 并告警。第三方也可主动查单 `GET /open/api/pay/orders/{out_trade_no}`（兜底，文档必须写明「查单为准」）。

### 6.4 退款 `POST /open/api/pay/refund {out_trade_no}`（签名同上）

CAS PAID→REFUNDED + `WalletRpc.RefundPay(txn_id, idem_key="refund:"+order.id)`（MERCHANT→用户反向分录）。MVP 仅全额退款。

### 6.5 后台任务

| 任务 | 周期 | 逻辑 |
|------|------|------|
| OrderExpireSweeper | 60s | CREATED 且过期 → CLOSED |
| NotifySweeper | 15s | notify_status=0 且 status∈(PAID,REFUNDED) 按退避重试 |

---

## 7. REST 汇总

```
-- App 内用户态（JWT）
GET  /api/v1/open/discovery                  → 九宫格（按 tenant + min_app_version 过滤）
POST /api/v1/open/authorize {app_id, scope}  → {code}
GET  /api/v1/open/orders/{prepay_id}         → 收银台展示数据（amount/subject/app name/icon）
POST /api/v1/open/orders/{prepay_id}/pay {pin}
-- 第三方 server-to-server（签名）
POST /open/api/oauth/token
GET  /open/api/user/info
POST /open/api/pay/unified-order
GET  /open/api/pay/orders/{out_trade_no}
POST /open/api/pay/refund
-- 管理后台（im-admin，RBAC）
POST /admin/v1/open/apps（录入 app，返回一次性 secret）  PUT .../apps/{id}  灰度/封禁/白名单管理
POST /admin/v1/open/discovery-entries ...
```

---

## 8. im-app（Flutter）实现要点

- `features/discovery/`：发现页九宫格（朋友圈入口同页，见 moments 文档）。
- `features/open/`：`OpenWebViewPage`（容器 §5.1 全部规则）、授权确认面板（app icon/名称/scope 说明/一次授权后静默）、原生收银台弹层（金额大字号 + 商户名 + PIN 键盘复用 wallet 组件）。
- bridge 实现集中一个 `OpenBridgeController`：api 分发表 + 域名校验拦截器 + scope 校验；**单元测试覆盖：白名单外调用全拒、金额篡改无效（金额根本不从 H5 读）**。
- 分享落地：收到 `open.app.card` 的会话渲染卡片气泡；点击校验域名白名单后进容器。

---

## 9. 与现有决策一致性核对 / 注意事项

| 关注点 | 结论 |
|--------|------|
| D19/D20 | WS 零改动；卡片走 CustomContent（逃生口正确用例） |
| D46 | 支付/退款全走 WalletRpc，open 模块不碰账务表；MERCHANT 科目 owner=open_app.id |
| D3 | 全表 tenant_id；`/open/api/*` 无 JWT，**tenant 由 app_id 反查后手动装载 TenantContext**（server-to-server 接口是拦截器盲区，实现必须显式处理，高危遗漏点） |
| D5 | 新模块 im-open-service，跨模块 gRPC |
| D24 | 无新中间件；token/code 用现有 Redis |

易错清单：

1. `/open/api/*` 的 TenantContext 手动装载（上表）——漏了会串租户，**P0 级审查项**。
2. code 消费必须 GETDEL（原子），GET+DEL 两步会被并发换两次 token。
3. 回调/查单文档必须写「支付结果以服务端回调或查单为准，H5 回调仅 UI 信号」。
4. 收银台金额只从服务端拉（§5.1-4），bridge pay 参数只有 prepay_id。
5. app 封禁（status=2）要同时：拒绝 authorize/下单/bridge 调用 + 已发 token 失效（token value 里带 app_pk，校验时查 app status）。
6. discovery_entry.url 直链（app_pk=0）打开的页面**无 bridge**——容器判断依据是 app_pk 而非 URL。
7. 支付并发双击：§6.2 幂等键 + 冲正路径要有集成测试。
8. secret/sign_key 不入日志；回调 body 含金额，日志脱敏可不做（非敏感）但签名值不打。

## 10. 二阶段 / Open Questions（登记 CLAUDE.md）

- MERCHANT 结算/出金语义（虚拟币形态下商户余额如何消化：抵扣平台服务费？兑换？）
- 开发者自助后台 + app 审核工作流
- URL 签名（wx.config 式）防白名单页 XSS 盗刷
- 模板消息/服务通知（向用户推送，复用 SYSTEM 会话是现成路径）
- 小程序运行时（重决策，独立评估）
