package com.nhnacademy.book_server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();
    private final WebApplicationContextRunner webContextRunner = new WebApplicationContextRunner();

    @Test
    @DisplayName("MinioConfig: S3Client 빈 등록 및 프로퍼티 주입 테스트")
    void minioConfigTest() {
        contextRunner.withUserConfiguration(MinioConfig.class)
                .withPropertyValues(
                        "minio.url=http://localhost:9000",
                        "minio.access-key=test-access-key",
                        "minio.secret-key=test-secret-key"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }

    @Test
    @DisplayName("CacheConfig: RedisCacheManager 빈 등록 및 설정 확인")
    void cacheConfigTest() {
        contextRunner.withUserConfiguration(CacheConfig.class)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCacheManager.class);
                    RedisCacheManager manager = context.getBean(RedisCacheManager.class);

                    assertThat(manager.getCacheConfigurations())
                            .containsKeys("bookDetail", "newBooks", "bookReviews");
                });
    }

    @Test
    @DisplayName("RabbitMqConfig: 큐, 익스체인지, 바인딩, 템플릿 빈 등록 테스트")
    void rabbitMqConfigTest() {
        contextRunner.withUserConfiguration(RabbitMqConfig.class)
                .withBean(ConnectionFactory.class, () -> mock(ConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(Queue.class);
                    assertThat(context).hasBean("pointQueue");

                    assertThat(context).hasSingleBean(DirectExchange.class);
                    assertThat(context).hasBean("pointExchange");

                    assertThat(context).hasSingleBean(Binding.class);

                    assertThat(context).hasSingleBean(MessageConverter.class);
                    assertThat(context).getBean(MessageConverter.class)
                            .isInstanceOf(Jackson2JsonMessageConverter.class);

                    assertThat(context).hasSingleBean(RabbitTemplate.class);
                });
    }

    @Test
    @DisplayName("ElasticClientConfig: RestClient 및 ElasticsearchClient 빈 등록 테스트")
    void elasticClientConfigTest() {
        contextRunner.withUserConfiguration(ElasticClientConfig.class)
                .withPropertyValues(
                        "elasticsearch.host=localhost",
                        "elasticsearch.port=9200",
                        "elasticsearch.username=testuser",
                        "elasticsearch.password=testpass"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(RestClient.class);
                    assertThat(context).hasSingleBean(ElasticsearchClient.class);
                });
    }

    @Test
    @DisplayName("RestTemplateConfig: RestTemplate 빈 등록 테스트")
    void restTemplateConfigTest() {
        contextRunner.withUserConfiguration(RestTemplateConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(RestTemplate.class);
                });
    }

    @Test
    @DisplayName("GeminiConfig: 프로퍼티 바인딩 및 빈 등록 테스트")
    void geminiConfigTest() {
        contextRunner
                // ConfigurationProperties 기능 활성화를 위해 자동 설정 추가
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(GeminiConfig.class)
                .withPropertyValues(
                        "gemini.api-key=my-secret-api-key",
                        "gemini.embedding-model=text-embedding-004"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(GeminiConfig.class);
                    GeminiConfig config = context.getBean(GeminiConfig.class);

                    // 바인딩 확인
                    assertThat(config.getApiKey()).isEqualTo("my-secret-api-key");
                    assertThat(config.getEmbeddingModel()).isEqualTo("text-embedding-004");
                });
    }


    @Test
    @DisplayName("SecurityConfig: SecurityFilterChain 빈 등록 테스트")
    void securityConfigTest() {
        webContextRunner
                .withConfiguration(AutoConfigurations.of(SecurityAutoConfiguration.class))
                .withUserConfiguration(SecurityConfig.class)
                // SecurityConfig의 requestMatchers가 MvcRequestMatcher를 사용할 때 필요한 빈 등록
                .withBean("mvcHandlerMappingIntrospector", HandlerMappingIntrospector.class, () -> mock(HandlerMappingIntrospector.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(SecurityFilterChain.class);
                    SecurityFilterChain chain = context.getBean(SecurityFilterChain.class);
                    assertThat(chain).isNotNull();
                });
    }
}