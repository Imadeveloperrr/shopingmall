package com.example.crud;

import com.example.crud.ai.config.ChatGptProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan(basePackages = "com.example.crud.common.mapper")
@EnableConfigurationProperties(ChatGptProperties.class)
@EnableScheduling      // OutboxDispatcher
@EnableKafka           // Kafka Listener
public class CrudApplication {

    public static void main(String[] args) {
        SpringApplication.run(CrudApplication.class, args);
    }
}
