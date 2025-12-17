package com.nhnacademy.book_server.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import software.amazon.awssdk.services.s3.S3Client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    @DisplayName("MinioConfig 빈 등록 테스트")
    void minioConfigTest() {
        contextRunner.withUserConfiguration(MinioConfig.class)
                .withPropertyValues(
                        "minio.url=http://localhost:9000",
                        "minio.access-key=test",
                        "minio.secret-key=test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(S3Client.class);
                });
    }

    @Test
    @DisplayName("CacheConfig 빈 등록 테스트")
    void cacheConfigTest() {
        contextRunner.withUserConfiguration(CacheConfig.class)
                .withBean(RedisConnectionFactory.class, () -> mock(RedisConnectionFactory.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(RedisCacheManager.class);
                    RedisCacheManager manager = context.getBean(RedisCacheManager.class);
                    // 커스텀 설정 확인
                    assertThat(manager.getCacheConfigurations()).containsKey("bookDetail");
                    assertThat(manager.getCacheConfigurations()).containsKey("newBooks");
                });
    }
}