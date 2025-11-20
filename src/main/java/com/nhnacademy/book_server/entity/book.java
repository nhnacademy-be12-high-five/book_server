package com.nhnacademy.book_server.entity;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Slf4j
@Entity
@Table(name = "book")
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "도서 정보 모델입니다.")

// 도서 전체 조회 dto
public class book{

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    // SEQ_NO : 일련번호
    @NotNull
    private Long bookId;
    @Schema(
            description = "일련번호",
            example = "6352228"
    )

    @NotNull
    private String isbn;
    // int로 하면 범위를 초과하므로 String으로 변경
    // ISBN_THIRTEEN_NO : ISBN 번호
    @Schema(
            description = "ISBN",
            example = "9791156759270"
    )

    // VLM_NM : 권(卷) 이름/번호
    // -> 시리즈물일경우 (예: 1권, Vol. 2)
    private String volumeNumber;
    private String title;
    // TITLE_NM : 제목
    @Schema(
            description = "제목",
            example = "너에게 목소리를 보낼게 - <달빛천사> 성우 이용신의 첫 번째 에세이"
    )

    private String author;
    // AUTHR_NM : 저자이름
    @Schema(
            description = "작가",
            example = "이용신 (지은이)"
    )

    // 출판사
    private String publisher;
    @Schema(description = "출판사",example = "푸른숲")

    // 초판 발행일
    // PBLICTE_DE : 초판 발행일
    private LocalDate dateTime;
    @Schema(description = "초판 발행일",example = "2021-12-12")  // 비어있을 수 있음

    // ADTION_SMBL_NM : 판차 기호명
    private String edition;

    // PRC_VALUE : 가격
    private int price;
    @Schema(description = "가격",example = "200000")

    // 이미지 URL : IMAGE_URL
    private String image;
    @Schema(
            description = "이미지 URL",
            example = "https://image.aladin.co.kr/product/28415/8/cover/k652835115_1.jpg"
    )

    // BOOK_INTRCN_CN : 도서 소개 내용
    private String content;
    @Schema(
            description = "도서 소개 내용",
            example = "2004년 방영한 애니메이션 <달빛천사>에서 주인공 루나(풀문) 역을 맡으며 90년대생들에게 보석 같은 추억을 선물한 성우 이용신의 첫 번째 에세이. " +
                    "수많은 작품의 주연을 맡으며 쉬지 않고 대중에게 행복을 전해온 성우 이용신의 발자취를 확인할 수 있다."
    )

//    private String summary;
//    @NotNull
//    @Schema(
//            description = "작가",
//            example = "이용신 (지은이)"
//    )

    // KDC_NM : KDC 분류명
    private String kdcCode;

    // TITLE_SBST_NM : 부제
    private String subjectName;

    // AUTHR_SBST_NM : 역자/역할 이름
    // 저자 외의 인물(예: 역자, 감수자) 및 그 역할에 대한 정보입니다.
    private String contributorName;

    // TWO_PBLICTE_DE : 최종 발행일
    private ZonedDateTime publishedDate;

    // 서점 재고 여부
    private Boolean stockCheckedAt;

    // ISBN_NO : 표준 도서 번호 10자리
    private String isbnNO;

    //포장 여부
    private boolean isWrappedOr;
    @Schema(description = "포장여부",example = "포장")

    private String tag;
    private String bookLike;
    private int count;

}

