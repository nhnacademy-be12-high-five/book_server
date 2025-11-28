//package com.nhnacademy.book_server.config;
//
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import org.apache.http.auth.AuthScope;
//import org.apache.http.auth.UsernamePasswordCredentials;
//import org.apache.http.client.CredentialsProvider;
//import org.apache.http.impl.client.BasicCredentialsProvider;
//import org.elasticsearch.client.RestClient;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//@Configuration
//public class ElasticClientConfig {
//
//    //elasticsearch 연결 객체 만드는 코드
//
//    @Value("${elasticsearch.host}")
//    private String host;
//
//    @Value("${elasticsearch.port}")
//    private int port;
//
//    @Value("${elasticsearch.username}")
//    private String username;
//
//    @Value("${elasticsearch.password}")
//    private String password;
//
//    @Bean
//    public ElasticsearchClient elasticsearchClient(){
//        //elasticsearch 서버에 접속할때 항상 이 id/pw로 인증
//        final CredentialsProvider credentialsProvider = new BasicCredentialsProvider(); //인증 정보 넣을 바구니
//        credentialsProvider.setCredentials(AuthScope.ANY,new UsernamePasswordCredentials(username,password));
//        //AuthScope.ANY -> 어떤 url/포드든 상관없이 이 인증방법 사용
//        //new -> 아이디/비밀번호 객체 생성
//
//        RestClient restClient = new RestClient.builder(
//                new Http
//        )
//
//
//
//    }
//}
