package com.nhnacademy.book_server.request;

import java.util.ArrayList;
import java.util.List;

public class AladinListRequest {

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
