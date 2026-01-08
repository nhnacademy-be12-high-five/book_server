package com.nhnacademy.book_server.controller.Tag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.TagController;
import com.nhnacademy.book_server.dto.request.TagRequest;
import com.nhnacademy.book_server.dto.response.TagResponse;
import com.nhnacademy.book_server.service.TagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TagController.class)
@WithMockUser // 인증된 가짜 사용자로 실행 (401 에러 방지)
class TagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TagService tagService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("태그 생성 테스트 (POST /api/tag)")
    void createTag() throws Exception {
        // given
        // TagRequest가 record라고 가정 (서비스 코드에서 .name()으로 호출하므로)
        TagRequest request = new TagRequest("Spring");

        // 반환될 응답 객체
        TagResponse response = new TagResponse(1L, "Spring");

        given(tagService.createTag(any(TagRequest.class))).willReturn(response);

        // when & then
        mockMvc.perform(post("/api/tag")
                        .with(csrf()) // POST 요청 시 필수
                        .content(objectMapper.writeValueAsString(request)) // 객체 -> JSON 변환
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated()) // 201 Created 확인
                .andExpect(jsonPath("$.tagId").value(1L))
                .andExpect(jsonPath("$.name").value("Spring"));

        // verify
        verify(tagService).createTag(any(TagRequest.class));
    }

    @Test
    @DisplayName("태그 단건 조회 테스트 (GET /api/tag/{tagId})")
    void getTag() throws Exception {
        // given
        Long tagId = 1L;
        TagResponse response = new TagResponse(tagId, "Java");

        given(tagService.getTag(tagId)).willReturn(response);

        // when & then
        mockMvc.perform(get("/api/tag/{tagId}", tagId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tagId").value(1L))
                .andExpect(jsonPath("$.name").value("Java"));

        verify(tagService).getTag(tagId);
    }

    @Test
    @DisplayName("태그 전체 조회 테스트 (GET /api/tags)")
    void getTags() throws Exception {
        // given
        List<TagResponse> responses = List.of(
                new TagResponse(1L, "Java"),
                new TagResponse(2L, "Spring"),
                new TagResponse(3L, "JPA")
        );

        given(tagService.getAllTags()).willReturn(responses);

        // when & then
        mockMvc.perform(get("/api/tags")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(3)) // 리스트 크기 확인
                .andExpect(jsonPath("$[0].name").value("Java"))
                .andExpect(jsonPath("$[1].name").value("Spring"));

        verify(tagService).getAllTags();
    }
}