package com.miniorch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MiniOrchApplication {

    public static void main(String[] args) {
        SpringApplication.run(MiniOrchApplication.class, args);
    }
}
