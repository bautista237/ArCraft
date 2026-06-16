package org.austral.ing.arcraft;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ArcraftApplication {
    public static void main(String[] args) {
        SpringApplication.run(ArcraftApplication.class, args);
    }
}
