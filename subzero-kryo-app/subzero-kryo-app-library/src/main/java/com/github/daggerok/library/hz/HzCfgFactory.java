package com.github.daggerok.library.hz;

import com.hazelcast.config.Config;
import info.jerrinot.subzero.SubZero;

public class HzCfgFactory {

  //tag::content[]
  public static final Config config = createConfig();

  private static Config createConfig() {
    final Config config = new Config();
    SubZero.useAsGlobalSerializer(config);
    return config;
  }
  //end::content[]

  private HzCfgFactory() { }
}
