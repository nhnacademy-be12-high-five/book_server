package com.nhnacademy.book_server.service.search;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.RecordComponent;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingClientServiceTest {

    private static final String URL = "http://ollama.java21.net/api/embeddings";

    @Mock
    RestTemplate restTemplate;

    OllamaEmbeddingClientService service;

    @BeforeEach
    void setUp() {
        service = new OllamaEmbeddingClientService(restTemplate);
        // 혹시 이전 테스트가 interrupt 상태를 남겼다면 정리
        Thread.interrupted();
    }

    @AfterEach
    void tearDown() {
        // interrupt 상태 정리
        Thread.interrupted();
    }

    @Test
    @DisplayName("text가 null이면 empty 반환 + RestTemplate 호출 없음")
    void embed_nullText_returnsEmpty_noHttpCall() {
        List<Float> result = service.embed(null);

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("text가 blank면 empty 반환 + RestTemplate 호출 없음")
    void embed_blankText_returnsEmpty_noHttpCall() {
        List<Float> result = service.embed("   ");

        assertThat(result).isEmpty();
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("1회차 성공: embedding 반환 + Content-Type JSON + model/prompt 요청 바디 검증")
    void embed_success_firstTry_returnsEmbedding_andValidRequest() throws Exception {
        List<Float> embedding = List.of(0.1f, 0.2f, 0.3f);
        Object response = newOllamaResponse(embedding);

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenReturn(response);

        List<Float> result = service.embed("hello");

        assertThat(result).containsExactlyElementsOf(embedding);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<Object>> entityCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(HttpEntity.class);

        verify(restTemplate, times(1))
                .postForObject(eq(URL), entityCaptor.capture(), any(Class.class));

        HttpEntity<Object> sent = entityCaptor.getValue();
        assertThat(sent.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

        Object requestBody = sent.getBody();
        assertThat(requestBody).isNotNull();

        // private record OllamaRequest(model,prompt) 바디 검증 (reflection)
        assertThat(getRecordComponentValue(requestBody, "model")).isEqualTo("bge-m3");
        assertThat(getRecordComponentValue(requestBody, "prompt")).isEqualTo("hello");
    }

    @Test
    @DisplayName("1회차 response가 null이면 재시도 후 성공 시 embedding 반환 (2회 호출)")
    void embed_responseNull_thenSuccess_returnsEmbedding() throws Exception {
        List<Float> embedding = List.of(1.0f);
        Object ok = newOllamaResponse(embedding);

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenReturn(null, ok);

        List<Float> result = service.embed("q");

        assertThat(result).containsExactlyElementsOf(embedding);
        verify(restTemplate, times(2)).postForObject(eq(URL), any(HttpEntity.class), any(Class.class));
    }

    @Test
    @DisplayName("1회차 embedding이 null이면 재시도 후 성공 시 embedding 반환 (2회 호출)")
    void embed_embeddingNull_thenSuccess_returnsEmbedding() throws Exception {
        Object bad = newOllamaResponse(null);
        Object ok = newOllamaResponse(List.of(0.9f, 0.8f));

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenReturn(bad, ok);

        List<Float> result = service.embed("q");

        assertThat(result).containsExactly(0.9f, 0.8f);
        verify(restTemplate, times(2)).postForObject(eq(URL), any(HttpEntity.class), any(Class.class));
    }

    @Test
    @DisplayName("예외 1회 발생 후 재시도 성공: sleep 지연 없이(인터럽트) embedding 반환 (2회 호출)")
    void embed_exceptionOnce_thenSuccess_returnsEmbedding_withoutDelay() throws Exception {
        // retry 경로에서 Thread.sleep(1000)이 있는데, interrupt 걸면 즉시 InterruptedException으로 빠져 지연 없음
        Thread.currentThread().interrupt();

        Object ok = newOllamaResponse(List.of(0.5f));

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new RuntimeException("timeout"))
                .thenReturn(ok);

        List<Float> result = service.embed("text");

        assertThat(result).containsExactly(0.5f);
        verify(restTemplate, times(2)).postForObject(eq(URL), any(HttpEntity.class), any(Class.class));
    }

    @Test
    @DisplayName("예외가 3회 모두 발생하면 최종 empty 반환 (3회 호출)")
    void embed_exceptionsAllRetries_returnsEmpty() {
        Thread.currentThread().interrupt(); // sleep 지연 제거

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenThrow(new RuntimeException("e1"))
                .thenThrow(new RuntimeException("e2"))
                .thenThrow(new RuntimeException("e3"));

        List<Float> result = service.embed("text");

        assertThat(result).isEmpty();
        verify(restTemplate, times(3)).postForObject(eq(URL), any(HttpEntity.class), any(Class.class));
    }

    @Test
    @DisplayName("응답은 오지만 embedding이 계속 null이면 3회 후 empty 반환 (3회 호출)")
    void embed_embeddingNullAllRetries_returnsEmpty() throws Exception {
        Object bad1 = newOllamaResponse(null);
        Object bad2 = newOllamaResponse(null);
        Object bad3 = newOllamaResponse(null);

        when(restTemplate.postForObject(eq(URL), any(HttpEntity.class), any(Class.class)))
                .thenReturn(bad1, bad2, bad3);

        List<Float> result = service.embed("text");

        assertThat(result).isEmpty();
        verify(restTemplate, times(3)).postForObject(eq(URL), any(HttpEntity.class), any(Class.class));
    }

    // -------------------------
    // Reflection helpers (private record 접근)
    // -------------------------

    /**
     * private record OllamaResponse(@JsonProperty("embedding") List<Float> embedding) 생성
     */
    private Object newOllamaResponse(List<Float> embedding) throws Exception {
        Class<?> responseClass = Class.forName(
                "com.nhnacademy.book_server.service.search.OllamaEmbeddingClientService$OllamaResponse"
        );
        Constructor<?> ctor = responseClass.getDeclaredConstructor(List.class);
        ctor.setAccessible(true);
        return ctor.newInstance(embedding);
    }

    /**
     * record 컴포넌트 값 읽기 (model/prompt 등)
     */
    private Object getRecordComponentValue(Object recordObj, String componentName) {
        if (!recordObj.getClass().isRecord()) {
            throw new IllegalArgumentException("Not a record: " + recordObj.getClass());
        }
        for (RecordComponent rc : recordObj.getClass().getRecordComponents()) {
            if (rc.getName().equals(componentName)) {
                try {
                    return rc.getAccessor().invoke(recordObj);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
        throw new IllegalArgumentException("No such component: " + componentName);
    }
}
