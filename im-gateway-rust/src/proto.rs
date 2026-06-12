pub mod im {
    pub mod rpc {
        pub mod v1 {
            tonic::include_proto!("im.rpc.v1");
        }
    }

    pub mod ws {
        pub mod v1 {
            tonic::include_proto!("im.ws.v1");
        }
    }
}
