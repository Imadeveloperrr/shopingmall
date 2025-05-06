package com.example.crud;

import com.example.crud.data.member.dto.MemberDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 컨트롤러 통합 테스트
 *  - H2 in-memory DB (application-test.properties)
 *  - MockMvc 로 실제 HTTP 레이어 검증
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class MemberControllerIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper om;   // JSON 직렬화·역직렬화

    /** 공통으로 쓸 테스트 사용자 */
    private MemberDto givenMember() {
        return MemberDto.builder()
                .email("ddooochii@gmail.com")
                .password("12345678")
                .name("이성호")
                .nickname("헬로ㅋ")
                .rememberMe(true)
                .build();
    }

    @Nested
    @DisplayName("회원 가입 + 로그인 + 마이페이지 시나리오")
    class SignUpLoginFlow {

        @Test
        @Transactional
        void signUp_login_then_getMyPage() throws Exception {

            /* ---------- 1) 회원가입 ---------- */
            mockMvc.perform(post("/register")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(givenMember())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.email").value("ddooochii@gmail.com"))
                    .andExpect(jsonPath("$.nickname").value("헬로ㅋ"));

            /* ---------- 2) 로그인(쿠키에 JWT 세팅) ---------- */
            var loginResult = mockMvc.perform(post("/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(om.writeValueAsString(givenMember())))
                    .andExpect(status().isOk())
                    .andExpect(cookie().exists("accessToken"))
                    .andExpect(cookie().exists("refreshToken"))
                    .andReturn();

            // accessToken 쿠키 꺼내기
            Cookie accessTokenCookie = loginResult.getResponse().getCookie("accessToken");
            assertThat(accessTokenCookie).isNotNull();

            /* ---------- 3) JWT 가 든 쿠키로 마이페이지 호출 ---------- */
            mockMvc.perform(get("/mypage")
                            .cookie(accessTokenCookie))
                    .andExpect(status().isOk())
                    .andExpect(view().name("fragments/mypage"))
                    .andExpect(model().attributeExists("member"))
                    .andExpect(model().attributeExists("products"));
        }
    }
}
