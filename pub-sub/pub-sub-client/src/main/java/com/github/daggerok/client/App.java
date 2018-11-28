package com.github.daggerok.client;

import com.github.daggerok.library.entity.DomainEvent;
import com.github.daggerok.library.hz.Hz;
import com.github.daggerok.library.json.JSON;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.ITopic;

import java.time.LocalDateTime;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static java.util.Collections.singletonMap;

public class App {
  public static void main(String[] args) {
    //tag::content[]
    final String topicName = "domainEventsTopic";
    final HazelcastInstance client = Hz.client;
    final Function<String, ITopic<DomainEvent>> topic = client::getTopic;
    final Function<String, DomainEvent> data = message -> DomainEvent
        .builder()
        .createdAt(LocalDateTime.now())
        .type(DomainEvent.Type.NONE)
        .jsonData(JSON.stringify(singletonMap("message", message)))
        .build();

    if (args.length > 0) {
      final String message = String.join("", args);
      topic.apply(topicName)
           .publish(data.apply(message));
      //end::content[]
      System.exit(0);
      //tag::content[]
    }
    //end::content[]
    final ITopic<DomainEvent> domainEventsTopic = topic.apply(topicName);
    final AtomicReference<String> store = new AtomicReference<>("non null, not empty");
    final Scanner scanner = new Scanner(System.in);
    System.out.println("Enter 'q' to quit.");
    System.out.println("Ready to accept input messages...");
    while (scanner.hasNext()) {
      final String message = scanner.nextLine();
      if ("q".equals(message)) System.exit(0);
      domainEventsTopic.publish(data.apply(message));
      store.set(message);
    }
  }
}
