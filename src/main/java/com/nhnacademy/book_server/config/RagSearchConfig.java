package com.nhnacademy.book_server.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "rag-search")
@Getter
@Setter
public class RagSearchConfig {
    private int topK;
    private boolean rereank; //false
    private List<String> fields; //["title", "author", "content"]
}