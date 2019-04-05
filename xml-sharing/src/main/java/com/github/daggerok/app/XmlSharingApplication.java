package com.github.daggerok.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.HazelcastClientManager;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.spring.HazelcastConfigBeanDefinitionParser;
import io.vavr.control.Try;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.annotation.Id;
import org.springframework.data.keyvalue.annotation.KeySpace;
import org.springframework.data.map.repository.config.EnableMapRepositories;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;
import org.springframework.web.reactive.function.server.RouterFunction;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.SerializationFeature.*;
import static java.util.Arrays.asList;
import static lombok.AccessLevel.PACKAGE;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Repository
interface DomainEventRepository extends CrudRepository<DomainEvent, UUID> {}

@Configuration
@EnableMapRepositories
class KVCfg {}

@Data
@KeySpace("domainEvents")
@NoArgsConstructor(access = PACKAGE)
@AllArgsConstructor(staticName = "of")
class DomainEvent implements Serializable {
  private static final long serialVersionUID = -5039904161548307056L;

  @Id
  private UUID id;

  @NonNull
  private String data;
}

@Slf4j
@Configuration
@RequiredArgsConstructor
class RoutesConfig {

  final HazelcastInstance hzInstance;

  @Bean(name = "domainEvents")
  IMap<UUID, DomainEvent> domainEvents() {
    return hzInstance.getMap("domainEvents");
  }

  @Bean
  ObjectMapper objectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(FAIL_ON_EMPTY_BEANS);
    objectMapper.enable(WRITE_DATE_KEYS_AS_TIMESTAMPS);
    objectMapper.enable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    objectMapper.enable(WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.enable(WRITE_DURATIONS_AS_TIMESTAMPS);
    return objectMapper;
  }

  @Bean
  Function<Object, String> serializer(ObjectMapper objectMapper) {
    return o -> Try.of(() -> objectMapper.writeValueAsString(o))
                   .onFailure(throwable -> log.info("oops: {}", throwable.getLocalizedMessage()))
                   .getOrElseGet(throwable -> "{}");
  }

  @Bean
  RouterFunction router(DomainEventRepository repository,
                        Function<Object, String> serializer,
                        @Qualifier("domainEvents") IMap<UUID, DomainEvent> domainEvents) {
    return
        route(POST("/**"),
              request -> ok().body(request.bodyToMono(Map.class)
                                          .subscribeOn(Schedulers.elastic())
                                          .map(serializer)
                                          .map(jsonData -> DomainEvent.of(UUID.randomUUID(), jsonData))
                                          .map(repository::save)
                                          .map(domainEvent -> {
                                            domainEvents.put(domainEvent.getId(), domainEvent);
                                            return domainEvent;
                                          }), DomainEvent.class))

            .andRoute(path("/client/**"),
                      request -> ok().body(Flux.fromIterable(hzInstance.getMap("domainEvents")
                                                                       .values()),
                                           Object.class))

            .andRoute(path("/**"),
                      request -> ok().body(Flux.zip(Flux.fromIterable(repository.findAll()),
                                                    Flux.fromIterable(domainEvents.values()))
                                               .subscribeOn(Schedulers.elastic())
                                               .map(pair -> asList(pair.getT1(), pair.getT2())), List.class))
        ;
  }
}

@SpringBootApplication
public class XmlSharingApplication {

  public static void main(String[] args) {
    SpringApplication.run(XmlSharingApplication.class, args);
  }
}
