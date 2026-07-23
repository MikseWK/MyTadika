package com.mytadika;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcTransactionManagerAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        R2dbcAutoConfiguration.class,
        R2dbcTransactionManagerAutoConfiguration.class
})
@EnableScheduling
public class MyTadikaApplication {

    public static void main(String[] args) {
        SpringApplication.run(MyTadikaApplication.class, args);
    }
}