package com.learning.urlshortnerbc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class UrlShortnerBcApplication {

    public static void main(String[] args) {
        SpringApplication.run(UrlShortnerBcApplication.class, args);
    }

}
