package com.github.daggerok.app;

import com.github.daggerok.library.entity.DomainEvent;
import com.github.daggerok.library.hz.Hz;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Date;

//tag::content[]
@Slf4j
@Configuration
class PubSubHzCfg {
  @Bean(name = "hzInstance", destroyMethod = "shutdown")
  HazelcastInstance hzInstance() {
    return Hz.instance;
  }

  @Bean(name = "domainEventsTopic")
  ITopic<DomainEvent> domainEventsTopic(@Qualifier("hzInstance") final HazelcastInstance hzInstance) {
    return hzInstance.getTopic("domainEventsTopic");
  }

  @Bean
  ApplicationRunner applicationRunner(@Qualifier("domainEventsTopic") final ITopic<DomainEvent> domainEventsTopic) {
    return args -> domainEventsTopic.addMessageListener(
        message -> log.info("received message: {}",
                            message.getMessageObject(),
                            message.getPublishingMember(),
                            new Date(message.getPublishTime())));
  }
}
//end::content[]

@SpringBootApplication
public class PubSubBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(PubSubBackendApplication.class, args);
  }
}
