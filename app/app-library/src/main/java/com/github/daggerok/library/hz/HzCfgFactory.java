package com.github.daggerok.library.hz;

import com.hazelcast.config.Config;

public class HzCfgFactory {

  public static Config hzCfg() {
    final Config config = new Config();
    return config;
  }

  private HzCfgFactory() { }
}
