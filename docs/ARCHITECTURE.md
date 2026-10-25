# Poly 모드 로더 아키텍처 (0.1.0-alpha.10 현재)

이 문서는 **현재 저장소에 실제로 있는 것**을 설명합니다. 목표 구조는 설계서 §7.7, §8에 있고, 모듈은 실제 책임 경계가 생길 때만 추가합니다.

## 모듈과 의존 방향

```text
                    공개 API (모드가 의존)
        ┌──────────────────────────────────────────────────────────┐
        │ poly-runtime-api ◄── poly-system-api ◄── poly-lifecycle-api │
        └───────▲─────────────────────▲────────────────────────────┘
                │                     │
  poly-transform-api ◄── poly-version-adapter-api
        ▲                     ▲
        │                     │
  poly-transform-engine   poly-version-adapter-v26_2   (Minecraft 이름은 여기에만)
        ▲                     ▲ (runtimeOnly, ServiceLoader)
        │                     │
  poly-mod-metadata ◄── poly-system-registry ◄── poly-runtime-impl ──┐
                              ▲                       │
                              │                       │
                        poly-bootstrap  ── polyDevMod ──► examples:minimal-native-mod
                                                       (compileOnly poly-lifecycle-api)
```

규칙(설계서 §8.1)과 이를 지키게 하는 방법:

| 규칙 | 강제 방법 |
|---|---|
| API 모듈은 구현체에 의존하지 않음 | `poly-runtime-api`, `poly-lifecycle-api`는 다른 Poly 모드 로더 모듈을 `api`로만 참조 |
| Minecraft 내부 이름은 `game.version.*` 밖으로 새지 않음 | Minecraft jar가 **어떤 모듈의 컴파일 클래스 경로에도 없음**. 클래스·메서드 이름은 어댑터의 문자열 상수로만 존재 |
| 모드는 엔진 구현에 의존하지 않음 | 예제 모드는 `compileOnly(poly-lifecycle-api)`만 사용 |
| 순환 의존 없음 | Gradle이 프로젝트 순환을 거부 |

## Native API와 게임 계층 (ADR-0010)

```text
모드 ──(compileOnly)──► poly-lifecycle-api ──► poly-game-api (Minecraft 타입 없음)
                                   │ 선언
                                   ▼
            poly-runtime-impl: NativeRegistrations (검증·저장, 버전 독립)
                                   │ GameLayerContext
                                   ▼
            poly-game-v26_2 (Minecraft jar에 compileOnly 컴파일, 게임 클래스로더가 정의)
                                   ▲ 훅 호출
            poly-version-adapter-v26_2: InvokeHookAtHead / InvokeHookBeforeReturn 규칙 14개
```

- 위 규칙표의 "Minecraft jar가 어떤 모듈의 컴파일 클래스 경로에도 없음"은 이제 **`poly-game-v26_2`를 제외하고** 지켜집니다.
- 게임 계층은 Poly 모드 로더 패키지 가운데 유일하게 게임 클래스로더가 정의합니다.

## 여러 Minecraft 버전 (ADR-0011)

위 그림의 `-v26_2` 두 모듈은 지원 버전마다 하나씩 있습니다. 나머지 모듈은 모든 버전이 함께 씁니다.

```text
gradle/minecraft-versions.properties   supported=26.2, default=26.2, java.26.2=25
        │ settings.gradle.kts가 버전마다 포함
        ▼
poly-version-adapter-v<id>  +  poly-game-v<id>      (<id> = 26_2, 이름 규칙으로 찾음)
        │ poly-bootstrap이 모든 어댑터를 담음
        ▼
PolyRuntime: 게임 jar의 version.json → gameVersions()가 그 버전을 포함하는 어댑터
```

- 실행·검증 작업은 한 번에 한 버전입니다. 기본 버전을 쓰고, `-PpolyMinecraft=<버전>`으로 바꿉니다.
- 지원 버전과 모듈은 `./gradlew minecraftVersions`로 봅니다.
- 새 버전은 `./gradlew newMinecraftVersion -Pfrom=26.2 -Pto=<버전>`으로 가장 가까운 버전을 복사해 시작합니다.
- 모드의 `"minecraft"` 목록은 `26.2`, `26.*`, `>=26.2 <27` 같은 형식을 받습니다. Native API만 쓰는 모드는 다시 빌드하지 않고 새 버전에서 돕니다.

## 실행 흐름 (`PolyRuntime.launch`)

