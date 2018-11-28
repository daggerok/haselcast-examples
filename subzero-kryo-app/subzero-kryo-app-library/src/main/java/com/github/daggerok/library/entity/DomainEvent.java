package com.github.daggerok.library.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Builder;
import lombok.Value;

import java.io.Serializable;
import java.time.LocalDateTime;

//tag::content[]
@Value
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DomainEvent /* implements Serializable */ {
  public enum Type {
    NONE
  }

  // we don't need any serialization, because SubZero Kryo was added globally
  // // private static final long serialVersionUID = -3158689686565990234L;
  @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:s")
  @JsonDeserialize(using = LocalDateTimeDeserializer.class)
  @JsonSerialize(using = LocalDateTimeSerializer.class)
  final LocalDateTime createdAt;
  final Type type;
  final String jsonData;
}
//end::content[]
