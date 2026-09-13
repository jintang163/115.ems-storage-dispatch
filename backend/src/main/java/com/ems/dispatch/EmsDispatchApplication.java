package com.ems.dispatch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class EmsDispatchApplication {
    public static void main(String[] args) {
        SpringApplication.run(EmsDispatchApplication.class, args);
    }
}