설계서 §11의 단계 가운데 구현된 부분입니다. 단계마다 `[Poly 모드 로더] Phase …` 로그를 남깁니다.

1. **BOOTSTRAP**
   - Poly 모드 로더 버전과 Java 버전을 기록합니다.
   - 게임 jar의 `version.json`에서 게임 버전을 읽습니다.
   - `ServiceLoader`로 그 버전의 `GameVersionAdapter`를 고릅니다. 없으면 `POLY-BOOTSTRAP-002`.
2. **DISCOVERY**
   - `mods/` 폴더의 jar와 개발용 모드 jar를 찾습니다.
   - 메타데이터 파일로 Native / Fabric / NeoForge / Forge / Unknown을 분류합니다.
   - Native가 아닌 모드는 설치된 로더 브리지(`ModLoaderBridge`, ServiceLoader)가 평가합니다. `BLOCKED`면 기능별 `POLY-COMPAT-1xx/2xx`로 보고하고 로드하지 않습니다. 브리지는 판정만 하고, 의존성은 다음 단계에서 판정합니다. 브리지가 없으면 `POLY-COMPAT-001`입니다. 깨진 jar는 `POLY-DISCOVERY-003`, FML 라이브러리 jar는 `POLY-COMPAT-205`입니다.
3. **METADATA_VALIDATION** — `poly.mod.json`의 모든 문제를 한 번에 모읍니다(`POLY-METADATA-*`).
4. **DEPENDENCY_RESOLUTION**
   - 먼저 외부 모드를 정합니다(`CrossLoaderModResolver`, ADR-0014). 필요한 모드(Native·외부·`minecraft`·`java`·브리지 내장 ID)가 있고 버전이 맞아야 하며, `breaks` 대상이 없고 ID가 겹치지 않아야 합니다. 그다음 바이트코드 링크 검사(`ModLinkVerifier`)를 통과해야 합니다. Fabric class tweaker의 접근 확대 대상도 여기서 게임 jar와 대조합니다(`POLY-COMPAT-128`, ADR-0018). 막히면 의존하는 모드도 연쇄로 막힙니다(`POLY-COMPAT-111..119`).
   - 그다음 Poly 모드를 검사합니다: ID 중복, 예약 ID, 누락 의존성(외부 모드 포함, 차단된 외부 모드는 `POLY-DEPENDENCY-009`), 버전 불일치, 순환, 충돌, 실행 환경, 게임 버전(`POLY-DEPENDENCY-*`).
   - 엔트리포인트는 로더를 가리지 않고 의존 순서대로 부릅니다(`CrossLoaderLoadOrder`).
   - 로드 순서는 의존성 우선으로 정하고, 동률이면 ID 순으로 정합니다.
5. **CONFLICT_RESOLUTION** — 메타데이터의 `replacesSystems` 선언으로 시스템마다 활성 제공자를 정합니다(`POLY-SYSTEM-001/002/004/005`). 모드 코드는 아직 실행되지 않습니다.
6. **TRANSFORMATION_PLANNING** — 어댑터의 내장 규칙과 모드의 `transforms` 규칙·`accessWidenings`, 로드되는 Fabric 모드의 class tweaker(`ForeignAccessWidenings`)를 게임 jar 바이트와 대조해 계획을 확정합니다(`POLY-TRANSFORM-*`). 클래스 로드 시 적용 결과는 변환 캐시(`.poly/transform-cache`)에 저장되어 다음 실행에서 재사용됩니다.
7. 게임 jar와 모드 jar로 `GameClassLoader`를 만듭니다. 컨텍스트 클래스로더도 이것으로 설정합니다.
8. **SYSTEM_REGISTRATION** — 활성 제공자를 생성·검증하고(`POLY-SYSTEM-003`), 버전 어댑터에 `SystemLookup`을 연결하며, `.poly/startup-report.json`을 씁니다.
9. **LIFECYCLE_INITIALIZATION**
   - JVM 종료 훅으로 `ShutdownCoordinator`를 등록합니다.
   - 모든 모드의 common 엔트리포인트를 호출한 뒤, 측별(client/server) 엔트리포인트를 호출합니다(`POLY-ENTRYPOINT-*`).
