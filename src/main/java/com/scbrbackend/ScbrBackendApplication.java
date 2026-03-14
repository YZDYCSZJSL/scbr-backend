package com.scbrbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ScbrBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScbrBackendApplication.class, args);
    }

}
