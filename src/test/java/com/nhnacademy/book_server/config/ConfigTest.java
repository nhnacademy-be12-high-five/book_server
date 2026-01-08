package com.nhnacademy.book_server.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.indices.ElasticsearchIndicesClient;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.util.ObjectBuilder;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
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
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

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
                        "minio.secret-key=test-secret-key",
                        "minio.connection-timeout=10s",
                        "minio.socket-timeout=60s"
                )
                .withInitializer(context -> {
                    ApplicationConversionService conversionService = new ApplicationConversionService();

                    context.getBeanFactory().setConversionService(conversionService);
                    context.getEnvironment().setConversionService(conversionService);
                })
                .run(context -> {
                    assertThat(context).hasSingleBean(software.amazon.awssdk.services.s3.S3Client.class);
                    assertThat(context).hasNotFailed();
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
    @DisplayName("RestTemplateConfig: RestTemplate 빈 2개 등록 테스트")
    void restTemplateConfigTest() {
        contextRunner.withUserConfiguration(RestTemplateConfig.class)
                .run(context -> {
                    // 1. 빈이 1개가 아니라, 해당 타입의 빈이 존재하는지 확인 (개수 체크 아님)
                    // 혹은 getBeans(RestTemplate.class)의 사이즈가 2인지 확인
                    assertThat(context).getBeans(RestTemplate.class).hasSize(2);

                    // 2. 구체적으로 각 빈의 이름으로 존재하는지 확인
                    assertThat(context).hasBean("restTemplate");
                    assertThat(context).hasBean("ollamaRestTemplate");

                    assertThat(context.getBean(RestTemplate.class)).isNotNull();
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

    @Test
    @DisplayName("ElasticSearchConfig: 인덱스 초기화 컴포넌트 빈 등록 테스트")
    void elasticSearchConfigTest() {
        contextRunner.withUserConfiguration(ElasticSearchConfig.class)
                .withBean(ElasticsearchClient.class, () -> mock(ElasticsearchClient.class))
                .run(context -> {
                    // 빈이 정상적으로 등록되었는지 확인
                    assertThat(context).hasSingleBean(ElasticSearchConfig.class);
                });
    }

    @Test
    @DisplayName("ElasticSearchConfig 로직: 인덱스가 이미 존재할 경우 생성 건너뛰기")
    void elasticSearchConfig_IndexExists() throws Exception {
        // 1. Mock 객체 생성
        ElasticsearchClient mockClient = mock(ElasticsearchClient.class);
        ElasticsearchIndicesClient mockIndicesClient = mock(ElasticsearchIndicesClient.class);
        BooleanResponse mockBooleanResponse = mock(BooleanResponse.class);

        // 2. Mock 동작 정의
        when(mockClient.indices()).thenReturn(mockIndicesClient);

        // 방법 B: 정석적인 제네릭 명시 (아래 코드 사용)
        when(mockIndicesClient.exists(ArgumentMatchers.<Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>>any()))
                .thenReturn(mockBooleanResponse);

        // exists.value() -> true (인덱스 존재함)
        when(mockBooleanResponse.value()).thenReturn(true);

        // 3. 테스트 대상 실행
        ElasticSearchConfig config = new ElasticSearchConfig(mockClient);
        config.createBookIndex();

        // 4. 검증
        // verify에서도 동일하게 제네릭 타입을 맞춰줍니다.
        verify(mockIndicesClient).exists(ArgumentMatchers.<Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>>any());

        // create()는 호출되지 않아야 함 (제네릭 타입 주의)
        verify(mockIndicesClient, never()).create(ArgumentMatchers.<Function<co.elastic.clients.elasticsearch.indices.CreateIndexRequest.Builder, ObjectBuilder<co.elastic.clients.elasticsearch.indices.CreateIndexRequest>>>any());
    }

    @Test
    @DisplayName("ElasticSearchConfig 로직: 인덱스가 없을 때 생성 시도")
    void elasticSearchConfig_IndexNotExists() throws Exception {
        // 1. Mock 객체 생성
        ElasticsearchClient mockClient = mock(ElasticsearchClient.class);
        ElasticsearchIndicesClient mockIndicesClient = mock(ElasticsearchIndicesClient.class);
        BooleanResponse mockBooleanResponse = mock(BooleanResponse.class);

        // 2. Mock 동작 정의
        when(mockClient.indices()).thenReturn(mockIndicesClient);

        when(mockIndicesClient.exists(ArgumentMatchers.<Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>>any()))
                .thenReturn(mockBooleanResponse);

        // exists.value() -> false (인덱스 없음)
        when(mockBooleanResponse.value()).thenReturn(false);

        // 3. 실행
        ElasticSearchConfig config = new ElasticSearchConfig(mockClient);

        // *주의: 실제 경로에 파일이 없으면 FileNotFoundException 발생 후 catch로 빠짐
        config.createBookIndex();

        // 4. 검증
        verify(mockIndicesClient).exists(ArgumentMatchers.<Function<ExistsRequest.Builder, ObjectBuilder<ExistsRequest>>>any());
    }

    @Test
    @DisplayName("OpenApiConfig: OpenAPI 빈 등록 및 설정 확인")
    void openApiConfigTest() {
        contextRunner.withUserConfiguration(OpenApiconfig.class)
                .withPropertyValues("springdoc.version=v2.0")
                .run(context -> {
                    // 1. OpenAPI 빈이 정상적으로 등록되었는지 확인
                    assertThat(context).hasSingleBean(io.swagger.v3.oas.models.OpenAPI.class);

                    io.swagger.v3.oas.models.OpenAPI openAPI = context.getBean(io.swagger.v3.oas.models.OpenAPI.class);

                    // 2. 코드에 설정된 제목, 설명이 맞는지 검증
                    assertThat(openAPI.getInfo().getTitle()).isEqualTo("제목");
                    assertThat(openAPI.getInfo().getDescription()).isEqualTo("설명");

                    // 3. 주입한 버전(v2.0)이 잘 반영되었는지 확인
                    assertThat(openAPI.getInfo().getVersion()).isEqualTo("v2.0");
                });
    }
}