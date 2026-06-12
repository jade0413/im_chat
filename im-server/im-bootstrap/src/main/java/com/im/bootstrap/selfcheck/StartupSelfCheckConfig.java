package com.im.bootstrap.selfcheck;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(StartupSelfCheckProperties.class)
public class StartupSelfCheckConfig {
}
