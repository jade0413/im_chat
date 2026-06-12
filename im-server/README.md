# im-server — Java 21 + Spring Boot 3.2 业务侧

Maven 多模块「模块化单体」：模块边界=未来微服务边界，模块间只许走 gRPC 接口定义的契约，
MVP 由 im-bootstrap 合并为单进程部署，二阶段按需拆分独立部署。
全局开启虚拟线程（spring.threads.virtual.enabled=true）。

## 模块

```text
im-server
├── im-proto-java
├── im-common
├── im-user-service
├── im-message-service
├── im-conversation-service
├── im-group-service
├── im-file-service
├── im-push-service
└── im-bootstrap
```

## 本地工具链

- JDK 21
- Maven 3.9+

## 验证

```bash
cd im-server
mvn verify
```

`im-proto-java` 是唯一执行 protobuf 生成的模块，源文件来自 `../im-proto/proto`。

如果 Maven 只由 IDEA 提供，可以使用：

```bash
cd im-server
JAVA_HOME=/Users/yupeiyan/Library/Java/JavaVirtualMachines/temurin-21.0.11/Contents/Home \
  "/Applications/IntelliJ IDEA.app/Contents/plugins/maven/lib/maven3/bin/mvn" verify
```

`TenantContext` 使用普通 `ThreadLocal` 并在 finally 中清理；项目不依赖 JDK preview API，启动不需要 `--enable-preview`。

## 启动

```bash
cd im-server
mvn -pl im-bootstrap -am package
java -jar im-bootstrap/target/im-bootstrap-0.1.0-SNAPSHOT.jar
```

健康检查：

```bash
curl http://localhost:8081/actuator/health
```

默认端口：

- REST/Actuator：`8081`
- gRPC GatewayAuth/Uplink/ConnEvent：`9091`（可用 `IM_GRPC_PORT` 覆盖）
