package com.nhnacademy.book_server.dto;

import com.nhnacademy.book_server.dto.response.AladinSearchResponse;
import com.nhnacademy.book_server.entity.AladinItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AladinSearchResponseTest {

    @Test
    @DisplayName("AladinSearchResponse: Getter/Setter 및 객체 생성 테스트")
    void aladinSearchResponse_GetterSetter() {
        // Given
        AladinSearchResponse response = new AladinSearchResponse();
        
        // 테스트용 아이템 리스트 생성
        List<AladinItem> items = new ArrayList<>();
        AladinItem item = new AladinItem(); // AladinItem 엔티티/DTO 사용
        // item에 필요한 설정이 있다면 여기서 추가
        items.add(item);

        // When: Setter를 통해 값 설정
        response.setTile("Result Title"); // 필드명 tile (오타일 수 있으나 코드 그대로 테스트)
        response.setTotalResults(100);
        response.setStartIndex(1);
        response.setItemsPerPage(10);
        response.setSearchCategoryId(500);
        response.setSearchCategoryName("IT/Science");
        response.setTitle("Search API Response");
        response.setLink("http://api.aladin.co.kr/...");
        response.setAuthor("Aladin API");
        response.setPubdate("2024-01-01");
        response.setDescription("Search Result Description");
        response.setIsbn("1234567890");
        response.setIsbn13("9781234567890");
        response.setPricesales(15000);
        response.setPricestandard(20000);
        response.setItem(items);

        // Then: Getter를 통해 값 검증
        assertThat(response.getTile()).isEqualTo("Result Title");
        assertThat(response.getTotalResults()).isEqualTo(100);
        assertThat(response.getStartIndex()).isEqualTo(1);
        assertThat(response.getItemsPerPage()).isEqualTo(10);
        assertThat(response.getSearchCategoryId()).isEqualTo(500);
        assertThat(response.getSearchCategoryName()).isEqualTo("IT/Science");
        assertThat(response.getTitle()).isEqualTo("Search API Response");
        assertThat(response.getLink()).isEqualTo("http://api.aladin.co.kr/...");
        assertThat(response.getAuthor()).isEqualTo("Aladin API");
        assertThat(response.getPubdate()).isEqualTo("2024-01-01");
        assertThat(response.getDescription()).isEqualTo("Search Result Description");
        assertThat(response.getIsbn()).isEqualTo("1234567890");
        assertThat(response.getIsbn13()).isEqualTo("9781234567890");
        assertThat(response.getPricesales()).isEqualTo(15000);
        assertThat(response.getPricestandard()).isEqualTo(20000);
        
        // 리스트 검증
        assertThat(response.getItem()).hasSize(1);
        assertThat(response.getItem().get(0)).isEqualTo(item);
    }
}