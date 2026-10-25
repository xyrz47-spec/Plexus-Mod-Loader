# 스프린트 1 결과 — Phase 0 + 첫 구현 스프린트

- 기간: 2026-10-19
- 결과 버전: 0.1.0-alpha.1
- 기준: 설계서 §16 Phase 0, §21

## §21 항목별 결과

| # | 항목 | 결과 | 근거 |
|---|---|---|---|
| 1 | Git 저장소 + Gradle Wrapper 멀티모듈 | ✅ | 동기화 폴더 밖 로컬 경로, Wrapper 9.8.1(SHA-256 고정), 모듈 10개 + `build-logic` |
| 2 | IntelliJ에서 동기화·빌드·테스트 | ⚠️ 부분 | CLI에서 모두 검증했습니다. `.run/` 실행 구성 4개를 추가했습니다. **IntelliJ 화면에서의 확인은 사람이 해야 합니다**([체크리스트](../DEVELOPMENT_IN_INTELLIJ.md)) |
| 3 | 대상 버전 선정, Java/Gradle 조합 기록 | ✅ | Minecraft 26.2 / Java 25 / Gradle 9.8.1 — [ADR-0001](../adr/0001-target-minecraft-26-2.md), [ADR-0002](../adr/0002-build-and-dev-environment.md) |
| 4 | 최소 게임 실행 프로필과 로그 위치 | ✅ | `runClient`(`run/client/logs/latest.log`), `runServer`(`run/server/logs/latest.log`) |
| 5 | 런타임 진입점과 버전 어댑터 경계 | ✅ | `PolyLauncherMain` → `PolyRuntime`. `GameVersionAdapter` + `Minecraft26_2Adapter`(`ServiceLoader`) |
| 6 | 최소 Native 메타데이터 읽기·검증 | ✅ | `ModMetadataReader`(오류 코드 11종) |
| 7 | 로그에 모드 ID, 버전, 로딩 단계 표시 | ✅ | `[Poly 모드 로더] Phase …`, `[Poly 모드 로더] Loaded mod minimal_native_mod 0.1.0 …` |
| 8 | 가장 간단한 샘플 모드 실행 | ✅ | `examples/minimal-native-mod`가 클라이언트·전용 서버에서 실행됨 |
| 9 | 시스템 교체 최소 데모 설계 | ✅ 설계만 | [SYSTEM_REPLACEMENT_DEMO.md](../architecture/SYSTEM_REPLACEMENT_DEMO.md) |
| 10 | Fabric/Forge 변환·초기화 최소 실험, 위험 문서화 | ⚠️ 부분 | 분류·차단 실험과 jar 구조 분석, 공식 자료 조사는 완료했습니다. **Fabric/NeoForge 코드를 실제로 Poly 모드 로더 위에서 초기화하는 실험은 하지 않았습니다**([COMPATIBILITY.md](../COMPATIBILITY.md)) |
| 11 | `check`가 테스트와 형식 검사 수행 | ✅ | `check` = 컴파일(`-Werror`) + `spotlessCheck` + 테스트 |
| 12 | 실행 결과·실패·다음 단계 기록 | ✅ | 이 문서 |
| 13 | 내부 접근 최소 PoC (descriptor 검증, 메서드 변환, 시그니처 변경 시 안전한 실패) | ✅ | `poly:client_brand`/`poly:server_brand` 실제 적용. descriptor 불일치는 `POLY-TRANSFORM-002`로 거부(테스트) |
| 14 | API 카탈로그와 안정성 등급 | ✅ | [API_CATALOG.md](../API_CATALOG.md), 코드에 `@ApiStability` |

## Phase 0 완료 기준

| 기준 | 상태 |
|---|---|
| 대상 버전과 Java/Gradle 조합 문서화 | ✅ |
| IntelliJ에서 빌드·테스트·실행 재현 | ⚠️ CLI는 재현됨. IntelliJ는 사람 확인 필요 |
| 첫 실행의 로그와 진단 흐름 확인 | ✅ |
| 시스템 교체 개념을 검증할 최소 시나리오 | ✅ (설계) |
| 혼합 실행의 가장 큰 기술 위험을 실험 결과로 기록 | ⚠️ 정적 분석과 조사 수준. 런타임 실험은 다음 단계 |

## 실행한 검증 (2026-10-19, Windows 11, JDK 25.0.3)

| 명령 | 결과 |
|---|---|
| `.\gradlew.bat check` | BUILD SUCCESSFUL. 테스트 47개 통과, 실패 0, 스킵 0 |
| `.\gradlew.bat :poly-bootstrap:serverBootSmokeTest` | 통과. 예상 로그 8줄 확인, EULA 단계에서 정상 정지 |
| `.\gradlew.bat :poly-bootstrap:runClient` | 사운드 엔진 시작까지 정상 실행, 창 제목 `Minecraft* 26.2`, 변환 2건 적용. 확인 후 수동 종료 |
| `runClient` + Fabric API / NeoForge / Forge jar | 세 jar 모두 `POLY-COMPAT-001 Blocked`로 보고, 게임 정상 실행 |

테스트 내역:
- `ModMetadataReaderTest` 6개
- `VersionRangeTest` 18개
- `ModDependencyResolverTest` 7개
- `PolyRuntimeTest` 4개
- `ClassTransformPlannerTest` 12개

## 작업 중 발견하고 고친 결함

1. **jar 파일 잠금(Windows).** `GameClassLoader`가 `URL.openStream()`으로 클래스를 읽어 JDK 전역 jar 캐시에 파일이 남았습니다. 로더가 `JarFile`을 직접 소유하고 `close()`에서 닫도록 바꿨습니다(ADR-0003).
2. **진단 순서가 실행마다 달라짐.** `ModMetadata`가 `Map.copyOf`로 `depends` 선언 순서를 잃었습니다. 선언 순서를 보존하도록 바꿨습니다.
3. **변환 보고 경쟁 조건.** 병렬 클래스 로딩 중 "마지막 보고 항목"을 읽는 구조를 없앴습니다. 실행기가 변환할 때마다 리스너를 직접 호출합니다.

## 실행하지 않았거나 확인하지 못한 것

- IntelliJ IDEA 화면에서의 가져오기, 실행 구성, 디버거 브레이크포인트
- 전용 서버의 **월드 진입**과 `poly:server_brand`의 실제 적용. EULA 동의는 사용자의 몫이라 하지 않았습니다.
- 클라이언트 **월드 생성·저장·재불러오기**(설계서 Phase 1 완료 기준). 타이틀 화면까지만 확인했습니다.
- 성능 측정. 하지 않았고, 성능 주장도 하지 않습니다.
- macOS·Linux 실행

## 다음 단계 (권장 순서)

1. 사람이 IntelliJ 체크리스트를 확인합니다.
2. Phase 1 마무리:
   - 월드 생성·저장·재불러오기 자동 테스트(클라이언트 quick play 인수 또는 전용 서버 + EULA 동의 후)
   - 정돈된 종료(Shutdown) 단계
3. Phase 2: `poly-system-api` / `poly-system-registry`와 `poly:game_brand` 교체 데모 구현
4. Phase 3: 변환 우선순위 계약, 모드 선언 변환(`transforms`), 변환 캐시
5. Fabric 런타임 실험: Poly 모드 로더 로더 위에 sponge-mixin `IMixinService`를 붙이고, 소형 Fabric 모드 1개를 적용합니다([COMPATIBILITY.md](../COMPATIBILITY.md))
