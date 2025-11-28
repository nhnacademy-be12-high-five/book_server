package com.nhnacademy.book_server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

//인덱스 컴포넌트 생성
@Component
@RequiredArgsConstructor
public class ElasticSearchConfig {

    private final ElasticsearchClient client;

    @PostConstruct
    public void createBookIndex() throws Exception{
        String index = "book_index";

        boolean exists = client.indices().exists(e -> e.index(index)).value();

        if(exists){
            System.out.println("ES: book_index 이미 존재");
            return;
        }
        //Json 파일 읽기
        String mappingJson = Files.readString(Paths.get("src/main/resources/Elastic/book_index.json"));

        //Json 문자열 -> inputstream 변환
        InputStream jsonStream = new ByteArrayInputStream(mappingJson.getBytes(StandardCharsets.UTF_8));

        //인덱스 생성
        client.indices().create(c-> c.index(index).withJson(jsonStream));

        System.out.println("ES: book_index 생성 완료");
    }
}
