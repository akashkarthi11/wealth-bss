package com.wealth.bss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = {"com.wealth.bss", "com.wealth.common"})
@EnableFeignClients(basePackages = "com.wealth.bss.common.client")
@EnableScheduling
public class WealthBssApplication {
    public static void main(String[] args) {
        SpringApplication.run(WealthBssApplication.class, args);
    }
}
