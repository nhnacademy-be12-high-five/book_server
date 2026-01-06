package com.nhnacademy.book_server.parser;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DataParserTest {

    @Test
    @DisplayName("DataParser Interface: Mock 구현을 통한 인터페이스 계약 검증")
    void interfaceContractTest() {
        // Given
        // DataParser 인터페이스를 Mocking하여 가짜 구현체 생성
        DataParser parser = mock(DataParser.class);
        File mockFile = mock(File.class);
        ParsingDto mockDto = new ParsingDto();
        mockDto.setTitle("Mock Book");

        // 동작 정의 (Stubbing)
        given(parser.getFileType()).willReturn(".json");
        given(parser.parsing(mockFile)).willReturn(List.of(mockDto));

        // When
        String fileType = parser.getFileType();
        List<ParsingDto> result = parser.parsing(mockFile);

        // Then
        // 1. getFileType()이 정의대로 동작하는지 확인
        assertThat(fileType).isEqualTo(".json");

        // 2. parsing() 메서드가 리스트를 반환하는지 확인
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Mock Book");

        // 3. 실제로 메서드가 호출되었는지 검증
        verify(parser).getFileType();
        verify(parser).parsing(mockFile);
    }
}