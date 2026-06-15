# 单机 Docker 部署运维手册（im-project MVP）

> 适用范围：单台云服务器 + Docker，目标 1~5 万同时在线（D4）。
> 上游事实来源：`deploy/docker-compose/`、各模块 Dockerfile、docs/architecture.md §13、docs/middleware-selection.md。
> 本文是给运维（Jade）的"照着做"手册，不替代架构文档。

---

## 0. 先决条件与机器规格

**软件**

- 宿主机**唯一需要安装的是 Docker 本身**。MySQL / Redis / RabbitMQ / MinIO **全部以 Docker 容器运行**（compose 里用官方镜像），不在宿主机单独 `apt install`。`docker compose up -d` 会自动拉镜像并启动，这一步就等于"安装"。
- Docker Engine ≥ 24，自带 `docker compose` v2 插件（用 `docker compose` 而非旧的 `docker-compose`）。
- 编译镜像在本机进行（compose `--profile app` 会用 Maven/Rust/Node 多阶段构建），首次构建吃 CPU 和内存，建议构建机内存 ≥ 8GB。

**安装 Docker（干净服务器才需要；已装可跳过）**

以 Ubuntu 为例：

```bash
curl -fsSL https://get.docker.com | sudo sh   # 装 Engine + compose 插件
docker --version && docker compose version     # 验证，compose 须为 v2
sudo usermod -aG docker $USER                  # 免 sudo 用 docker（重新登录生效，可选）
```

**机器规格建议（5 万在线档）**

| 资源 | 建议 | 说明 |
|---|---|---|
| CPU | 8 vCPU 起 | im-server(JVM 虚拟线程) + MySQL 是主要消耗者 |
| 内存 | 32GB | 见下方分配；MySQL/JVM/Redis 都要留够 |
| 磁盘 | SSD，≥ 200GB | 消息表日增长大（日百万消息≈年 3.65 亿行，见 middleware-selection §1），MinIO 媒体另算 |
| 网络 | 公网带宽按消息量估，长连接本身带宽小 | TLS 在前置卸载 |

内存粗分配参考：MySQL `innodb_buffer_pool` 8~12GB、JVM 堆 6~8GB、Redis 2~4GB、RabbitMQ 1~2GB、MinIO + 网关 + 系统余量其余。**网关单连接内存到 KB 级**（Rust 无 GC，architecture §2.1），5 万连接的瓶颈不在网关内存，而在文件描述符和 MySQL。

**内核与 ulimit（5 万长连接的硬前提，最容易被忽略）**

5 万 WebSocket = 5 万 fd 在网关进程上。默认 `nofile=1024` 必爆。在宿主机设置：

```bash
# /etc/sysctl.conf
net.core.somaxconn = 32768
net.ipv4.tcp_max_syn_backlog = 8192
net.ipv4.ip_local_port_range = 1024 65535
fs.file-max = 2000000
# 生效
sudo sysctl -p
```

并给网关容器放开 fd（在 compose 的 `im-gateway` 服务下加）：

```yaml
    ulimits:
      nofile:
        soft: 1000000
        hard: 1000000
```

---

## 1. 把代码放到服务器、配置 .env

`--profile app` 是**从源码现场构建镜像**（compose `build.context: ../..`，要读 `im-proto` + 各模块源码），所以源码必须先在服务器上。三选一：

**方式 A：服务器上 git clone（推荐，后续 `git pull` 发版最干净）**

```bash
git clone <你的仓库地址> im_chat
cd im_chat/deploy/docker-compose
```

**方式 B：本地打包上传（没接 git 远程时用）**

在本地项目根目录执行，**务必排除构建产物**（体积几个 G 且服务器会重新构建，传上去纯浪费）：

```bash
rsync -av --exclude='.git' --exclude='node_modules' --exclude='target' \
  --exclude='dist' ./ user@服务器IP:/opt/im_chat/
# 然后在服务器上：
cd /opt/im_chat/deploy/docker-compose
```

> `.dockerignore` 只管 Docker build 上下文，不管 scp/rsync，所以上传时要手动排除上面这些目录。

**方式 C：预构建镜像 + 仅传配置（服务器内存紧/想零编译时用）**

在本地或 CI 构建好三个镜像并推到镜像仓库（Docker Hub / 阿里云 ACR 等），服务器上**只放 `docker-compose.yml` + `.env`，不放源码**，并把 compose 里 `im-server`/`im-gateway`/`im-web` 三个服务的 `build:` 块改成 `image: 你的仓库/镜像:tag`。服务器侧 `docker compose --profile app up -d`（不带 `--build`）直接拉镜像跑，零编译、内存压力最小。

