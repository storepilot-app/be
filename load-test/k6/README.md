# StorePilot k6 부하 테스트

프런트엔드를 거치지 않고 다음 상품 엑셀 작업을 재현하는 첫 번째 테스트입니다.

1. 테스트 계정으로 로그인합니다.
2. 엑셀 파일을 업로드하여 비동기 작업을 생성합니다.
3. 2초마다 작업 상태를 조회합니다.
4. 완료 시간, 대략적인 큐 대기 시간, 실패 여부를 기록합니다.

가상 사용자마다 서로 다른 계정을 사용합니다. 아직 완료된 결과 엑셀을 다운로드하는
과정은 포함하지 않습니다.

## 사전 준비

- StorePilot BE가 실행 중이어야 합니다(기본 주소: `http://localhost:8080`).
- BE가 사용하는 MySQL과 AI 서버가 실행 중이어야 합니다.
- k6가 설치되어 있어야 합니다.
- 이메일 인증까지 완료한 테스트 계정이 있어야 합니다.
- 민감한 정보가 없는 `.xlsx` 테스트 파일이 있어야 합니다.

Windows에서 k6를 설치하고 확인합니다.

```powershell
winget install k6 --source winget
k6 version
```

## 로컬 테스트 파일 준비

계정 예시 파일을 복사한 후 테스트 전용 계정 정보로 변경합니다.

```powershell
Copy-Item users.example.json users.local.json
```

작은 테스트 엑셀을 다음 위치에 넣습니다.

```text
fixtures/products-small.xlsx
```

`users.local.json`, 테스트 엑셀, 실행 결과는 Git에서 제외됩니다. 운영 계정이나
개인정보·민감정보가 들어 있는 엑셀은 사용하지 마세요.

## 실행

다음 폴더에서 명령을 실행합니다.

```powershell
cd C:\Project\StorePilot\be\load-test\k6
```

먼저 가상 사용자 1명으로 실행합니다.

```powershell
k6 run -e VUS=1 product-excel-job.js
```

성공하면 서로 다른 계정 2개로 동시에 실행합니다.

```powershell
k6 run -e VUS=2 product-excel-job.js
```

두 테스트가 모두 성공하면 `users.local.json`에 계정을 추가하고 5명으로 실행합니다.

```powershell
k6 run -e VUS=5 product-excel-job.js
```

BE 주소, 엑셀 파일, 조회 주기, 제한 시간을 바꾸려면 다음처럼 실행합니다.

```powershell
k6 run `
  -e VUS=2 `
  -e BASE_URL=http://localhost:8080 `
  -e EXCEL_FILE=./fixtures/products-medium.xlsx `
  -e POLL_SECONDS=2 `
  -e JOB_TIMEOUT_SECONDS=1800 `
  product-excel-job.js
```

## 처음 확인할 지표

- `http_req_failed`: HTTP 요청 실패율
- `http_req_duration`: 로그인, 업로드, 상태 조회 응답시간
- `job_duration_ms`: 작업 생성 요청부터 완료까지 걸린 시간
- `job_queue_wait_ms`: 처음 `PROCESSING` 상태를 확인하기까지 걸린 대략적인 시간
- `job_failed`: 비동기 작업의 최종 실패율
- `checks`: 로그인, 작업 생성, 상태 조회 검사 성공률

상태를 일정 간격으로 조회하므로 큐 대기 시간은 근삿값입니다. 작업이 매우 빨리 끝나
`PROCESSING`을 한 번도 확인하지 못한 경우에는 큐 대기 시간이 기록되지 않을 수 있습니다.
`PENDING`은 실패가 아니며 제한 시간 안에 최종적으로 `COMPLETED`가 되면 성공입니다.

## 권장 첫 실행 순서

1. 작은 엑셀로 사용자 1명 테스트를 실행합니다.
2. BE와 AI 서버 로그에서 오류가 없는지 확인합니다.
3. 서로 다른 계정으로 사용자 2명 테스트를 실행합니다.
4. 두 작업 모두 `COMPLETED`가 되고 사용자별 결과가 섞이지 않는지 확인합니다.
5. 여기까지 성공한 후 사용자 5명으로 늘립니다.

테스트 중에는 BE 로그, AI 서버 로그, k6 결과를 서로 다른 터미널에 띄워두는 것이
좋습니다. 작업 관리자 또는 `nvidia-smi -l 2`로 CPU, 메모리, GPU 사용량도 확인하세요.
