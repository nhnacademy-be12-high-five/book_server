//package com.nhnacademy.book_server.service;
//
//import com.nhnacademy.book_server.dto.BookResponse;
//import com.nhnacademy.book_server.dto.request.BookUpdateRequest;
//import com.nhnacademy.book_server.entity.*;
//import com.nhnacademy.book_server.parser.ParsingDto;
//import com.nhnacademy.book_server.repository.AuthorRepository;
//import com.nhnacademy.book_server.repository.BookAuthorRepository;
//import com.nhnacademy.book_server.repository.BookRepository;
//import com.nhnacademy.book_server.repository.PublisherRepository;
//import org.junit.jupiter.api.DisplayName;
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.extension.ExtendWith;
//import org.mockito.InjectMocks;
//import org.mockito.Mock;
//import org.mockito.junit.jupiter.MockitoExtension;
//import org.springframework.data.domain.Page;
//import org.springframework.data.domain.PageImpl;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Optional;
//
//import static org.assertj.core.api.Assertions.assertThat;
//import static org.assertj.core.api.Assertions.assertThatThrownBy;
//import static org.mockito.ArgumentMatchers.any;
//import static org.mockito.BDDMockito.given;
//import static org.mockito.Mockito.*;
//
//@ExtendWith(MockitoExtension.class) // Mockito 환경에서 실행 (속도가 빠름)
//class BookServiceTest {
//
//    @InjectMocks
//    private BookService bookService; // Mock 객체들이 주입될 대상
//
//    @Mock
//    private BookRepository bookRepository;
//    @Mock
//    private PublisherRepository publisherRepository;
//    @Mock
//    private AuthorRepository authorRepository;
//    @Mock
//    private BookAuthorRepository bookAuthorRepository;
//
//    @Test
//    @DisplayName("도서 생성")
//    void createBook(){
//        ParsingDto dto=new ParsingDto();
//        dto.setIsbn("1234567789012");
//        dto.setTitle("title");
//        dto.setPrice("15000");
//        dto.setPublisher("Publisher");
//        dto.setAuthor("Author");
//        // isbn 중복 체크
//
//        given(bookRepository.existsByIsbn13(any())).willReturn(true);
//        // 3. 책 저장시 반환될 객체
//        Book savedBook = Book.builder()
//                .id(1L)
//                .title("Test Title")
//                .price(15000)
//                .build();
//
//        Publisher publisher= Publisher.builder()
//                .PublisherId(1L)
//                .name("publisher")
//                .build();
//
//        Author author=Author.builder()
//                .id(1L)
//                .name("name")
//                .build();
//
//        BookAuthor bookAuthor=BookAuthor.builder()
//                .book(savedBook)
//                .author(author)
//                .build();
//
//        given(bookRepository.save(any(Book.class))).willReturn(savedBook);
//        given(publisherRepository.save(any(Publisher.class))).willReturn(publisher);
//        given(authorRepository.save(any(Author.class))).willReturn(author);
//         given(bookAuthorRepository.save(any(BookAuthor.class))).willReturn(bookAuthor);
//        //
//        Book result1=bookService.createBook(dto);
//        assertThat(result1.getTitle()).isEqualTo("Test Title");
//
//        verify(bookRepository, times(1)).save(any(Book.class));
//        verify(publisherRepository, times(1)).save(any(Publisher.class)); // 출판사 저장됨
//        verify(authorRepository,times(1)).save(any(Author.class));
//    }
//
//    @Test
//    @DisplayName("도서 전체 조회")
//    void findAllBooks() {
//
//        // given
//        List<Book> books = List.of(
//                Book.builder().title("Book1").build(),
//                Book.builder().title("Book2").build()
//        );
//
//        Pageable pageable = PageRequest.of(0,10);
////        Page<BookResponse> bookPage = new PageImpl<>(books, pageable, books.size());
//
////        given(bookRepository.findAll(pageable)).willReturn(bookPage);
//        given(bookRepository.findAll()).willReturn(books);
//
//        // when
//        Page<BookResponse> result = bookService.findAllBooks(pageable);
//        // then
//        assertThat(result).hasSize(2);
//        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
//        assertThat(result.getContent().get(0).title()).isEqualTo("Book1");
//    }
////
//    @Test
//    @DisplayName("도서 단건 조회 - 성공")
//    void findBookById_Success() {
//        // given
//        Long bookId = 1L;
//        Book book = Book.builder().id(bookId).title("Book1").build();
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(book));
//
//        // when
//
//        Page<BookResponse> result = bookService.findAllBooks(pageable);
//
//
//        // then
//        assertThat(result).isPresent();
//        assertThat(result.get().getTitle()).isEqualTo("Book1");
//    }
//
//    @Test
//    @DisplayName("도서 업데이트 - 성공 (작가 포함 수정)")
//    void updateBook_WithAuthors() {
//        // given
//        Long bookId = 1L;
//        BookUpdateRequest request = new BookUpdateRequest();
//        request.setAuthors(List.of("New Author")); // 작가 추가
//
//        Book existingBook = Book.builder().id(bookId).bookAuthors(new ArrayList<>()).build();
//        Author newAuthor = Author.builder().name("New Author").build();
//
//        given(bookRepository.findById(bookId)).willReturn(Optional.of(existingBook));
//        // 작가 조회 Mock
//        given(authorRepository.findByName("New Author")).willReturn(Optional.of(newAuthor));
//
//        // when
//        bookService.updateBook(bookId, request);
//
//        // then
//        // 작가가 포함되면 save가 호출되므로 검증 가능
//        verify(bookRepository, atLeastOnce()).save(existingBook);
//    }
//
//    @Test
//    @DisplayName("도서 업데이트 - 실패 (존재하지 않는 ID)")
//    void updateBook_Fail_NotFound() {
//        // given
//        Long bookId = 999L;
//        BookUpdateRequest request = new BookUpdateRequest();
//        given(bookRepository.findById(bookId)).willReturn(Optional.empty());
//
//        ParsingDto dto=new ParsingDto();
//        dto.setIsbn("1234567789012");
//        dto.setTitle("title");
//        dto.setPrice("15000");
//        dto.setPublisher("Publisher");
//
//        // when & then
//        assertThatThrownBy(() -> bookService.updateBook(bookId, request))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessage("아이디가 존재하지 않습니다.");
//    }
//
//    @Test
//    @DisplayName("도서 삭제 - 성공")
//    void deleteBook_Success() {
//        // given
//        Long bookId = 1L;
//        given(bookRepository.existsById(bookId)).willReturn(true);
//
//        // when
//        bookService.deleteBook(bookId, 1L);
//
//        // then
//        verify(bookRepository, times(1)).deleteById(bookId);
//    }
//
//    @Test
//    @DisplayName("도서 삭제 - 실패 (존재하지 않는 ID)")
//    void deleteBook_Fail_NotFound() {
//        // given
//        Long bookId = 999L;
//        given(bookRepository.existsById(bookId)).willReturn(false);
//
//        // when & then
//        assertThatThrownBy(() -> bookService.deleteBook(bookId, 1L))
//                .isInstanceOf(RuntimeException.class)
//                .hasMessage("삭제할 아이디가 없습니다.");
//
//        // deleteById는 호출되지 않아야 함
//        verify(bookRepository, never()).deleteById(any());
//    }
//}