# poly-transform-engine — High-Performance ASM Bytecode Transformation Engine

`poly-transform-engine`는 `poly-transform-api`에 정의된 선언적 변환 규칙을 바탕으로, 실제 마인크래프트 클래스 바이트코드를 파싱·검증·변환하고 디스크에 캐싱하는 고성능 ASM 기반 엔진입니다.

---

## 1. 2단계 변환 아키텍처 (Two-Phase Transformation)

```
[Transform Rules] ---> [ Phase 1: Planning (ClassTransformPlanner) ]
                             │  - ID 중복 및 순서 의존성 검증
                             │  - 타겟 클래스 / 메서드 디스크립터 검증
                             │  - 사전조건(Precondition) 대조
                             ▼
                        [ TransformPlan ]
                             │
                             ▼
[Class Bytes]    ---> [ Phase 2: Execution (ClassTransformExecutor) ]
                             │  - Call-site 리다이렉트
                             │  - 반환 훅 & 시작 훅 주입
                             │  - Access Widener (public/final 해제)
                             │  - CheckClassAdapter 무결성 검증
                             ▼
                      [ Transformed Class Bytes ] ---> [ DirectoryTransformCache ]
```

### 1) 사전 계획 단계 (`ClassTransformPlanner`)
- 게임 시작 전, 등록된 모든 변환 규칙을 실제 원본 클래스 바이트코드와 대조합니다.
- 중복 ID 검사 → 지원 버전 및 사이드 검사 → 타겟 메서드 존재 여부 → 결합 가능성을 사전 확인하여 확정된 변환 계획(`TransformPlan`)을 수립합니다.

### 2) 실행 및 검증 단계 (`ClassTransformExecutor`)
- 클래스가 클래스로더에 의해 실제로 로드되는 순간, 사전 수립된 `TransformPlan`에 따라 ASM 9.11 트리를 조작합니다.
- 조작 직후 ASM의 `CheckClassAdapter`를 통과시켜 유효하지 않은 바이트코드가 생성되는 것을 원천 차단합니다.
- 디버깅을 위해 `-Dpoly.transformDumpDir` 설정 시 변환 전/후 `.class` 파일을 디스크에 자동 덤프합니다.

### 3) 디스크 캐싱 (`DirectoryTransformCache`)
- 엔진 지문, 클래스 이름, 원본 클래스 바이트, 적용할 규칙과 접근 확장을 SHA-256으로 묶어 클래스마다 캐시 키(`TransformCacheKey`)를 만듭니다.
- 이미 변환된 클래스는 캐시 디렉터리(기본 `.poly/transform-cache`, `-Dpoly.transformCacheDir`로 변경, `none`이면 끔)에서 즉시 읽어 들여 2회차 부팅 속도를 높입니다. 기본 한도는 256 MiB이고, 넘으면 가장 오래 쓰지 않은 항목부터 지웁니다.

---

## 2. 주요 클래스

| 클래스 | 역할 |
| :--- | :--- |
| `dev.plexusmodloader.internal.transform.engine.ClassTransformPlanner` | 변환 규칙 검증 및 통합 실행 계획(`TransformPlan`) 생성 |
| `dev.plexusmodloader.internal.transform.engine.ClassTransformExecutor` | ASM 9.11 기반 실제 클래스 바이트코드 조작 및 검증 |
| `dev.plexusmodloader.internal.transform.engine.DirectoryTransformCache` | 변환된 바이트코드 디스크 캐시 및 LRU 만료 처리 |

---

## 3. 검증 및 테스트 (Gradle Tasks)

```powershell
.\gradlew.bat :poly-transform-engine:check
```

- **`ClassTransformPlannerTest`**: 정상 변환, 정적 훅 교체, 디스크립터 불일치, ABORT 정책, 중복 ID, 버전 필터링 등 13종 테스트.
- **`HookActionsTest`**: 실제 javac로 컴파일된 클래스에 시작/반환 훅을 주입하고 직접 호출하여 스택 무결성 검증.
- **`CallSiteAndAccessTest`**: 접근 제어자 확장(`AccessWidening`) 및 Call-site 리다이렉트 순서 검증.
- **`TransformCacheTest`**: 캐시 적중, 무효화, 데이터 손상 감지 및 최대 용량 제한(256 MiB) 처리 검증.
