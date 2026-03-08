package com.nexuschat.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class NexuschatServerApplication {

	public static void main(String[] args) {
		ApplicationContext ctx = SpringApplication.run(
				NexuschatServerApplication.class, args
		);

		// Debug — list all Redis-related beans
		System.out.println("\n=== REDIS BEANS ===");
		for (String name : ctx.getBeanDefinitionNames()) {
			if (name.toLowerCase().contains("redis")) {
				System.out.println("✅ " + name);
			}
		}
		System.out.println("===================\n");
	}
}
