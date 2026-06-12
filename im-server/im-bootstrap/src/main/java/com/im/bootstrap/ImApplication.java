package com.im.bootstrap;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.im")
public class ImApplication {

  public static void main(String[] args) {
    SpringApplication.run(ImApplication.class, args);
  }
}
