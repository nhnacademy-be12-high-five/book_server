package com.nhnacademy.book_server.service.impl;

import com.nhnacademy.book_server.entity.AladinItem;
import com.nhnacademy.book_server.entity.Book;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.dto.response.AladinSearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AladinServiceImpl implements AladinService {

    private final RestTemplate restTemplate;
    private final BookRepository bookRepository; // Repository 필수 사용

    @Value("${aladin.ttb-key}")
    private String ttbKey;

    // 1. 검색용 URL (ItemSearch)
    private static final String SEARCH_URL =
            "http://www.aladin.co.kr/ttb/api/ItemSearch.aspx?ttbkey={ttbKey}&Query={query}&queryType={queryType}&SearchTarget=Book&output=JS&Version=20131101&Cover=Big";

    // 2. 상세 조회용 URL (ItemLookUp - ISBN 사용)
    private static final String LOOKUP_URL =
            "http://www.aladin.co.kr/ttb/api/ItemLookUp.aspx?ttbkey={ttbKey}&ItemId={isbn13}&ItemIdType=ISBN13&output=JS&Version=20131101&Cover=Big&OptResult=packing,ratinginfo,authors,reviewList";

    // 3. 리스트 조회용 URL (ItemList - 베스트셀러/신간)
    private static final String LIST_URL =
            "http://www.aladin.co.kr/ttb/api/ItemList.aspx?ttbkey={ttbKey}&QueryType={queryType}&SearchTarget=Book&output=JS&Version=20131101&Cover=Big";

    @Override
    public List<AladinItem> searchBooks(String query, String queryType) {

        try {
            AladinSearchResponse response = restTemplate.getForObject(
                    SEARCH_URL,
                    AladinSearchResponse.class,
                    ttbKey,
                    query,
                    queryType
            );

            if (response != null && response.getItem() != null) {
                List<AladinItem> items = response.getItem();
                List<Book> booksToSave = new ArrayList<>();

                log.info("알라딘에서 {} 건을 가져왔습니다.", items.size());

                for (AladinItem item : items) {
                    // 1. 데이터 보정 (Null 방지)
                    if (item.getPriceStandard() == null) item.setPriceStandard(0);
                    if (item.getPriceSales() == null) item.setPriceSales(0);

                    // 2. 할인율 계산 로그
                    if (item.getPriceStandard() > 0) {
                        int discountPrice = item.getPriceStandard() - item.getPriceSales();
                        double discountRate = (double) discountPrice / item.getPriceStandard() * 100;
                        log.info("책: {}, 할인율: {}%", item.getTitle(), (int) discountRate);
                    }

                    Book book = convertToBookEntity(item);

                    if(book != null) {
                        booksToSave.add(book);
                    }
                }

                return items; // 저장된 리스트 반환
            }

        } catch (Exception e) {
            throw new RuntimeException("알라딘 API 오류: " + e.getMessage(), e);
        }

        return Collections.emptyList();
    }

    @Override
    public Book convertToBookEntity(AladinItem item) {
        // 데이터 무결성 체크 (ISBN이나 제목이 없으면 저장 안 함)
        if (item.getIsbn13() == null || item.getTitle() == null) {
            return null;
        }

        // Builder를 사용하여 Book 객체 생성
        Book book = Book.builder()
                .isbn13(item.getIsbn13())
                .title(item.getTitle())
                .price(item.getPriceStandard()) // 정가
                .publishedDate(item.getPubDate()) // String -> String 매핑
                .content(item.getDescription() != null ? item.getDescription() : "") // null 방지
                .image(item.getLink()) // 링크나 이미지 URL 매핑
                // .dateTime(LocalDate.parse(item.getPubDate(), DateTimeFormatter.ofPattern("yyyy-MM-dd"))) // 날짜 변환 필요시

                // ⚠️ 주의: 연관 관계 매핑
                // Book 엔티티 설정을 보니 Publisher와 BookAuthor가 필수(@NotNull)일 수 있습니다.
                // 임시로 null을 넣거나, 기본 값을 넣어줘야 에러가 안 납니다.
                .build();

        return book;
    }

    public AladinItem lookupBook(String isbn13) {
        if (isbn13 == null || isbn13.isEmpty()) {
            return null;
        }

        Optional<Book> existBook = bookRepository.findByIsbn13(isbn13);

        if (existBook.isPresent()) {
            log.info("DB에서 책 정보를 찾았습니다: {}", isbn13);
            // DB 엔티티(Book)를 DTO(AladinItem)로 변환해서 반환
            return convertEntityToAladinItem(existBook.get());
        }

        // 2. DB에 없으면 알라딘 API 호출 (ItemLookUp URL 사용 필수!)
        log.info("DB에 책이 없어 알라딘 API를 호출합니다: {}", isbn13);

        try {
            // 주의: 검색용 BASE_URL이 아니라, 상품조회용 LOOKUP_URL을 써야 합니다.
            AladinSearchResponse response = restTemplate.getForObject(
                    LOOKUP_URL, // 위에서 정의한 ItemLookUp URL
                    AladinSearchResponse.class,
                    ttbKey,
                    isbn13
            );

            if (response != null && response.getItem() != null && !response.getItem().isEmpty()) {
                return response.getItem().get(0);
            }
        } catch (Exception e) {
            log.error("알라딘 API 조회 오류: {}", e.getMessage());
        }

        return null;
    }

    public List<AladinItem> getBookList(String queryType) {
        try {
            AladinSearchResponse response = restTemplate.getForObject(
                    LIST_URL, // 리스트 URL 사용
                    AladinSearchResponse.class,
                    ttbKey,
                    queryType // Bestseller, ItemNewAll 등
            );

            if (response != null && response.getItem() != null) {
                log.info("알라딘 리스트 조회({}): {} 건", queryType, response.getItem().size());
                return response.getItem();
            }
        } catch (Exception e) {
            log.error("알라딘 리스트 API 오류: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    public AladinItem convertEntityToAladinItem(Book book) {
        AladinItem item = new AladinItem();
        item.setTitle(book.getTitle());
        item.setLink(book.getImage());  // 이미지 Link
        item.setIsbn13(book.getIsbn13());
        item.setPubDate(book.getPublishedDate());
        item.setPriceStandard(book.getPrice());
        item.setDescription(book.getContent());
        return item;
    }
}