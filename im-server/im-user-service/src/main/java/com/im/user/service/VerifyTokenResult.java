package com.im.user.service;

public record VerifyTokenResult(long userId, int heartbeatIntervalSec) {
}