---

配置 `.env`：

```bash
cp .env.example .env
vim .env
```

编辑 `.env`，**逐项替换**（这是生产，不是联调）：

- `MYSQL_ROOT_PASSWORD` / `REDIS_PASSWORD` / `RABBITMQ_PASSWORD` / `MINIO_ROOT_PASSWORD`：全部换强密码。
- `JWT_SECRET`：≥ 32 字符的随机串（`openssl rand -base64 48`）。换它等于使全部已签发 token 失效，部署前定好不要再动。
- `IM_GATEWAY_ALLOWED_ORIGINS`：改成真实 Web 域名（如 `https://im.example.com`），默认的 `localhost` 在生产会拒绝正常握手或放行任意来源。
- `GRAFANA_PASSWORD`：若启用观测栈，换掉默认 `admin`。

`.env` 不入库（已在 .gitignore）。

---

## 2. 先起中间件，确认健康

分两步走是刻意的：中间件先就绪，应用启动自检才能通过。

```bash
docker compose up -d              # 仅 mysql/redis/rabbitmq/minio + minio-init
docker compose ps                 # 等所有服务 healthy
```

首启会自动完成（无需手动操作）：

- **MySQL**：执行 `init/mysql/01-schema.sql`（15 张表 + dev 租户种子）。⚠️ 该脚本**只在数据目录为空的首次启动执行**。后续表结构演进走 Flyway，不要再改这个文件，也不要靠删库重跑它来"升级"。
- **MinIO**：`minio-init` 一次性建私有 bucket `im-media`（D10 预签名直传），跑完即退出，`docker compose ps` 里显示 exited(0) 属正常。

验证：

```bash
docker compose exec mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "use im; show tables;" | wc -l   # 应有 15 张表
docker compose logs minio-init | tail        # 看到 "bucket im-media ready"
```

---

## 3. 构建并启动应用

```bash
docker compose --profile app up -d --build
```

注意点：

- **构建上下文是仓库根目录**（compose 里 `context: ../..`），因为 im-server 和 im-gateway 都要读同级 `im-proto`。从 `deploy/docker-compose/` 目录执行即可，不要 cd 到别处。
- 三个镜像：`im-server`(Java)、`im-gateway`(Rust)、`im-web`(nginx 静态)。Rust 首次编译较慢，已配 cargo/maven 构建缓存挂载，二次构建会快。
- **im-server 启动自检 fail-fast**（`IM_STARTUP_CHECK_ENABLED=true`）：MySQL 表、Redis、RabbitMQ exchange、MinIO bucket、workerId 租约任一失败直接退出。容器起不来先看 `docker compose logs im-server`，自检日志会指明哪一项没过。
- RabbitMQ 的 `im.events` exchange 和队列由 im-server 启动时声明，不在编排层建。

端口（容器对宿主）：

| 服务 | 端口 | 用途 |
|---|---|---|
| im-web | 80 | 前端静态页 + 反代 |
| im-gateway | `${GATEWAY_PORT:-8082}` -> 容器内 8080 | WebSocket（容器内明文，TLS 前置卸载） |
| im-server | 8081 / 9091 | REST / gRPC（gRPC 仅网关内部用） |

健康检查：

```bash
curl -fsS http://127.0.0.1:8081/actuator/health/readiness   # im-server
curl -fsS http://127.0.0.1:${GATEWAY_PORT:-8082}/health      # 网关
curl -fsS http://127.0.0.1:${GATEWAY_PORT:-8082}/metrics     # 网关 Prometheus 指标
```

---

## 4. 前置 nginx 与 TLS（生产必做）

容器内一律明文，**TLS 在最前面的 nginx / 云 SLB 卸载**（architecture §13.8）。最小前置配置：把 `https://你的域名` 终止 TLS 后，`/` 与 `/api/` 转给 im-web:80，`/ws` 升级为 WebSocket 转给宿主机 `${GATEWAY_PORT:-8082}`，或在 Docker 内网中转给 `im-gateway:8080`。客户端连 `wss://你的域名/ws`。

> `im-web/nginx.conf` 运行在 Docker 网络内，所以 `/ws` 反代目标保持 `http://im-gateway:8080`；宿主机对外端口由 compose 的 `GATEWAY_PORT` 控制，默认 `8082`。

---

## 5. 观测栈（可选但建议开）

```bash
docker compose --profile obs up -d
```

