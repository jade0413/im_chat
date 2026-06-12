# syntax=docker/dockerfile:1.7

FROM rust:1.96-slim-bookworm AS build
WORKDIR /workspace

RUN apt-get update && \
    apt-get install -y --no-install-recommends pkg-config libssl-dev ca-certificates && \
    rm -rf /var/lib/apt/lists/*

COPY im-proto ./im-proto
COPY im-gateway-rust ./im-gateway-rust

RUN --mount=type=cache,target=/usr/local/cargo/registry \
    --mount=type=cache,target=/workspace/im-gateway-rust/target \
    cd im-gateway-rust && \
    cargo build --release --locked && \
    cp target/release/im-gateway-rust /tmp/im-gateway-rust

FROM debian:bookworm-slim
WORKDIR /app

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl ca-certificates && \
    rm -rf /var/lib/apt/lists/* && \
    useradd --system --create-home --shell /usr/sbin/nologin im

COPY --from=build /tmp/im-gateway-rust /usr/local/bin/im-gateway-rust

USER im
EXPOSE 8080
HEALTHCHECK --interval=15s --timeout=5s --retries=10 \
  CMD curl -fsS http://127.0.0.1:8080/health >/dev/null || exit 1

ENTRYPOINT ["im-gateway-rust"]
