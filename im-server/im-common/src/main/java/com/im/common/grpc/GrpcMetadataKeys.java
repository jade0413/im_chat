package com.im.common.grpc;

import io.grpc.Metadata;

public final class GrpcMetadataKeys {

  public static final Metadata.Key<String> TENANT_ID = Metadata.Key.of(
      "tenant_id", Metadata.ASCII_STRING_MARSHALLER);
  public static final Metadata.Key<String> TRACE_ID = Metadata.Key.of(
      "trace_id", Metadata.ASCII_STRING_MARSHALLER);
  public static final Metadata.Key<String> CALLER = Metadata.Key.of(
      "caller", Metadata.ASCII_STRING_MARSHALLER);

  private GrpcMetadataKeys() {
  }
}
