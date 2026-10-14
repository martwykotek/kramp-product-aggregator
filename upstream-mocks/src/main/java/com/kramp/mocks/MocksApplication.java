package com.kramp.mocks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MocksApplication {

  public static void main(String[] args) {
    SpringApplication.run(MocksApplication.class, args);
  }
}
