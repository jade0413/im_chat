package com.im.user.grpcapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.im.proto.rpc.CheckRelationReq;
import com.im.proto.rpc.CheckRelationResp;
import com.im.user.service.AgentService;
import com.im.user.service.AuthService;
import com.im.user.service.RelationService;
import com.im.user.service.RelationService.RelationCheckResult;
import com.im.user.service.VisitorUserService;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserRpcGrpcServiceTest {

  @Mock
  private RelationService relationService;

  @Mock
  private VisitorUserService visitorUserService;

  @Mock
  private AgentService agentService;

  @Mock
  private AuthService authService;

  @Test
  void returnsRelationCheckResult() {
    when(relationService.check(100L, 200L))
        .thenReturn(new RelationCheckResult(true, false));

    CapturingObserver<CheckRelationResp> observer = new CapturingObserver<>();
    new UserRpcGrpcService(relationService, visitorUserService, agentService, authService).checkRelation(CheckRelationReq.newBuilder()
        .setFromUserId(100L)
        .setToUserId(200L)
        .build(), observer);

    assertThat(observer.value.getBlocked()).isTrue();
    assertThat(observer.value.getFriendRequiredUnmet()).isFalse();
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