- Prometheus :9090（抓取配置 `config/prometheus.yml`）、Grafana :3000、Loki :3100。
- 网关 `/metrics` 已暴露；至少盯：在线连接数、握手速率、上行 gRPC 时延、下行 ack 超时断连率、RabbitMQ 队列积压、MySQL 慢查询。

---

## 6. 端口暴露与防火墙（安全红线）

只有 **80 / 443**（经前置 nginx）该对公网开放。以下端口**绝不暴露公网**，用防火墙/安全组限制，或把 compose 端口映射改成只绑 `127.0.0.1`：

`3306`(MySQL)、`6379`(Redis)、`5672/15672`(RabbitMQ + 管理台)、`9000/9001`(MinIO + 控制台)、`3000/9090/3100`(观测栈)、`8081/9091`(im-server)、`${GATEWAY_PORT:-8082}`(网关，应走前置而非直连)。

当前 `.env` 把这些端口直接映射到宿主，单机部署时建议改为：

```yaml
    ports:
      - "127.0.0.1:3306:3306"   # 示例：MySQL 只本机可达
```

---

## 7. 部署前必修清单（基于现状核对）

| 项 | 现状 | 处理 |
|---|---|---|
| Web `/ws` 反代地址 | `im-web/nginx.conf` 指向 `im-gateway:8080` | Docker 内网地址，保持容器内端口 8080；宿主机端口由 `GATEWAY_PORT` 控制 |
| 中间件端口 | `.env` 默认映射到 `0.0.0.0` | 改绑 `127.0.0.1` 或防火墙封禁 |
| 默认密码 / JWT_SECRET | `.env.example` 是弱口令 | 全部替换 |
| 网关 fd 上限 | compose 未设 ulimits | 加 `nofile` 与 sysctl，否则连接数上不去 |
| ALLOWED_ORIGINS | 默认 localhost | 改真实域名 |

---

## 8. 数据持久化与备份

有状态数据全在命名卷：`mysql-data`、`redis-data`、`rabbitmq-data`、`minio-data`。

- **MySQL**：每日 `mysqldump`（或物理备份）到异机/对象存储；binlog 已设保留 7 天（`binlog_expire_logs_seconds=604800`），可做时间点恢复。这是**第一优先级**，消息和业务数据都在这里。
- **Redis**：已开 AOF everysec（`--appendonly yes`）。Redis 是仅次于 MySQL 的关键单点。⚠️ 灾难恢复若丢失 `token_ver:*` key，已签发 token 会被判无效，用户需重新登录——这是**预期的安全降级，不是故障**（deploy/README 已说明）。
- **MinIO**：`minio-data` 卷备份；媒体文件可观，纳入备份容量规划。
- **RabbitMQ**：队列是瞬时的，无需备份；消息可靠性靠 Outbox 模式（D18）+ 消费侧按 server_msg_id 幂等保证，MQ 丢消息能从 MySQL outbox 重投。

恢复演练：定期在测试机用备份拉起一套，验证 MySQL + MinIO 能还原、应用自检能过。没演练过的备份等于没有备份。

---

## 9. 升级与发版

- **业务发版（im-server / 网关 / web）**：改代码后 `docker compose --profile app up -d --build`，compose 滚动重建对应容器。im-server 无长连接负担，可随意重启；网关重启会断开其上连接，客户端自动重连 + 增量同步补齐，不丢消息（architecture §4 网关无状态）。建议低峰发版网关。
- **表结构演进**：走 Flyway，**不要**改 `01-schema.sql` 或删库重建。
- **中间件大版本升级**：单独操作，先备份卷、读 release note，逐个来，别和应用发版混在一起。

---

## 10. 常见排障

| 现象 | 排查方向 |
|---|---|
| im-server 起不来 | `docker compose logs im-server`，看启动自检哪项失败（表/Redis/MQ/bucket/workerId） |
| 客户端连不上 WS | 前置 nginx `/ws` 是否指向宿主机 `${GATEWAY_PORT:-8082}` 或 Docker 内网 `im-gateway:8080`；`ALLOWED_ORIGINS` 是否含来源域名；fd 是否打满 |
| 连接数上不去/到几千就掉 | sysctl `somaxconn`、容器 `nofile` ulimit |
| 消息发出去对端收不到 | 查 Redis 路由表 `route:{tenant}:{uid}:{platform_class}`、RabbitMQ `push.gw.{instance}` 队列是否积压、网关 ack 超时断连指标 |
| 握手被限流 | 网关 `IM_GATEWAY_HANDSHAKE_RATE_LIMIT_*`，大规模重连风暴时按需调高 |
| workerId 租约失败 | Redis 中已有同 ID 租约（多实例时见第 11 节），需为每个 im-server 实例分配唯一 workerId |

