package com.github.daggerok.library.hz;

import com.hazelcast.client.HazelcastClient;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

public class Hz {

  //tag::content[]
  public static final HazelcastInstance instance = createInstance();
  public static final HazelcastInstance client = createClient();
  //end::content[]

  private static HazelcastInstance createInstance() {
    return Hazelcast.newHazelcastInstance();
  }

  private static HazelcastInstance createClient() {
    return HazelcastClient.newHazelcastClient();
  }

  private Hz() { }
}
