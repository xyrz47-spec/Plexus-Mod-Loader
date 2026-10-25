# poly-transform-engine

## 이 모듈이 하는 일
게임 시작 전에 변환 규칙을 실제 바이트코드와 대조해 계획을 확정하고, 클래스를 로드할 때 그 계획을 적용합니다.

## 무엇을 소유하는가
`dev.polymodloader.internal.transform.engine`(`ClassTransformPlanner`, `TransformPlan`, `ClassTransformExecutor`, `TransformReportEntry`, `TransformCache`, `TransformCacheKey`, `DirectoryTransformCache`)

## 무엇을 하지 않는가
클래스로더를 만들지 않습니다. 어떤 클래스를 변환할지도 결정하지 않습니다(규칙은 어댑터가 제공).

## 의존성
`poly-transform-api`(api), ASM 9.11.1(asm, asm-tree, asm-util)

## 주요 진입점
`new ClassTransformPlanner().plan(rules, gameVersion, side, classBytes)` → `new ClassTransformExecutor(plan, dumpDir, listener).transform(name, bytes)`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-transform-engine:check
```

- `ClassTransformPlannerTest` 13개: 정상 변환, 정적 훅 호출로 교체, descriptor 불일치, 클래스 누락, 사전조건 불일치, 충돌, 중복 ID, 버전 불일치, 다른 측(side), ABORT 정책, 계획 후 바이트 변경, 덤프
- `HookActionsTest` 7개: 시작·반환 훅을 javac 컴파일 클래스에 적용해 실행
- `CallSiteAndAccessTest` 11개: call-site 변경, 같은 메서드 규칙의 순서·거부, 접근 확대, 캐시 키
- `TransformCacheTest` 10개: 적중, 무효화, 손상 감지, 크기 상한

## 대표 흐름
계획 단계에서는 ID 중복 → 버전 → 같은 메서드 규칙의 순서·결합 가능성 → 클래스 존재 → name+descriptor → 사전조건 순으로 검사하고 통과한 규칙만 승인합니다. 접근 확대는 버전과 대상 존재를 검사합니다. 클래스를 로드할 때는 사전조건을 원본 메서드에서 다시 확인하고, 메서드마다 호출 바꾸기 → 반환 훅 → 시작 훅(역순) 순으로 적용한 뒤 접근 확대를 적용합니다. 그다음 `CheckClassAdapter`로 검증하고 전후 SHA-256을 보고합니다.

## 알려진 제한
프레임을 다시 계산하지 않으므로, 스택 모양을 바꾸는 동작을 추가할 때는 ADR-0004의 결정을 다시 검토해야 합니다. 캐시는 크기 상한(기본 256 MiB)을 넘으면 가장 오래 쓰지 않은 항목부터 지웁니다(ADR-0013).
