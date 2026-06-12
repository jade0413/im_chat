package com.im.message.grpcapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.message.service.MessageQueryService;
import com.im.proto.body.MsgPush;
import com.im.proto.rpc.PullMsgsReq;
import com.im.proto.rpc.PullMsgsResp;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MessageGrpcServiceTest {

  @Mock
  private MessageQueryService messageQueryService;

  @Test
  void pullsMessages() {
    PullMsgsReq request = PullMsgsReq.newBuilder()
        .setConvId(501L)
        .setBeginSeq(1L)
        .setEndSeq(2L)
        .setLimit(20)
        .build();
    when(messageQueryService.pullForRpc(request)).thenReturn(List.of(
        MsgPush.newBuilder().setConvId(501L).setSeq(1L).build(),
        MsgPush.newBuilder().setConvId(501L).setSeq(2L).build()));

    CapturingObserver<PullMsgsResp> observer = new CapturingObserver<>();
    new MessageGrpcService(messageQueryService).pullMsgs(request, observer);

    assertThat(observer.value.getMsgsList()).extracting(MsgPush::getSeq).containsExactly(1L, 2L);
    assertThat(observer.completed).isTrue();
  }

  private static final class CapturingObserver<T> implements StreamObserver<T> {

    private T value;
    private boolean completed;

    @Override
    public void onNext(T value) {
      this.value = value;
    }

    @Override
    public void onError(Throwable t) {
      throw new AssertionError(t);
    }

    @Override
    public void onCompleted() {
      completed = true;
    }
  }
}
