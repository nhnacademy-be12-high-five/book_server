package com.nhnacademy.book_server.controller.book;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.book_server.controller.MemberBookUserTagController;
import com.nhnacademy.book_server.service.MemberBookUserTagService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MemberBookUserTagController.class)
@WithMockUser(username = "user", roles = "USER") // 기본 인증 유저 설정
class MemberBookUserTagControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private MemberBookUserTagService service;

    private static final String HEADER_MEMBER_ID = "X-MEMBER-ID";

    @Test
    @DisplayName("GET: 회원 태그 목록 조회 성공")
    void getUserTags() throws Exception {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        List<String> mockTags = List.of("TO_READ", "FAVORITE");

        given(service.getUserTags(memberId, bookId)).willReturn(mockTags);

        // when & then
        mockMvc.perform(get("/api/books/{bookId}/user-tags", bookId)
                        .header(HEADER_MEMBER_ID, memberId)
                        .accept(MediaType.APPLICATION_JSON))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0]").value("TO_READ"))
                .andExpect(jsonPath("$[1]").value("FAVORITE"));

        then(service).should(times(1)).getUserTags(memberId, bookId);
    }

    @Test
    @DisplayName("POST: 태그 추가 성공")
    void addUserTag() throws Exception {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        String tagCode = "READING";

        // request.setTagCode(tagCode); // DTO 구조에 맞게 설정 필요 (여기서는 JSON 변환만 되면 됨)
        // 테스트 편의상 Map이나 직접 JSON String을 써도 되지만, 여기선 ObjectMapper 활용
        String requestBody = "{\"tagCode\": \"" + tagCode + "\"}";

        // when & then
        mockMvc.perform(post("/api/books/{bookId}/user-tags", bookId)
                        .with(csrf())
                        .header(HEADER_MEMBER_ID, memberId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andDo(print())
                .andExpect(status().isNoContent()); // 204 No Content

        then(service).should(times(1)).addUserTag(eq(memberId), eq(bookId), eq(tagCode));
    }

    @Test
    @DisplayName("DELETE: 태그 삭제 성공")
    void removeUserTag() throws Exception {
        // given
        Long memberId = 1L;
        Long bookId = 100L;
        String tagCode = "TO_READ";

        // when & then
        mockMvc.perform(delete("/api/books/{bookId}/user-tags/{tagCode}", bookId, tagCode)
                        .with(csrf())
                        .header(HEADER_MEMBER_ID, memberId))
                .andDo(print())
                .andExpect(status().isNoContent()); // 204 No Content

        then(service).should(times(1)).removeUserTag(eq(memberId), eq(bookId), eq(tagCode));
    }
}