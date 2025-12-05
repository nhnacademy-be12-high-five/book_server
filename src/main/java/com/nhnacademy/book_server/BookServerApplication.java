package com.nhnacademy.book_server;

import com.nhnacademy.book_server.config.GeminiConfig;
import com.nhnacademy.book_server.config.RagConfig;
import com.nhnacademy.book_server.config.RagSearchConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;


@EnableConfigurationProperties({
        RagConfig.class,
        RagSearchConfig.class,
        GeminiConfig.class
})
@EnableFeignClients
@SpringBootApplication
public class BookServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookServerApplication.class, args);
	}
}

