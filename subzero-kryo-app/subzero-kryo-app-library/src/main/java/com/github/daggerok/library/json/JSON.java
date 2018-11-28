package com.github.daggerok.library.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;

import static com.fasterxml.jackson.databind.SerializationFeature.*;

@Slf4j
public class JSON {

  private static final ObjectMapper objectMapper = objectMapper();

  private static ObjectMapper objectMapper() {
    final ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.disable(FAIL_ON_EMPTY_BEANS);
    objectMapper.enable(WRITE_DATE_KEYS_AS_TIMESTAMPS);
    objectMapper.enable(WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
    objectMapper.enable(WRITE_DATES_AS_TIMESTAMPS);
    objectMapper.enable(WRITE_DURATIONS_AS_TIMESTAMPS);
    return objectMapper;
  }

  public static String stringify(Object o) {
    return Try.of(() -> objectMapper.writeValueAsString(o))
              .onFailure(throwable -> log.info("oops: {}", throwable.getLocalizedMessage()))
              .getOrElseGet(throwable -> "{}");
  }

  private JSON() { }
}
