package com.nhnacademy.book_server.service;

import com.nhnacademy.book_server.dto.BookResponse;
import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
import com.nhnacademy.book_server.dto.response.GetBookResponse;
import com.nhnacademy.book_server.entity.*;
import com.nhnacademy.book_server.parser.ParsingDto;
import com.nhnacademy.book_server.repository.AuthorRepository;
import com.nhnacademy.book_server.repository.BookAuthorRepository;
import com.nhnacademy.book_server.repository.BookRepository;
import com.nhnacademy.book_server.repository.PublisherRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BookService {

    private final BookRepository bookRepository;
    private final PublisherRepository publisherRepository;
    private final AuthorRepository authorRepository;
    private final BookAuthorRepository bookAuthorRepository;
    private final MinioImageService minioImageService;

    public Book createBook(ParsingDto dto){
        if (bookRepository.existsByIsbn13(dto.getIsbn())) {
            log.warn("이미 존재하는 ISBN입니다: {}", dto.getIsbn());
        }

        Publisher publisher = null;
        if (StringUtils.hasText(dto.getPublisher())) {
            String publisherName = dto.getPublisher().trim();
            publisher = publisherRepository.findByName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder().name(publisherName).build()
                    ));
        }

        String finalUrl = minioImageService.uploadImageFromUrl(dto.getImageUrl(), dto.getIsbn());

        Book newBook = Book.builder()
                .isbn13(dto.getIsbn())
                .title(dto.getTitle())
                .publisher(publisher)
                .publishedDate(dto.getPubDate())
                .price(parsePrice(dto.getPrice()))
                .image(finalUrl)
                .content(dto.getDescription())
                .build();

        Book savedBook = bookRepository.save(newBook);

        if (StringUtils.hasText(dto.getAuthor())) {
            String[] authorNames = dto.getAuthor().split(",");
            for (String name : authorNames) {
                String trimmedName = name.trim();
                if (trimmedName.isEmpty()) continue;

                // 작가 조회 없으면 생성
                Author author = authorRepository.findByName(trimmedName)
                        .orElseGet(() -> authorRepository.save(
                                Author.builder().name(trimmedName).build()
                        ));

                // BookAuthor 연결 관계 저장
                BookAuthor bookAuthor = BookAuthor.builder()
                        .book(savedBook)
                        .author(author)
                        .build();

                bookAuthorRepository.save(bookAuthor);
            }
        }

        return savedBook;
    }

    // 모든 책 조회
    // list -> Pageable로 변환
    @Transactional(readOnly = true)
    public Page<BookResponse> findAllBooks(Pageable pageable){
        return bookRepository.findAll(pageable)
                .map(BookResponse::from);
    }

    // 책 한권 조회
    @Transactional(readOnly = true)
    public BookResponse findBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("책을 찾을 수 없습니다."));

        // Service 안에서 변환하므로 Lazy Loading 문제 없음 (이미 EntityGraph로 가져왔지만)
        return BookResponse.from(book);
    }

    // 책 업데이트
    @Transactional // 💡 트랜잭션 적용
    public Book updateBook(Long id, BookUpdateRequest request){
        Book existingBook = bookRepository.findById(id).orElseThrow(()->new RuntimeException("아이디가 존재하지 않습니다."));

        String finalUrl = minioImageService.uploadImageFromUrl(request.getImage(), request.getIsbn());

        minioImageService.deleteImages(List.of(existingBook.getImage()));

        existingBook.setIsbn13(request.getIsbn());
        existingBook.setTitle(request.getTitle());
        existingBook.setContent(request.getDescription());
        existingBook.setPrice(request.getPrice());
        existingBook.setImage(finalUrl);
        existingBook.setPublishedDate(request.getPublishedDate());

        if (StringUtils.hasText(request.getPublisher())) {
            String publisherName = request.getPublisher().trim();
            Publisher publisher = publisherRepository.findByName(publisherName)
                    .orElseGet(() -> publisherRepository.save(
                            Publisher.builder().name(publisherName).build()
                    ));

            existingBook.setPublisher(publisher);
        }

        if (request.getAuthors() != null){
            existingBook.getBookAuthors().clear();

            for (String authorName: request.getAuthors()){
                String trimmedName = authorName.trim();

                if(!StringUtils.hasText(trimmedName)) continue;
                Author author=authorRepository.findByName(authorName).orElseGet(()->authorRepository.save(Author.builder().name(authorName).build()));

                BookAuthor bookAuthor = BookAuthor.builder()
                        .book(existingBook)  // 중요: 현재 책 정보 주입
                        .author(author)      // 중요: 찾은 작가 정보 주입
                        .build();

                existingBook.getBookAuthors().add(bookAuthor);
                bookRepository.save(existingBook);
            }
        }

        return existingBook;
    }

    // 책 삭제
    public void deleteBook(Long id,Long memberId){
        if (!bookRepository.existsById(id)) {
            throw new RuntimeException("삭제할 아이디가 없습니다.");
        }

        bookRepository.deleteById(id);
    }

    private Integer parsePrice(String priceStr) {
        if (!StringUtils.hasText(priceStr)) return 0;
        try {
            return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // bulk api 조회
    // 장바구니에서 책을 조회할때 책을 1번만 호출하도록 하는 API
    // Service Layer
    public List<GetBookResponse> getBooksBulk(List<Long> bookIds) {
        List<Book> books = bookRepository.findAllById(bookIds);

        // List를 Map<BookId, Dto> 형태로 변환
        return books.stream()
                .map(book -> new GetBookResponse(
                        book.getId(),
                        book.getTitle(),
                        book.getPrice(),
                        book.getImage()                // 이미지
                ))
                .collect(Collectors.toList());

    }

    // 재고 확인 (단순 조회이므로 readOnly)
    @Transactional(readOnly = true)
    public int getBookStock(Long bookId) {
        // 1. 전체 엔티티를 다 가져오는 건 낭비일 수 있음.
        // 단순히 재고만 확인할 거라면 Repository에서 재고 컬럼만 가져오는 쿼리를 짜는 게 성능상 베스트.
        // 하지만 일단 기존 로직을 유지하면서 Service로 옮긴다면:

        return bookRepository.findById(bookId)
                .map(book -> {
                    // 만약 getStockCheckedAt이 Boolean이 아니라 날짜라거나 로직이 있다면 여기서 처리
                    // 예시: 재고 필드가 따로 있다면 book.getStock() 반환
                    boolean inStock = Boolean.TRUE.equals(book.getStockCheckedAt());
                    return inStock ? 1 : 0;
                })
                .orElse(0); // 책이 없으면 재고 0 처리
    }

}

