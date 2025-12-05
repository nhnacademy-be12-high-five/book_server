package com.nhnacademy.book_server.feign;

import com.nhnacademy.book_server.dto.response.MemberResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(name = "team5-member-server")
public interface MemberFeignClient {

    @PostMapping("/loginIds")
    List<MemberResponse> getMembersInfo(@RequestBody List<Long> memberIds);

}
