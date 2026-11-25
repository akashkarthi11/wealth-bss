package com.wealth.bss;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(exclude = DataSourceAutoConfiguration.class,
        scanBasePackages = {"com.wealth.bss", "com.wealth.common"})
@EnableFeignClients(basePackages = "com.wealth.bss.common.client")
public class WealthBssApplication {
    public static void main(String[] args) {
        SpringApplication.run(WealthBssApplication.class, args);
    }
}
