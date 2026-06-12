package com.im.common.uplink;

import com.im.common.error.ErrorCode;
import com.im.common.error.ImException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class CmdHandlerRegistry {

  private final Map<Integer, CmdHandler> handlers;

  public CmdHandlerRegistry(List<CmdHandler> handlers) {
    this.handlers = handlers.stream()
        .collect(Collectors.toUnmodifiableMap(
            CmdHandler::cmd,
            Function.identity(),
            (left, right) -> {
              throw new ImException(ErrorCode.INTERNAL_ERROR,
                  "duplicate cmd handler: " + left.cmd());
            }));
  }

  public Optional<CmdHandler> find(int cmd) {
    return Optional.ofNullable(handlers.get(cmd));
  }
}
