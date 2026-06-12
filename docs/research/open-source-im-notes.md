# 开源 IM 调研笔记：OpenIM / Tinode / Matrix

> 结论先行：**消息模型抄 OpenIM，会话抽象学 Tinode，Matrix 只借鉴思想不抄实现。**

## 1. OpenIM（Go，github.com/openimsdk/open-im-server）

### 架构流水线
```
client ──ws──> msggateway ──MQ(kafka)──> msgtransfer ──> mongo/redis 落库
                                   └──> push ──> 在线推送 / 离线第三方推送
api/rpc 服务群：user / friend / group / msg / conversation / third / auth
依赖：MongoDB(消息) + Redis(seq/缓存) + Kafka(削峰解耦) + MinIO(对象)
```

### 最值得抄的：seq + 收件箱模型
- 每个会话维护一个**单调递增的 seq**（Redis INCR 产生），每条消息落库前分配 seq
- 客户端记录每个会话的 local max seq；上线/收到推送时对比 server max seq
- 缺口 = [localMaxSeq+1, svrMaxSeq]，按区间拉取 → **离线消息、多端同步、丢消息补偿全是同一套机制**
- 推送的消息 seq == localMax+1 则无缝衔接，否则触发拉取对齐 → 保证有序 + 必达
- 消息双 ID：clientMsgID（客户端 UUID，幂等去重）+ serverMsgID（服务端生成）

### 其他可借鉴
- 消息先写 Redis（最近 N 条缓存）+ 异步批量落库，读多写少时热数据全在 Redis
- 已读回执也走 seq：记录 per-user per-conversation 的 read_seq，一个数字搞定
- 消息撤回 = 发一条特殊类型的通知消息 + 修改原消息状态，不物理删除

### 对我们的取舍
- Kafka → RabbitMQ（万级规模够用，D8）
- MongoDB → MySQL（团队熟悉度优先，消息表按 tenant_id+conversation_id 索引，量大再分表）
- 三段流水线保留思想，但 MVP 里 msgtransfer/push 是 im-server 内的模块而非独立进程

## 2. Tinode（Go，github.com/tinode/chat）

### 核心抽象：Session / User / Topic
- **一切皆 Topic**：p2p 单聊是 topic，群是 topic，系统通知 me-topic 也是 topic
- Server 自我定位是 "IM router + store"，极度克制，业务都在客户端/bot
- 协议支持 WS / long polling / gRPC 多接入

### 对我们的借鉴
- **会话统一抽象**：我们的 conversation 表带 type 字段（C2C / GROUP / CS_SESSION），
  路由、未读数、已读、列表逻辑全部统一处理——客服会话只是一种新 type，
  这正是"IM 兼客服系统"的关键设计（D6）
- me-topic 思想 → 我们的"系统通知会话"（每用户一个，推送审核/坐席分配等系统事件）

## 3. Matrix（Synapse/Dendrite，spec.matrix.org）

### 设计
- 联邦制：房间数据在所有参与 homeserver 间复制，无中心
- 房间历史 = **事件 DAG**（有向无环图），每个事件引用前驱，fork 用状态解析算法确定性合并
- 事件分两类：message event（消息）与 state event（房间名/权限等持久状态）
- CAP 取 AP 舍 C

### 对我们的取舍
- **不采用** DAG / 联邦：我们是中心化多租户 SaaS，单写入点 + seq 全序即可，DAG 是为去中心化付出的复杂度
- **借鉴**：消息事件化（撤回/编辑/已读都是事件，统一走消息管道）；state 与 message 分离的思想 → 我们把"会话元数据变更"（改群名、加人）做成系统消息类型，天然获得多端同步

## 4. 多租户客服参考（Chatwoot 模式）

- 共享库 + account_id（即我们的 tenant_id）行级隔离，行业主流
- 角色：tenant 下有 agent（坐席）/ visitor（访客，可匿名，临时账号升级正式）
- 客服会话生命周期：open → assigned → resolved，分配策略可插拔
- 我们的映射：CS_SESSION 类型会话 + user_type 字段 + 第二阶段的 assignment 模块

## 来源

- https://github.com/openimsdk/open-im-server 、https://docs.openim.io
- OpenIM seq 设计解析：https://www.cnblogs.com/OpenIM/p/16021044.html 、https://zhuanlan.zhihu.com/p/394077398
- https://github.com/tinode/chat （docs/API）
- https://spec.matrix.org/latest/ 、Matrix Event Graph 论文 https://arxiv.org/pdf/2011.06488
