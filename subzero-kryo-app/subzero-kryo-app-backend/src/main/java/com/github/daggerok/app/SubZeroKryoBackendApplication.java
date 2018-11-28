package com.github.daggerok.app;

import com.github.daggerok.library.entity.DomainEvent;
import com.github.daggerok.library.hz.HzCfgFactory;
import com.github.daggerok.library.json.JSON;
import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import io.vavr.control.Try;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.UUID;

import static com.github.daggerok.library.entity.DomainEvent.Type.NONE;
import static java.time.LocalDateTime.now;
import static org.springframework.web.reactive.function.server.RequestPredicates.GET;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Configuration
class HzCfg {

  @Bean
  Config hzConfig() {
    return HzCfgFactory.config;
  }

  @Bean(name = "hzInstance", destroyMethod = "shutdown")
  HazelcastInstance hzInstance(Config hzConfig) {
    return Hazelcast.newHazelcastInstance(hzConfig);
  }

  @Bean(name = "domainEvents")
  IMap<UUID, DomainEvent> domainEvents(@Qualifier("hzInstance") HazelcastInstance hazelcastInstance) {
    return hazelcastInstance.getMap("domainEvents");
  }
}

@Slf4j
@Configuration
@RequiredArgsConstructor
class RoutesConfig {

  @Qualifier("domainEvents")
  final IMap<UUID, DomainEvent> domainEvents;

  @Bean
  RouterFunction router() {
    return
        route(POST("/**"),
              request -> ok().body(request.bodyToMono(Map.class)
                                          .map(map -> Try.of(() -> JSON.stringify(map))
                                                         .onFailure(throwable -> log.error("oops: {}", throwable.getLocalizedMessage()))
                                                         .getOrElseGet(throwable -> "{}"))
                                          .map(jsonData -> DomainEvent.builder()
                                                                      .jsonData(jsonData)
                                                                      .createdAt(now())
                                                                      .type(NONE)
                                                                      .build())
                                          .subscribeOn(Schedulers.elastic())
                                          .map(domainEvent -> Mono.justOrEmpty(domainEvents.put(UUID.randomUUID(), domainEvent)))
                                          .flatMap(whoCares -> whoCares), DomainEvent.class))

            .andRoute(GET("/**"),
                      request -> ok().body(Mono.just(domainEvents), Map.class))

        ;
  }
}

@SpringBootApplication
public class SubZeroKryoBackendApplication {

  public static void main(String[] args) {
    SpringApplication.run(SubZeroKryoBackendApplication.class, args);
  }
}
