package com.ghost.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableDiscoveryClient // Registra no Eureka
@EnableFeignClients // Permite chamar o ghost-integrations depois
@EnableAsync
public class GhostCoreApplication {

	public static void main(String[] args) {
		SpringApplication.run(GhostCoreApplication.class, args);
	}

}
