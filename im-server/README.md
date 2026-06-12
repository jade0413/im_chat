# im-server — Java 21 + Spring Boot 3.2 业务侧

Maven 多模块「模块化单体」：模块边界=未来微服务边界，模块间只许走 gRPC 接口定义的契约，
MVP 由 im-bootstrap 合并为单进程部署，二阶段按需拆分独立部署。
全局开启虚拟线程（spring.threads.virtual.enabled=true）。
