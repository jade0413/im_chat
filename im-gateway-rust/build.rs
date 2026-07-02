fn main() -> Result<(), Box<dyn std::error::Error>> {
    let protoc = protoc_bin_vendored::protoc_bin_path()?;
    std::env::set_var("PROTOC", protoc);

    let proto_root = "../im-proto/proto";
    // R1：所有 bytes 字段生成为 bytes::Bytes（引用计数），
    // Frame.body / PushEnvelope.body / UplinkReq.body 的 clone 不再深拷贝，
    // 且从 Bytes 解码时 body 是输入缓冲的零拷贝切片。
    let mut prost_config = prost_build::Config::new();
    prost_config.bytes(["."]);
    tonic_build::configure()
        .build_client(true)
        .build_server(false)
        .compile_protos_with_config(
            prost_config,
            &[
                format!("{proto_root}/ws/frame.proto"),
                format!("{proto_root}/rpc/gateway.proto"),
            ],
            &[proto_root.to_string()],
        )?;

    println!("cargo:rerun-if-changed={proto_root}/ws/frame.proto");
    println!("cargo:rerun-if-changed={proto_root}/rpc/gateway.proto");
    Ok(())
}
