package com.wealth.bss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class)
public class WealthBssApplication {
    public static void main(String[] args) {
        SpringApplication.run(WealthBssApplication.class, args);
    }
}