---

## 11. 横向扩展（单机内 → 多机）

架构本就是为扩展设计的（网关无状态、业务无长连接、路由全在 Redis）。按"何时扩、扩哪个、有什么坑"来看：

### 11.1 扩展顺序：先垂直，后水平

万级规模下瓶颈通常是 MySQL 和单机 CPU，不是架构。**先把单机吃满**（加 CPU/内存、调 JVM 堆、调 innodb_buffer_pool），再考虑拆。过早水平扩展只是徒增运维事故面（middleware-selection 的总原则：运维事故 > 性能上限）。

### 11.2 网关（im-gateway）——最容易水平扩展

网关无状态，路由信息全在 Redis，可直接多开。单机内可跑多个网关容器，多机时每机若干个。

- **每个实例必须有唯一 `GW_INSTANCE_ID`**：它决定下行队列 `push.gw.{instance}`，重复会导致推送串到错误实例。
- 前置 nginx/SLB 做四层或七层负载均衡分发 WS 连接。**无需会话粘性**：连接落在哪个网关，push 模块就把该用户路由写成哪个实例，下行精准投递。实例扩缩容只影响其上连接重连，客户端自动重连 + 增量同步兜底，不丢消息。
- 扩容公式参考：单网关稳态承载几万连接，按目标在线数 ÷ 单机承载 + 冗余，决定实例数。

### 11.3 im-server（Java 模块化单体）——可多实例，但有三个前提

im-server 不持有长连接，多副本理论可行，但**单机阶段优先垂直扩容（加堆/CPU）**。要多实例时务必处理：

1. **workerId 唯一**：Snowflake server_msg_id 依赖 workerId。代码已有 `WorkerIdLease`（基于 Redis 租约，启动自检会 fail-fast 拒绝重复 ID），但你必须保证每个实例拿到不同 ID（按其租约机制分配，别给两个实例配相同固定 ID）。
2. **seq 一致性天然安全**：会话级 seq 用 MySQL 行锁自增（`UPDATE conversation SET max_seq=max_seq+1`，D26），多实例并发写由数据库串行化，无空洞、回滚一致——这是多实例最关键的正确性点，已经稳了。
3. **gRPC 寻址**：当前网关 `UPSTREAM_GRPC` 是单地址 `http://im-server:9091`。多 im-server 实例需要在网关与业务之间放 gRPC 负载均衡（客户端负载均衡 / 或 nginx/envoy L7），否则所有上行只打到一个实例。这是多实例必须补的一块，MVP 单实例时不需要。

### 11.4 中间件的扩展/高可用路径（按 D24 / middleware-selection）

单机这些都是单点，水平扩展即"上高可用、上集群"，属于二阶段：

- **MySQL**：第一道保留策略归档（90 天~1 年归档到 MinIO）→ 第二道按月 RANGE 分区 → 第三道 ShardingSphere 按 conv_id 分表或整体迁 TiDB（MySQL 协议兼容，应用近零改动）。读压力大可先加只读副本。
- **Redis**：主从 + 哨兵（MVP 单机可接受，HA 是二阶段底线）；介意 license 可无感平替 Valkey。
- **RabbitMQ**：万级足够单机；百万级按 D8 的 MQ 抽象层换 Kafka（"每网关动态队列"模式需在 Kafka 上自建路由层）。
- **MinIO**：上云换 OSS/COS/S3（应用只依赖 S3 兼容 API，改配置即可），加 CDN 回源。

### 11.5 离开单机的信号与终局

当出现：单机 CPU 长期打满且垂直到顶、MySQL 成为写瓶颈、单网关连接数逼近上限——就该把中间件拆到独立节点、业务与网关各自多机，最终迁 K8s（`deploy/k8s/` 当前为空，是二阶段规划位）。架构的模块化单体（D5）保证了拆模块为微服务时 in-process gRPC 换成网络地址即可，代码零改动。

---

## 附：最小上线命令序列

```bash
# 干净服务器先装 Docker：curl -fsSL https://get.docker.com | sudo sh
git clone <仓库地址> im_chat && cd im_chat/deploy/docker-compose   # 或 rsync 上传源码
cp .env.example .env && vim .env          # 改密码/JWT/Origin；如需改网关宿主机端口，设置 GATEWAY_PORT
docker compose up -d                       # 中间件
docker compose ps                          # 等 healthy
docker compose --profile app up -d --build # 应用
docker compose --profile obs up -d         # 观测栈(可选)
# 前置 nginx 配 TLS + 反代，防火墙只放 80/443
```
