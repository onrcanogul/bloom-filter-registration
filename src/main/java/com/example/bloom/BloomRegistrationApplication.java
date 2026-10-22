package com.example.bloom;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class BloomRegistrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(BloomRegistrationApplication.class, args);
    }
}
