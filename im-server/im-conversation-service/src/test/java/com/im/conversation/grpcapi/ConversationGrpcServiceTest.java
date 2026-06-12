package com.im.conversation.grpcapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import com.im.conversation.service.ConversationService;
import com.im.proto.body.ConvInfo;
import com.im.proto.common.ConvType;
import com.im.proto.rpc.GetMembersReq;
import com.im.proto.rpc.GetMembersResp;
import com.im.proto.rpc.ResolveConvReq;
import com.im.proto.rpc.ResolveConvResp;
import io.grpc.stub.StreamObserver;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConversationGrpcServiceTest {

  @Mock
  private ConversationService conversationService;

  @Test
  void returnsResolvedConversation() {
    ResolveConvReq request = ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setToUserId(200L)
        .build();
    ConvInfo conv = ConvInfo.newBuilder()
        .setConvId(501L)
        .setType(ConvType.C2C)
        .setPeerUserId(200L)
        .build();
    when(conversationService.resolve(request)).thenReturn(conv);

    CapturingObserver<ResolveConvResp> observer = new CapturingObserver<>();
    new ConversationGrpcService(conversationService).resolveConv(request, observer);

    assertThat(observer.completed).isTrue();
    assertThat(observer.value.getCode()).isEqualTo(ErrorCode.OK.code());
    assertThat(observer.value.getConv().getConvId()).isEqualTo(501L);
  }

  @Test
  void returnsBusinessErrorCode() {
    ResolveConvReq request = ResolveConvReq.newBuilder()
        .setFromUserId(100L)
        .setConvId(501L)
        .build();
    when(conversationService.resolve(request)).thenThrow(new ImException(ErrorCode.NOT_CONV_MEMBER));

    CapturingObserver<ResolveConvResp> observer = new CapturingObserver<>();
    new ConversationGrpcService(conversationService).resolveConv(request, observer);

    assertThat(observer.value.getCode()).isEqualTo(ErrorCode.NOT_CONV_MEMBER.code());
    assertThat(observer.completed).isTrue();
  }

  @Test
  void returnsMembers() {
    when(conversationService.getMemberUserIds(501L)).thenReturn(List.of(100L, 200L));

    CapturingObserver<GetMembersResp> observer = new CapturingObserver<>();
    new ConversationGrpcService(conversationService)
        .getMembers(GetMembersReq.newBuilder().setConvId(501L).build(), observer);

    assertThat(observer.value.getUserIdsList()).containsExactly(100L, 200L);
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
