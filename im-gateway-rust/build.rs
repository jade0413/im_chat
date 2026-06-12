fn main() -> Result<(), Box<dyn std::error::Error>> {
    let protoc = protoc_bin_vendored::protoc_bin_path()?;
    std::env::set_var("PROTOC", protoc);

    let proto_root = "../im-proto/proto";
    tonic_build::configure()
        .build_client(true)
        .build_server(false)
        .compile_protos(
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
