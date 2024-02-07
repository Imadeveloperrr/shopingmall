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




## signIn Service jwtToken 생성 과정

JWT 토큰 생성 과정
이 서비스는 사용자 인증 후 JWT 토큰을 생성하는 과정을 담당합니다. 과정은 크게 세 단계로 나눌 수 있으며, 각 단계는 다음과 같습니다.

1. Authentication 객체 생성
// 사용자의 username과 password를 기반으로 Authentication 객체를 생성합니다.
// 이 시점에서의 Authentication 객체는 아직 인증되지 않았으므로, authenticated 속성은 false입니다.
UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

2. 사용자 인증
/* 실제 사용자 인증을 처리합니다.
   - AuthenticationManager의 authenticate 메소드를 통해 사용자 인증을 시도합니다.
   - AuthenticationProvider는 내부적으로 UserDetailsService의 loadUserByUsername 메소드를 호출하여 사용자 정보를 불러옵니다.
   - 불러온 UserDetails 객체와 입력받은 AuthenticationToken(UsernamePasswordAuthenticationToken) 내의 정보를 비교하여 인증을 완료합니다.
   - 비밀번호가 일치할 경우, 사용자는 인증되며 Authentication 객체의 authenticated 속성이 true로 업데이트됩니다.
*/
Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

3. JWT 토큰 생성
// 인증된 Authentication 객체를 기반으로 JWT 토큰을 생성합니다.
JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);


인증 과정은 사용자의 username과 password를 받아 UsernamePasswordAuthenticationToken 객체를 생성하고, 이를 AuthenticationManager의 authenticate 메소드에 전달하여 사용자를 인증합니다. 인증이 성공적으로 완료되면, JwtTokenProvider의 generateToken 메소드를 사용하여 인증된 사용자 정보를 바탕으로 JWT 토큰을 생성하게 됩니다.



## Spring Security 동작 원리

1. Http Request가 서버로 전송된다.
2. AuthenticationFilter가 요청을 받는다.
3. AuthenticationFilter에서 Request의 Id, Password를 이용하여 AuthenticationToken 생성
4. 토큰을 AuthenitcationManager가 받는다.
5. AuthenticationManager는 토큰을 AuthenticationProvider에게 토큰을 넘겨준다.
6. AuthenticationProvider는 UserDetailsService로 토근의 사용자 아이디를 전달하여 DB에 ID 존재를 확인한다. 이 때, UserDetailsService는 DB의 회원정보를 UserDetails라는 객체로 반환 받는다.
7. AuthenticationProvider는 반환받은 UserDetails 객체와 실제 사용자의 입력정보를 비교한다.
8. 비교가 완료되면 사용자 정보를 가진 Authentication 객체를 SecurityContextHolder에 담은 이후 AuthenticationSuccessHandle를 실행한다.(실패시 AuthenticationFailureHandler를 실행한다.)


* UserDetails 인터페이스의 username 필드를 email로 변경하였음

private String email;

@Override
public String getUsername() {
    return this.email;
    }


----------------- Future Updates -------------- 



----------------- Releases --------------



