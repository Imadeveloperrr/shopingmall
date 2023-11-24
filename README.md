# shopingmall


<img width="{80%}" src="https://github.com/Imadeveloperrr/shopingmall/assets/99321607/54ffe5db-39d5-40a2-9d30-3b4bd6cd5de1"/>


### Frontend Stack

1. bootstrap
2. Ajax (XMLHttpRequest 객체와 Fetch API 둘다 사용, Fetch API는 주석처리해놈 공부목적)
3. thymeleaf

### Backend Stack
1. Spring boot 3.1.4
2. Spring Security 6.x + JWT Token
3. JPA
4. Loombok
5. Maria db
6. Jupiter


----------------- Notes  -------------- 

<img width="{80%}" src="https://github.com/Imadeveloperrr/shopingmall/assets/99321607/58758ddf-a843-4ca7-badb-114a19a11c18"/>


## Spring Security 동작 원리

1. Http Request가 서버로 전송된다.
2. AuthenticationFilter가 요청을 받는다.
3. AuthenticationFilter에서 Request의 Id, Password를 이용하여 AuthenticationToken 생성
4. 토큰을 AuthenitcationManager가 받는다.
5. AuthenticationManager는 토큰을 AuthenticationProvider에게 토큰을 넘겨준다.
6. AuthenticationProvider는 UserDetailsService로 토근의 사용자 아이디를 전달하여 DB에 ID 존재를 확인한다. 이 때, UserDetailsService는 DB의 회원정보를 UserDetails라는 객체로 반환 받는다.
7. AuthenticationProvider는 반환받은 UserDetails 객체와 실제 사용자의 입력정보를 비교한다.
8. 비교가 완료되면 사용자 정보를 가진 Authentication 객체를 SecurityContextHolder에 담은 이후 AuthenticationSuccessHandle를 실행한다.(실패시 AuthenticationFailureHandler를 실행한다.)


Return value of username of UserDetails interface in Entity has been overridden into email field

private String email;

@Override
public String getUsername() {
    return this.email;
    }


----------------- Future Updates -------------- 



----------------- Releases --------------



