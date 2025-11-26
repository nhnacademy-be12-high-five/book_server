package com.nhnacademy.book_server.service.search;

import com.nhnacademy.book_server.dto.BookSortType;
import com.nhnacademy.book_server.entity.SearchFieldType;
import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.service.read.BookReadService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
@Service
public class BookSearchServiceImpl implements BookSearchService {

    private final BookReadService bookReadService;
    private final SearchLogService searchLogService;

    @Override
    public Page<BookResponse> searchBooks(String keyword, BookSortType sort, int page, int size){

        // 1) 동의어 키워드 확장
        Set<String> expandedKeywords = Dictionary.expand(keyword);

        // 2) 전체 도서 가져오기
        List<BookResponse> allBooks = bookReadService.findAllBooks();

        // 3) score 계산 (확장된 모든 키워드 사용)
        List<ScoredBook> matched = allBooks.stream()
                .map(book -> new ScoredBook(book, calculateScore(book, expandedKeywords)))
                .filter(sb -> expandedKeywords.isEmpty() || sb.score() > 0)
                .toList();

        // 4) 정렬 기준 적용
        Comparator<ScoredBook> comparator = resolveComparator(sort);
        matched.sort(comparator);

        // 5) 페이징 처리
        int start = page * size;
        int end = Math.min(start + size, matched.size());
        List<BookResponse> content = matched.subList(start, end).stream()
                .map(ScoredBook::book)
                .toList();

        return new PageImpl<>(content, PageRequest.of(page, size), matched.size());
    }


    @Override
    public Page<BookResponse> getAllBooks(int page, int size){
        //전체 도서 조회
        List<BookResponse> allBooks = bookReadService.findAllBooks();

        PageRequest pageRequest = PageRequest.of(page, size);
        int from = pageRequest.getPageNumber() * pageRequest.getPageSize();
        int to = Math.min(from + pageRequest.getPageSize(), allBooks.size());

        if (from >= allBooks.size()) {
            return new PageImpl<>(List.of(), pageRequest, allBooks.size());
        }

        List<BookResponse> content = allBooks.subList(from, to);
        return new PageImpl<>(content, pageRequest, allBooks.size());
    }

    @Override
    public BookResponse getBookById(Long id){
        return bookReadService.findBookById(id)
                .orElseThrow(() -> new IllegalArgumentException("해당 ID의 도서를 찾을 수 없습니다: " + id));
    }



    //bookResponse + score을 묶어주는 임시 클래스
    private record ScoredBook(BookResponse book, int score){}

    //점수계산 클래스
    private int calculateScore(BookResponse book, Set<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return 0;
        }

        int score = 0;

        for (String kw : keywords) {
            // 제목
            if (book.title() != null && book.title().toLowerCase().contains(kw)) {
                score += SearchFieldType.TITLE.getWeight();
            }

            // 저자
            if (book.author() != null && book.author().toLowerCase().contains(kw)) {
                score += SearchFieldType.AUTHOR.getWeight();
            }

            // ISBN
            if (book.isbn() != null && book.isbn().toLowerCase().contains(kw)) {
                score += SearchFieldType.ISBN.getWeight();
            }

            // 출판사
            if (book.publisher() != null && book.publisher().toLowerCase().contains(kw)) {
                score += SearchFieldType.PUBLISHER.getWeight();
            }


            // 도서설명(content)
            if (book.content() != null && book.content().toLowerCase().contains(kw)) {
                score += SearchFieldType.CONTENT.getWeight();
            }

            // tag 추가할 경우 여기도 추가
        }

        return score;
    }


    private Comparator<ScoredBook> resolveComparator(BookSortType sort){
        // 기본: 검색 가중치 점수 내림차순
        Comparator<ScoredBook> byScoreDesc =
                Comparator.comparingInt(ScoredBook::score).reversed();

        if (sort == null) {
            return byScoreDesc;
        }

        return switch (sort) {
            // 인기도: 일단은 가중치 점수 기준 (추후 searchLog, 조회수 연동 가능)
            case POPULAR -> Comparator
                    .comparingLong((ScoredBook sb) -> {
                        String title = sb.book().title();
                        if (title == null || title.isBlank()) {
                            return 0L;
                        }
                        // SearchLog.keyword에 도서 제목이 저장되어 있다고 가정하고 검색 횟수 조회
                        return searchLogService.getSearchCount(title);
                    })
                    .reversed()
                    // 인기도가 같으면 기존 검색 점수(가중치)로 한 번 더 정렬
                    .thenComparing(byScoreDesc);

            // 최저가
            case LOW_PRICE -> Comparator.comparing(sb -> sb.book().price());

            // 최고가
            case HIGH_PRICE -> Comparator
                    .comparing((ScoredBook sb) -> sb.book().price())
                    .reversed();

            // 신상품: 발행일 내림차순
            case NEW -> Comparator
                    .comparing(
                            (ScoredBook sb) -> sb.book().publishedDate(),
                            Comparator.nullsLast(Comparator.naturalOrder())
                    )
                    .reversed();

            // 평점: 리뷰수 100건 이상 우선 + 평균 평점 내림차순
            case RATING -> Comparator
                    // 1단계: reviewCount 100건 이상인 책을 먼저
                    .comparingLong((ScoredBook sb) -> {
                        Long count = sb.book().reviewCount();
                        return (count != null && count >= 100) ? 1L : 0L;
                    })
                    .reversed()
                    // 2단계: 그 안에서 avgRating 내림차순
                    .thenComparing(
                            (ScoredBook sb) -> {
                                Double avg = sb.book().avgRating();
                                return avg != null ? avg : 0.0;
                            },
                            Comparator.reverseOrder()
                    );
            //리뷰 수: reviewCount 기준 내림차순
            case REVIEW -> Comparator
                    .comparingLong((ScoredBook sb) -> {
                        Long cnt = sb.book().reviewCount();
                        return cnt != null ? cnt : 0L;
                    })
                    .reversed();
        };

    }



}
