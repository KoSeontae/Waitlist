# Waitlist

예약 취소로 발생한 빈자리를 대기자에게 자동으로 승계해 주는 예약 관리 서비스.

정원이 있는 수업·시설에서 취소가 발생하면, 대기 순서에 따라 다음 사용자에게 예약 기회를 제공하고 제한 시간 안에 수락하지 않으면 다음 대기자에게 넘긴다.

## 핵심 개념

| 용어 | 설명 |
|---|---|
| 회차 (Slot) | 정원이 있는 예약 단위. 특정 일시에 열리는 수업·공연·시설 이용 시간대 |
| 예약 (Reservation) | `CONFIRMED` / `CANCELED` |
| 대기 (WaitlistEntry) | `WAITING` → `OFFERED` → `ACCEPTED` / `EXPIRED` / `CANCELED` |
| 승계 | 빈자리가 순번대로 대기자에게 넘어가 예약으로 확정되는 과정 |
| 중단 (BLOCKED) | 회차의 승계, 신규 예약, 대기 신청을 모두 멈춘 상태 (운영자 수동 / 정원 초과 자동) |

## 기술 스택

Java 21, Spring Boot 4.1.1, Spring Security + JWT, Spring Data JPA, PostgreSQL 16, Flyway, Testcontainers

## 실행

사전 준비: JDK 21, Docker

```bash
docker compose up -d
./gradlew bootRun
```

- API 문서(Swagger UI): http://localhost:8080/swagger-ui.html
- 헬스체크: http://localhost:8080/actuator/health

환경 변수로 설정을 바꿀 수 있다.

| 변수 | 기본값 | 설명 |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5433/waitlist` | DB 주소 |
| `DB_USERNAME` / `DB_PASSWORD` | `slot` / `slot` | DB 계정 |
| `JWT_SECRET` | 로컬 개발용 값 | 배포 시 반드시 변경 |

## 테스트

Docker가 필요하다(Testcontainers가 PostgreSQL 컨테이너를 띄운다).

```bash
./gradlew test
```

동시성 시나리오 재현 테스트는 따로 실행한다. 결과는 `build/concurrency-report.md`에 기록된다. 동시성 제어를 도입하기 전까지는 실패하는 것이 정상이다.

```bash
./gradlew concurrencyTest
```

부하 테스트는 [loadtest/README.md](loadtest/README.md)를 따른다.

## 구조

```
com.example.waitlist
├─ global        오류 처리, 보안(JWT), DB 시각, OpenAPI 설정
├─ user          회원가입, 로그인
├─ slot          회차 생성·조회, 운영자 중단·설정 변경·조회
├─ reservation   예약, 예약 취소 (사용자 / 운영자)
├─ waitlist      대기, 승계, 수락·거절, 만료 스케줄러
└─ notification  알림 (앱 내 / 문자 모의 발송)
```

## 브랜치 전략

- 작업마다 `dev`에서 브랜치를 만들어 작업하고 `dev`로 PR을 보낸다
- `main`은 `dev`에서 서비스 동작을 확인한 뒤 마지막에 병합한다
