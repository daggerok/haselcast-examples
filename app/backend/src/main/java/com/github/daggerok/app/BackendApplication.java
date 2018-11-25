package com.github.daggerok.app;

import com.github.daggerok.library.HzCfgFactory;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class HzCfg {

  @Bean
  Config hzConfig() {
    return HzCfgFactory.hzCfg();
  }

  @Bean(destroyMethod = "shutdown")
  HazelcastInstance hzInstance(Config hzConfig) {
    return Hazelcast.newHazelcastInstance(hzConfig);
  }
}

@SpringBootApplication
public class BackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(BackendApplication.class, args);
  }
}
