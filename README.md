# 근무 시간표 제작 서비스
![Java](https://img.shields.io/badge/java-17%2B-blue.svg)
![Build](https://img.shields.io/badge/build-Gradle-success.svg)
> 최적의 근무 시간표를 자동 산출하는 1인 개발 서비스

- [사이트 링크](https://scheduler.j30ngwoo.site)
- [📦 API 명세서 (Swagger UI)](https://scheduler.j30ngwoo.site/api/swagger-ui/index.html)
- [📦 Frontend Code Repository](https://github.com/j30ngwoo/scheduler-frontend)

본교 학생회 활동을 할 당시 학생회실 상근 업무를 위한 시간표를 제작하였습니다. 모든 인원의 시간표를 별도로 수합하여 수동으로 근무를 배치하는 기존 방식이 매우 비효율적으로 느껴졌습니다. 각 인원의 시간표를 편리하게 입력해 관리하고, 최적의 상근 시간표를 산출하는 서비스를 개발하였습니다.

---
## 📝 Technology Stack

| Category            | Technology                               |
|---------------------|------------------------------------------|
| Language            | Java 21                   |
| Framework           | Spring Boot 3.5.0              |
| Databases           | MySQL                      |
| Authentication      | JWT                      |
| Development Tools   | Slf4j, Lombok, Data JPA         |
| API Documentation   | Swagger UI                |
| Deployment          | Docker + Github Actions      | 

---
## 실행 방법
```
./gradlew clean build -x test       # Gradle 프로젝트 빌드
docker build -t scheduler:latest .  # Docker 이미지 빌드
docker rm scheduler
docker run -d -p 9003:8080 --name scheduler -e SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.h2.Driver -e SPRING_DATASOURCE_URL="jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL" -e SPRING_DATASOURCE_USERNAME=sa -e SPRING_DATASOURCE_PASSWORD= -e KAKAO_CLIENT_SECRET=dummy-secret -e JWT_SECRET=dummy-jwt-secret-1234567890-abcdefghijklmnopqrstuvwxyz scheduler:latest
```

실행 후 Swagger UI 접속:
[http://localhost:9003/api/swagger-ui/index.html](http://localhost:9003/api/swagger-ui/index.html) 

---
## 🔑 Key Features

### 1. 사용자 인증 및 접근 관리
- Kakao OAuth 기반 회원가입 및 로그인
- 자체 Access / Refresh 토큰 및 로그아웃 구현
- HandlerInterceptor와 HandlerMethodArgumentResolver를 사용하여 JWT 인증을 직접 구현

### 2. 시간표 & 참가자 일정 관리
- 시간표 CRUD
- 개별 참가자 일정 CRUD
- 시간 일정을 0/1 Text로 저장하여 유연성 확보

### 3. 최적 시간표 산출
- 참가자별 최소 / 최대 할당 시간, 시간 슬롯당 최대 인원 설정
- 강의실 이동 시간 고려 옵션 / 수업일에 근무 우선 배정 옵션
- 그리디 및 휴리스틱 알고리즘 사용으로 구현효율 및 시간복잡도 고려
  1. 할당 가능 시간이 적은 사람부터 배정
  2. 각 인원별 길이가 가장 긴 세그먼트(가능 시간 구간)에 우선 배정 - 근무 시간의 연속성 확보
  3. 할당이 덜 된 인원 위주로 2차 배정

---
## 📂 패키지 구조
```
/src/main/java/com/j30ngwoo/scheduler/
│ 
├── common/                          # 공통 유틸
│   ├── exception/                   # 예외 처리
│   └── response/                    # 공통 API 응답 포맷
│
├── config/                          # 설정 관련 Bean 등록
│   ├── resolver/                    # 컨트롤러 파라미터 리졸버
│   ├── AuthInterceptor.java         # 인증/인가 Interceptor (JWT 검증)
│   ├── RestClientConfig.java        # RestClient 설정
│   └── WebMvcConfig.java            # 스프링 MVC 설정 (인터셉터, 리졸버 등록 등)
│
├── controller/                      # API 엔드포인트
│   ├── AuthController.java              # 로그인/로그아웃/토큰 관련 API
│   ├── AvailabilityController.java      # 참가자별 가능 시간 관리 API
│   ├── OptimizationController.java      # 스케줄 최적화 관련 API
│   ├── ScheduleController.java          # 스케줄 CRUD API
│   └── UserController.java              # 사용자 관리 API
│
├── domain/                          # JPA Entity
│   ├── Availability.java
│   ├── RefreshToken.java
│   ├── Schedule.java
│   └── User.java
│
├── dto/                             # 요청/응답 DTO
│   ├── AvailabilityResponse.java
│   ├── AvailabilitySubmitRequest.java
│   ├── KakaoLoginResponse.java
│   ├── ScheduleCreateRequest.java   
│   ├── ScheduleOptimizeRequest.java   
│   ├── ScheduleOptionUpdateRequest.java 
│   ├── ScheduleResponse.java  
│   └── UserResponse.java
│
├── repository/                      # JPA Repository
│   ├── AvailabilityRepository.java
│   ├── RefreshTokenRepository.java
│   ├── ScheduleRepository.java
│   └── UserRepository.java
│
└── service/                         # 비즈니스 로직 계층
    ├── AuthService.java                 # 인증/인가 처리
    ├── AvailabilityService.java         # 참가자별 가능 시간 관련 로직
    ├── KakaoOAuthService.java           # 카카오 OAuth 처리
    ├── ScheduleOptimizerService.java    # 스케줄 최적화 알고리즘
    └── ScheduleService.java             # 스케줄 CRUD 및 관리 로직


```



