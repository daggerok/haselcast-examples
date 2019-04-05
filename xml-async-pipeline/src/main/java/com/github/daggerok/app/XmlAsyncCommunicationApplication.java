package com.github.daggerok.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.ITopic;
import io.vavr.control.Try;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
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
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.SerializationFeature.*;
import static com.github.daggerok.app.KVCfg.DOMAIN_EVENTS;
import static lombok.AccessLevel.PACKAGE;
import static org.springframework.web.reactive.function.server.RequestPredicates.POST;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;
import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Repository
interface DomainEventRepository extends CrudRepository<DomainEvent, UUID> {}

@Configuration
@EnableMapRepositories
class KVCfg {

  static final String DOMAIN_EVENTS = "domainEvents";
}

@Data
@KeySpace(DOMAIN_EVENTS)
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
  final DomainEventRepository repository;

  @Bean
  ApplicationRunner topicToMapProcessor() {
    return args -> {
      ITopic<DomainEvent> topic = hzInstance.getTopic(DOMAIN_EVENTS);
      topic.addMessageListener(message -> {
        DomainEvent domainEvent = message.getMessageObject();
        log.info("topic-to-map processor received: {} {}", message, domainEvent);
        final IMap<UUID, DomainEvent> domainEvents = hzInstance.getMap(DOMAIN_EVENTS);
        domainEvents.put(domainEvent.getId(), domainEvent);
      });
    };
  }

  @Bean
  ApplicationRunner mapToRepositoryProcessor() {
    return args -> {
      ITopic<DomainEvent> topic = hzInstance.getTopic(DOMAIN_EVENTS);
      topic.addMessageListener(message -> {
        DomainEvent domainEvent = message.getMessageObject();
        log.info("map-to-repository processor received: {} {}", message, domainEvent);
        repository.save(domainEvent);
      });
    };
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
  Function<Object, String> serializer() {
    return o -> Try.of(() -> objectMapper().writeValueAsString(o))
                   .onFailure(throwable -> log.info("oops: {}", throwable.getLocalizedMessage()))
                   .getOrElseGet(throwable -> "{}");
  }

  @Bean
  RouterFunction router() {
    return
        route(POST("/**"),
              request -> ok().body(request.bodyToMono(Map.class)
                                          .subscribeOn(Schedulers.elastic())
                                          .doOnEach(mapSignal -> log.info("webFlux received: {}", mapSignal.get()))
                                          .map(serializer())
                                          .map(jsonData -> DomainEvent.of(UUID.randomUUID(), jsonData))
                                          .map(domainEvent -> {
                                            final ITopic<DomainEvent> topic = hzInstance.getTopic(DOMAIN_EVENTS);
                                            topic.publish(domainEvent);
                                            return domainEvent;
                                          }), DomainEvent.class))

            .andRoute(path("/**"),
                      request -> ok().body(Flux.fromIterable(repository.findAll())
                                               .subscribeOn(Schedulers.elastic()), DomainEvent.class))
        ;
  }
}

@SpringBootApplication
public class XmlAsyncCommunicationApplication {

  public static void main(String[] args) {
    SpringApplication.run(XmlAsyncCommunicationApplication.class, args);
  }
}
