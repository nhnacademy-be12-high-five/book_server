package com.nhnacademy.book_server.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.PARAMETER) // 파라미터에 붙이는 놈이다
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentMember {
}