package com.nhnacademy.book_server.resolver;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTagCodeTest {

    @Test
    @DisplayName("UserTagCode Enum 상수 존재 확인")
    void enumConstantsTest() {
        // 모든 Enum 값을 가져옴
        UserTagCode[] values = UserTagCode.values();

        // Enum 개수 및 특정 값 존재 여부 확인
        assertThat(values).containsExactlyInAnyOrder(
                UserTagCode.CART_CANDIDATE,
                UserTagCode.TO_READ,
                UserTagCode.GIFT_CANDIDATE,
                UserTagCode.REBUY_REVIEW,
                UserTagCode.WANT_RECOMMEND
        );
    }
    
    @Test
    @DisplayName("valueOf 테스트")
    void valueOfTest() {
        UserTagCode code = UserTagCode.valueOf("CART_CANDIDATE");
        assertThat(code).isEqualTo(UserTagCode.CART_CANDIDATE);
    }
}