package com.nhnacademy.book_server.service.read;

import com.nhnacademy.book_server.dto.BookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class BookReadServiceImpl implements BookReadService {

    //csv파서 준비되면 주입

    @Override
    public List<BookResponse> findAllBooks(){
        return Collections.emptyList(); //실제도서리스트 반환하도록 수정
    }

    @Override
    public Optional<BookResponse> findBookById(Long id){
        return Optional.empty(); //도서 id기준 조회로 수정
    }

}
