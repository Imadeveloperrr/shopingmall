package com.example.crud;

import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.repository.MemberRepository;
import com.example.crud.security.JwtToken;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Slf4j
public class MemberControllerTest {

    @Autowired
    MemberService memberService;
    @Autowired
    TestRestTemplate testRestTemplate;
    @Autowired
    MemberRepository memberRepository;
    @LocalServerPort
    int randomServerPort;

    private MemberDto memberDto;

    @BeforeEach
    void beforEach() {
        // Test 실행 되기 전 초기화 작업
        memberDto = MemberDto.builder()
                .email("ddooochii@gmail.com")
                .name("이성호")
                .nickname("헬로ㅋ")
                .password("1234")
                .build();
    }

    @AfterEach
    void afterEach() {
        // Test 실행 후 초기화
        memberRepository.deleteAll();
    }

    @Test
    public void signUpTest() {

        String url = "http://localhost:" + randomServerPort + "/register";
        ResponseEntity<MemberResponseDto> response = testRestTemplate.postForEntity(url, memberDto, MemberResponseDto.class);

        //log.info("response : {}", response.getBody());
        // 응답 검증
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        assertEquals(response.getBody().getEmail(), memberDto.getEmail());
        assertEquals(response.getBody().getName(), memberDto.getName());
    }

    @Test
    public void signInTest() {
        memberService.signUp(memberDto);

        MemberDto memberRequestDto = MemberDto.builder()
                .email("ddooochii@gmail.com")
                .password("1234")
                .build();
        String url = "http://localhost:" + randomServerPort + "/login";

        // 로그인 요청
        ResponseEntity<JwtToken> response = testRestTemplate.postForEntity(url, memberRequestDto, JwtToken.class);
        assertEquals(response.getStatusCode(), HttpStatus.OK);

        //JwtToken jwtToken = memberService.signIn(memberRequestDto.getEmail(), memberRequestDto.getPassword());


        // API 요청 설정

        /*HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setBearerAuth(jwtToken.getAccessToken());
        httpHeaders.setContentType(MediaType.APPLICATION_JSON);

        //log.info("httpHeaders = {}", httpHeaders);
        //HttpEntity<MemberDto> requestEntity = new HttpEntity<>(memberRequestDto, httpHeaders);

        String url = "http://localhost:" + randomServerPort + "/login";
        ResponseEntity<JwtToken> responseEntity = testRestTemplate.postForEntity(url, new HttpEntity<>(httpHeaders), JwtToken.class);
        assertEquals(HttpStatus.OK, responseEntity.getStatusCode());
        assertEquals(responseEntity.getBody(), memberRequestDto.getEmail());*/
    }
}
