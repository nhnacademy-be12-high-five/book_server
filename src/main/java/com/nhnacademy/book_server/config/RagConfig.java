package com.nhnacademy.book_server.config;


import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "rag")
@Getter
@Setter
public class RagConfig {
    private String indexName; //book_embedding_index
    private int dim; //1024
    private String similarity; //cosine
}
