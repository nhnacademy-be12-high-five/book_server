package com.nhnacademy.book_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
public class BookServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookServerApplication.class, args);
	}

}
