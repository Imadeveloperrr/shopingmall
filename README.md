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

@Transactional // 새로운 @Transactional 어노테이션을 사용하여 기본 설정을 오버라이딩함. 즉, 이 메서드에서는 데이터를 변경할 수 있음.
    @Override
    public JwtToken signIn(String username, String password) {
        // 1. username + password 를 기반으로 Authentication 객체 생성
        // 이때 authentication은 인증 여부를 확인하는 authenticated 값이 false
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        /* 2. 실제 검증.
        AuthenticationManager가 authenticate() 메서드를 통해 요청된 User 대한 검증 진행
        - AuthenticationProvider 내부에서 UserDetailesService의 loadUserByUsername 메서드가 호출됨.
        - 이 과정에서 authenticationToken 인스턴스에 저장된 사용자 이름이 loadUserByUsername 인자로 전달!
        - 그 후 Override한 loadUserByUsername은 UserDetails 객체를 반환 AuthenticationProvider는 반환된 UserDetails 객체와
        authenticationToken 인스턴스 즉 UsernamePasswordAuthenticationToken 의 자격증명을 비교하여 인증 과정을 완료.
        비밀번호가 일치하면 사용자는 인증되고 authentication은 authenticated 값이 true로 업데이트됩니다잉
        */
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);

        /*
        * UsernamePasswordAuthenticationToken 객체의 username, password를 입력받아
        * Authentication 객체를 생성한후 그 객체를 다시 AuthenticationManager의 authenticate 메서드를
        * 사용해서 Authentication 객체를 입력시킨뒤 인증된 사용자의 정보를 담고있는 Authentication 객체를
        * 반환하고 !!!! 나서 JwtTokenProvider의 generateToken 메서드로 토큰을 만들어버린다...
        * */
        return jwtToken;
    }


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



