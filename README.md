# shopingmall


<img width="{80%}" src="https://github.com/Imadeveloperrr/shopingmall/assets/99321607/54ffe5db-39d5-40a2-9d30-3b4bd6cd5de1"/>


Frontend Stack

1. bootstrap
2. Ajax (XMLHttpRequest 객체와 Fetch API 둘다 사용, Fetch API는 주석처리해놈 공부목적)


Backend Stack
1. Spring boot 3.1.4
2. Spring Security 5.6.2 + JWT Token
3. JPA
4. Loombok
5. Maria db
6. thymeleaf

----------------- Notes  -------------- 

Return value of username of UserDetails interface in Entity has been overridden into email field

private String email;

@Override
public String getUsername() {
    return this.email;
    }

----------------- Future Updates -------------- 
