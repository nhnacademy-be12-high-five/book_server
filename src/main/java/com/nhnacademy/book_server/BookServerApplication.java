package com.nhnacademy.book_server;

import com.nhnacademy.book_server.config.GeminiConfig;
import com.nhnacademy.book_server.config.RagConfig;
import com.nhnacademy.book_server.config.RagSearchConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableConfigurationProperties({
        RagConfig.class,
        RagSearchConfig.class,
        GeminiConfig.class
})
@EnableFeignClients
@SpringBootApplication
@EnableScheduling
@EnableCaching
@EnableAsync
public class BookServerApplication {

	public static void main(String[] args) {

		SpringApplication.run(BookServerApplication.class, args);
	}
}



