package com.github.daggerok.app;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hazelcast.config.Config;
import com.hazelcast.config.MapAttributeConfig;
import com.hazelcast.config.MapIndexConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
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
import org.springframework.session.hazelcast.HazelcastSessionRepository;
import org.springframework.session.hazelcast.PrincipalNameExtractor;
import org.springframework.session.hazelcast.config.annotation.web.http.EnableHazelcastHttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import javax.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static com.fasterxml.jackson.databind.SerializationFeature.*;
import static com.github.daggerok.app.KVCfg.DOMAIN_EVENTS;
import static java.util.Collections.singletonMap;
import static lombok.AccessLevel.PACKAGE;
import static org.springframework.web.servlet.function.RouterFunctions.route;
import static org.springframework.web.servlet.function.ServerResponse.ok;

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
@AllArgsConstructor(staticName = "allOf")
@RequiredArgsConstructor(staticName = "of")
class DomainEvent implements Serializable {
  private static final long serialVersionUID = -5039904161548307056L;

  @Id
  private UUID id;

  @NonNull
  private String data;
}

@Configuration
@EnableHazelcastHttpSession
class HZCfg {
  /*
  @Bean // FIXME: This one is 100% optional.
  public HazelcastInstance hazelcastInstance() {
    MapAttributeConfig attributeConfig = new MapAttributeConfig()
        .setName(HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE)
        .setExtractor(PrincipalNameExtractor.class.getName());

    Config config = new Config();

    config.getMapConfig(HazelcastSessionRepository.DEFAULT_SESSION_MAP_NAME)
          .addMapAttributeConfig(attributeConfig)
          .addMapIndexConfig(new MapIndexConfig(
              HazelcastSessionRepository.PRINCIPAL_NAME_ATTRIBUTE, false));

    return Hazelcast.newHazelcastInstance(config);
  }
  */
}

@Slf4j
@Configuration
@RequiredArgsConstructor
class RoutesConfig {

  final HazelcastInstance hzInstance;
  final DomainEventRepository repository;

  @Bean
  ApplicationRunner runner() {
    return args -> {
      ITopic<String> jsonStream = hzInstance.getTopic(DOMAIN_EVENTS);
      jsonStream.addMessageListener(message -> {
        String json = message.getMessageObject();
        DomainEvent domainEvent = DomainEvent.of(json);
        log.info("saved: {}", repository.save(domainEvent));
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
  Function<Object, String> serializer(ObjectMapper objectMapper) {
    return o -> Try.of(() -> objectMapper.writeValueAsString(o))
                   .onFailure(throwable -> log.info("oops: {}", throwable.getLocalizedMessage()))
                   .getOrElseGet(throwable -> "{}");
  }

  @Bean
  RouterFunction<ServerResponse> router(Function<Object, String> serializer) {
    return route()
        .POST("/api/**", request -> {
          Map map = request.body(Map.class);
          String jsonData = serializer.apply(map);
          ITopic<String> events = hzInstance.getTopic(DOMAIN_EVENTS);
          events.publish(jsonData);
          return ok().body(singletonMap("response", "data sent."));
        })
        .GET("/api/**", request -> ok().body(repository.findAll()))
        .build();
  }
}

@Controller
@RequiredArgsConstructor
class IndexPage {

  final DomainEventRepository repository;

  @GetMapping({ "index", "/", "/*" })
  //@GetMapping({"index/", "/", "/*"})
  public String index(HttpSession httpSession, Model model) {
    model.addAttribute("session", httpSession);
    model.addAttribute("events", repository.findAll());
    return "index";
  }
}

@SpringBootApplication
public class HzWeb1Application {

  public static void main(String[] args) {
    SpringApplication.run(HzWeb1Application.class, args);
  }
}
