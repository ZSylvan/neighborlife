package com.neighborlife;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@MapperScan("com.neighborlife.mapper")
@SpringBootApplication
public class NeighborLifeApplication {

  public static void main(String[] args) {
    SpringApplication.run(NeighborLifeApplication.class, args);
  }
}
