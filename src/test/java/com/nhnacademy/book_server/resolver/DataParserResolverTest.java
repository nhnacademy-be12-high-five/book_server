package com.nhnacademy.book_server.resolver;

import com.nhnacademy.book_server.parser.DataParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class DataParserResolverTest {

    @Mock
    private DataParser csvParser;

    @Mock
    private DataParser jsonParser;

    @Test
    @DisplayName("getDataParser: CSV 파일 확장자에 맞는 파서 반환 성공")
    void getDataParser_Success_Csv() {
        // Given
        given(csvParser.getFileType()).willReturn(".csv");

        // 파서 리스트 주입
        DataParserResolver resolver = new DataParserResolver(List.of(csvParser, jsonParser));

        // When
        DataParser result = resolver.getDataParser("data.csv");

        // Then
        assertThat(result).isEqualTo(csvParser);
    }

    @Test
    @DisplayName("getDataParser: 대소문자 구분 없이 확장자 매칭 성공")
    void getDataParser_Success_IgnoreCase() {
        // Given
        given(csvParser.getFileType()).willReturn(".csv");
        DataParserResolver resolver = new DataParserResolver(List.of(csvParser));

        // When (대문자 확장자 요청)
        DataParser result = resolver.getDataParser("DATA.CSV");

        // Then
        assertThat(result).isEqualTo(csvParser);
    }

    @Test
    @DisplayName("getDataParser: 등록된 파서가 없을 때 null 반환")
    void getDataParser_Fail_NoParsers() {
        // Given
        DataParserResolver resolver = new DataParserResolver(Collections.emptyList());

        // When
        DataParser result = resolver.getDataParser("test.csv");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getDataParser: 파일명이 null일 때 null 반환")
    void getDataParser_Fail_NullFileName() {
        // Given
        DataParserResolver resolver = new DataParserResolver(List.of(csvParser));

        // When
        DataParser result = resolver.getDataParser(null);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getDataParser: 확장자가 없는 파일명일 때 null 반환")
    void getDataParser_Fail_NoExtension() {
        // Given
        DataParserResolver resolver = new DataParserResolver(List.of(csvParser));

        // When
        DataParser result = resolver.getDataParser("README");

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getDataParser: 지원하지 않는 확장자일 때 null 반환")
    void getDataParser_Fail_UnsupportedExtension() {
        // Given
        given(csvParser.getFileType()).willReturn(".csv");
        DataParserResolver resolver = new DataParserResolver(List.of(csvParser));

        // When
        DataParser result = resolver.getDataParser("image.png");

        // Then
        assertThat(result).isNull();
    }
}