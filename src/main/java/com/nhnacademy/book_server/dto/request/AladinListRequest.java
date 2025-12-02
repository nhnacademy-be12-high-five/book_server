package com.nhnacademy.book_server.dto.request;

import java.util.ArrayList;
import java.util.List;

public class AladinListRequest {

    // 상품 리스트 API 는 제공하는 리스트 중 선택

    private String ttbkey;
   List<Items> items=new ArrayList<>();

   static class Items{
       public String title;
       public String image;
       public String author;
       public String isbn;
       public String description;
   }
}
