package com.nhnacademy.book_server.entity;

import com.opencsv.bean.CsvBindByName;
import com.opencsv.bean.CsvDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.BatchSize;

import java.sql.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Entity
@Table(name = "book")
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Schema(description = "도서 정보 모델입니다.")
@Getter
@Setter
public class Book{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    private String isbn;
    // int로 하면 범위를 초과하므로 String으로 변경
    // ISBN_THIRTEEN_NO : ISBN 번호

    // VLM_NM : 권(卷) 이름/번호
    // -> 시리즈물일경우 (예: 1권, Vol. 2)
    private String volumeNumber;

    @NotNull
    private String title;
    // TITLE_NM : 제목

    @NotNull
    private String author;
    // AUTHR_NM : 저자이름

    // 출판사
    private String publisher;

    // 초판 발행일
    // PBLICTE_DE : 초판 발행일
    private Date dateTime;

    // ADTION_SMBL_NM : 판차 기호명
    private String edition;

    // PRC_VALUE : 가격
    @NotNull
    private Integer price;

    // 이미지 URL : IMAGE_URL
    private String image;

    //     BOOK_INTRCN_CN : 도서 소개 내용
    @NotNull
    private String content;

    // KDC_NM : KDC 분류명
    private Integer kdcCode;

    // TITLE_SBST_NM : 부제
    private String subjectName;

    // AUTHR_SBST_NM : 역자/역할 이름
    // 저자 외의 인물(예: 역자, 감수자) 및 그 역할에 대한 정보입니다.
    private String contributorName;

    // TWO_PBLICTE_DE : 최종 발행일
    @NotNull
    private Date PublishedDate;

    // 서점 재고 여부
    private Boolean stockCheckedAt;

    // ISBN_NO : 표준 도서 번호 10자리
//    private String isbnNO;

    //    private String tag;
//    private String bookLike;
    private Boolean PORTAL_SITE_BOOK_EXST_AT;

    public void setAuthors(List<String> authorList) {
        if (authorList == null || authorList.isEmpty()) {
            this.author = null;
        } else {
            // List의 요소들을 ", "로 연결하여 하나의 문자열로 만듭니다.
            this.author = authorList.stream()
                    .collect(Collectors.joining(", "));
        }
    }

    //포장 여부
//    private boolean WrappedOr;
}