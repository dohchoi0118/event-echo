package com.genderreveal.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class GenderRevealApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(GenderRevealApiApplication.class, args);
    }
}
