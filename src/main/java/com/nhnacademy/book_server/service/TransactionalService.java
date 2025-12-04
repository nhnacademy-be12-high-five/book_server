package com.nhnacademy.book_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class TransactionalService {
    // 이 메서드를 호출하면 즉시 Commit하고 다음 작업으로 넘어갑니다.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public <T> T executeInNewTransaction(Supplier<T> supplier) {
        return supplier.get();
    }
}