10. **GAME_LAUNCH** — 실행 대상(`GameLaunchTarget`: `client`, `dedicated_server`, `game_test_server`)에 맞춰 어댑터가 지정한 게임 `main`을 `MethodHandle`로 호출합니다.
11. **SHUTDOWN** — JVM이 종료될 때 모드가 `ModContext.onShutdown`으로 등록한 작업을 등록 역순으로 한 번씩 실행합니다. 한 작업이 실패해도 나머지는 실행합니다(`POLY-SHUTDOWN-001`).

오류 수준 문제가 하나라도 있으면 그 단계에서 `PolyLaunchException`으로 멈춥니다. 이후 단계(엔트리포인트, 게임 실행)는 진행하지 않습니다.

아직 없는 단계: Game Validation, Runtime Monitoring.

## 클래스로더 구성

```text
Bootstrap/Platform
  └─ App class loader:   Poly 모드 로더 모듈 + Minecraft 라이브러리 (LWJGL, log4j, gson, netty, …)
       └─ poly-game (GameClassLoader):  게임 jar + 모드 jar   ← 여기서만 변환
```

자세한 이유는 [ADR-0003](adr/0003-game-class-loader.md)을 보세요.

JPMS 모듈이 필요한 로더(Forge)를 위해 `GameClassLoader.define`(브리지 API `GameModuleLayers`)이 모듈 레이어를 정의할 수 있습니다([ADR-0021](adr/0021-forge-on-polyloader.md) G3a). 그 모듈들의 패키지 클래스도 같은 클래스로더가 같은 변환을 거쳐 정의하므로, 이름 있는 모듈에 들어가도 게임 클래스의 소유자는 하나입니다. 아직 실행 경로에서는 쓰지 않습니다.

## 오류 코드 체계

| 접두사 | 담당 | 정의 위치 |
|---|---|---|
| `POLY-BOOTSTRAP-001..003` | 게임 jar, 어댑터, 실행 설정 | `PolyRuntime`, `PolyLauncherMain` |
| `POLY-BOOTSTRAP-006` | 클래스 경로에서 게임 jar를 찾지 못함(런처 프로필 실행) | `ClassPathGameJarLocator` |
| `POLY-BOOTSTRAP-007` | 게임의 main이 오류로 끝남(오류를 기록하고 상태 1로 종료) | `PolyLauncherMain` |
| `POLY-DISCOVERY-002` | 메타데이터 없는 jar | `PolyRuntime` |
| `POLY-COMPAT-001` | Native가 아닌 모드 차단 | `PolyRuntime` |
| `POLY-COMPAT-100..108` | Fabric 모드의 기능별 판정. `102`(class tweaker 차단)와 `103`(중첩 jar 차단)은 폐기 | `FabricBridge` |
| `POLY-COMPAT-111..113`, `118`, `119`, `128`, `130` | 시작 전 링크 검사. `128`: 이 게임에 없는 접근 확대 대상(차단), `130`: 전용 서버에서 적용하지 않는 클라이언트 클래스 접근 확대(경고) | `ModLinkVerifier` |
| `POLY-COMPAT-125`, `126`, `129` | Fabric class tweaker. `125`: 파일 없음·읽기 실패·형식 오류(줄 번호), `126`: `official`이 아닌 이름 공간, `129`: 적용할 접근 확대·인터페이스 주입·enum 상수 요약(정보). `127`은 폐기 | `FabricBridge`, `ClassTweakerFile` |
| `POLY-METADATA-001..015` | `poly.mod.json` | `MetadataProblemCode` |
| `POLY-SYSTEM-001..006` | 시스템 교체 충돌, 알 수 없는 시스템, 제공자 실패, 교체 불가, 잘못된 override, 바인딩 전 훅 호출 | `SystemProblemCode`, `Minecraft26_2Hooks` |
| `POLY-DEPENDENCY-001..009` | 모드 사이 관계 | `DependencyProblemCode` |
| `POLY-TRANSFORM-001..010` | 변환 규칙·접근 확대, 캐시 손상·쓰기 실패 | `TransformProblemCode`, `DirectoryTransformCache` |
| `POLY-ENTRYPOINT-001..003` | 엔트리포인트 로드·형식·실행 | `EntrypointInvoker` |
| `POLY-SHUTDOWN-001..002` | 종료 작업 실패, 종료 후 등록 | `ShutdownCoordinator` |
| `POLY-EVENT-001..003` | 리스너 예외(비활성화), 서버 접근자 불일치, 결정 이벤트 리스너가 `null`을 돌려줌(비활성화) | `EventBus`, `MinecraftServerView` |
