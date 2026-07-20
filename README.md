# StorePilot Backend

StorePilot의 Spring Boot 백엔드 서버입니다. 인증, 엑셀 업로드, 네이버 카테고리 데이터, 내 카테고리 매핑, 키워드 결과 생성, AI 서버 연동을 담당합니다.

## 기술 스택

- Java 21
- Spring Boot 4
- Spring Security
- Spring Data JPA
- MySQL
- Apache POI
- Springdoc OpenAPI

## 주요 기능

- 이메일/비밀번호 회원가입 및 로그인
- Resend 기반 회원가입 이메일 인증
- HttpOnly 쿠키 기반 Access Token / Refresh Token 인증
- Refresh Token 해시 저장
- 사용자 권한: `USER`, `ADMIN`
- 상품 엑셀 업로드 및 비동기 처리
- 처리 결과 엑셀 다운로드
- 상품 이미지 ZIP 다운로드
- 네이버 카테고리 리스트 업로드
- 내 카테고리 매핑 업로드
- 기존 상품 인덱스 재생성
- 상품 카테고리 피드백 반영
- 카테고리 예측 및 상품 인덱스 재생성을 위한 AI 서버 연동

관리자 전용 API:

- `POST /api/v1/admin/naver-categories/upload`
- `POST /api/v1/admin/training-products/rebuild`
- `POST /api/v1/admin/training-products/feedback`

## 필요 조건

- JDK 21
- MySQL 8 또는 호환 가능한 MySQL 서버
- `http://127.0.0.1:8000`에서 실행 중인 StorePilot AI 서버

## 환경변수

`.env.example`을 `.env`로 복사한 뒤 로컬 값에 맞게 수정합니다.

```env
DB_URL=jdbc:mysql://localhost:3306/storepilot?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
DB_USERNAME=root
DB_PASSWORD=change-me

AI_SERVER_BASE_URL=http://127.0.0.1:8000

STOREPILOT_AUTH_ALLOWED_ORIGINS=http://localhost:3000
STOREPILOT_AUTH_COOKIE_SAME_SITE=Lax
STOREPILOT_AUTH_COOKIE_SECURE=false
STOREPILOT_AUTH_REFRESH_TOKEN_DAYS=14
STOREPILOT_AUTH_ACCESS_TOKEN_MINUTES=30
STOREPILOT_AUTH_JWT_SECRET=change-this-to-a-long-random-secret
STOREPILOT_APP_BASE_URL=http://localhost:3000
STOREPILOT_EMAIL_VERIFICATION_ENABLED=false
STOREPILOT_EMAIL_VERIFICATION_TOKEN_MINUTES=30
STOREPILOT_RESEND_API_KEY=
STOREPILOT_MAIL_FROM=StorePilot <onboarding@resend.dev>
```

Spring Boot는 위 값을 프로세스 환경변수에서 읽습니다. IntelliJ에서 실행한다면 Run Configuration에 환경변수를 추가하거나 env-file 플러그인을 사용합니다. PowerShell에서 실행한다면 앱 실행 전에 `.env`를 현재 프로세스 환경변수로 로드합니다.

```powershell
Get-Content .env | Where-Object { $_ -match '^\s*[^#].*=.*$' } | ForEach-Object {
  $key, $value = $_.Split('=', 2)
  [Environment]::SetEnvironmentVariable($key.Trim(), $value.Trim(), 'Process')
}
```

## 로컬 실행

```powershell
cd C:\Project\StorePilot\be
.\gradlew.bat bootRun
```

백엔드 서버 주소:

```text
http://localhost:8080
```

Swagger UI:

```text
http://localhost:8080/swagger-ui.html
```

## 테스트

```powershell
cd C:\Project\StorePilot\be
.\gradlew.bat test
```

Windows에서 Java가 PATH에 잡히지 않는 경우:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.11.10-hotspot'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
.\gradlew.bat test
```

## 인증 흐름

- `POST /api/v1/auth/signup`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/verify-email`
- `POST /api/v1/auth/refresh`
- `GET /api/v1/auth/me`
- `POST /api/v1/auth/logout`
- `DELETE /api/v1/auth/me`

서버는 아래 쿠키를 발급합니다.

- `storepilot_access_token`
- `storepilot_refresh_token`

두 쿠키 모두 HttpOnly 쿠키입니다. 프론트엔드 요청은 반드시 `credentials: "include"`를 사용해야 합니다.

이메일 인증을 사용할 때는 아래 값을 운영 환경에 설정합니다.

```env
STOREPILOT_APP_BASE_URL=https://your-frontend-domain
STOREPILOT_EMAIL_VERIFICATION_ENABLED=true
STOREPILOT_EMAIL_VERIFICATION_TOKEN_MINUTES=30
STOREPILOT_RESEND_API_KEY=re_xxxxxxxxx
STOREPILOT_MAIL_FROM=StorePilot <no-reply@your-domain>
```

Resend에서 운영 발송을 하려면 발신 도메인을 인증한 뒤 해당 도메인의 주소를 `STOREPILOT_MAIL_FROM`에 사용합니다. 로컬 개발에서는 `STOREPILOT_EMAIL_VERIFICATION_ENABLED=false`로 두면 회원가입 직후 기존처럼 로그인됩니다.

로컬 HTTP 개발 환경:

```env
STOREPILOT_AUTH_COOKIE_SAME_SITE=Lax
STOREPILOT_AUTH_COOKIE_SECURE=false
```

Vercel 프론트엔드와 EC2 백엔드처럼 서로 다른 HTTPS 도메인에서 배포하는 경우:

```env
STOREPILOT_AUTH_COOKIE_SAME_SITE=None
STOREPILOT_AUTH_COOKIE_SECURE=true
STOREPILOT_AUTH_ALLOWED_ORIGINS=https://your-frontend.vercel.app
```

## AI 서버 연동

백엔드는 아래 환경변수를 통해 AI 서버를 호출합니다.

```env
AI_SERVER_BASE_URL=http://127.0.0.1:8000
```

백엔드가 사용하는 주요 AI 서버 API:

- `POST /ai/categories/rebuild`
- `POST /ai/categories/predict`
- `POST /ai/categories/product-index/rebuild`
- `POST /ai/categories/product-index/feedback`

백엔드와 AI 서버를 같은 EC2에 배포한다면 AI 서버는 `127.0.0.1:8000`에만 바인딩하고, 외부에 `8000` 포트를 직접 열지 않는 것을 권장합니다.

## 데이터베이스 주의사항

현재 `application.yml`의 `spring.jpa.hibernate.ddl-auto`는 `update`입니다.

로컬 개발에는 편하지만, 기존 컬럼을 제거하거나 운영 데이터를 안전하게 마이그레이션해주지는 않습니다. 소유권 필드나 인증 스키마가 크게 바뀐 경우 로컬에서는 DB를 새로 만드는 편이 더 안전할 수 있습니다. 운영 배포 전에는 별도의 마이그레이션 도구 도입을 권장합니다.

특정 사용자를 관리자로 바꾸려면:

```sql
UPDATE storepilot_users
SET role = 'ADMIN'
WHERE email = 'admin@example.com';
```

## 업로드 파일

업로드 및 생성 파일은 기본적으로 아래 경로에 저장됩니다.

```text
uploads/
```

이 디렉터리는 런타임 데이터이므로 Git에 커밋하지 않습니다.
