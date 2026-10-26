# poly-bootstrap — JVM Entrypoint & Execution Harness

`poly-bootstrap`는 Poly Mod Loader의 최상위 **JVM 진입점**이자 개발 환경 실행 및 헤드리스 테스트를 총괄하는 부트스트랩 모듈입니다.

---

## 1. 주요 역할 및 책임

1. **JVM 메인 진입점 제공:**
   - 마인크래프트 실행 시 최초로 호출되는 메인 클래스 `dev.polymodloader.bootstrap.PolyLauncherMain`을 소유합니다.
   - 바닐라 JVM 인수와 시스템 속성을 파싱하여 런타임 환경(`LaunchSpec`)을 구성합니다.
2. **클래스로더 계층 초기화:**
   - 마인크래프트 원본 jar 및 모드 라이브러리를 바이트코드 변환 클래스로더(`PolyTransformingClassLoader`) 상에 배치합니다.
   - 실제 모드 로딩 및 의존성 해석은 `poly-runtime-impl`의 `PolyRuntime`으로 위임합니다.
3. **개발 환경 실행 작업 (Gradle Tasks):**
   - IDE 및 터미널에서 클라이언트, 전용 서버, 게임 테스트 서버를 즉시 구동할 수 있는 Gradle 작업들을 제공합니다.

---

## 2. 주요 클래스 및 진입점

- **`dev.polymodloader.bootstrap.PolyLauncherMain`**:
  - `main(String[] args)`: JVM 시작점. 시스템 속성을 읽어 `PolyRuntime.start(...)`를 호출합니다.
- **`dev.polymodloader.bootstrap.PolyLaunchSpec`**:
  - 마인크래프트 버전, 클라이언트/서버 사이드(Side), 모드 디렉터리 경로 등을 담는 런타임 불변 사양서.

### 시스템 속성 (System Properties)

| 속성명 | 값 예시 | 설명 |
| :--- | :--- | :--- |
| `poly.launchTarget` | `client`, `dedicated_server`, `game_test_server` | 실행할 게임 타겟 환경 |
| `poly.gameJar` | `/path/to/minecraft.jar` | 마인크래프트 바닐라 클라이언트/서버 jar 경로 |
| `poly.modsDir` | `/path/to/mods` | 사용자 모드가 위치한 디렉터리 |
| `poly.devModJars` | 쉼표로 구분된 경로 | 개발 중인 모드 jar 목록 |
| `poly.transformDumpDir` | `/path/to/dumps` | 바이트코드 변환 후 디컴파일/검사용 class 파일 덤프 디렉터리 |
| `poly.neoforge` | `on` / `off` | NeoForge 지원 활성화 여부 (기본값: on) |
| `poly.forge` | `auto` / `always` / `off` | Forge 지원 활성화 여부 (기본값: auto) |

---

## 3. 개발 및 테스트 작업 (Gradle Tasks)

```powershell
# 1. 클라이언트 개발 실행 (Minecraft 26.2 개발 창 실행)
.\gradlew.bat :poly-bootstrap:runClient

# 2. 전용 서버 개발 실행 (run/server/eula.txt 동의 필요)
.\gradlew.bat :poly-bootstrap:runServer

# 3. 헤드리스 서버 부팅 스모크 테스트 (CI 및 빠른 검증용)
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest

# 4. 월드 생성, 엔티티 틱 및 저장 무결성 테스트
.\gradlew.bat :poly-bootstrap:worldCreateSaveTest

# 5. 게임 테스트 하네스 실행 (26.2)
.\gradlew.bat :poly-bootstrap:gameTestServer
```

---

## 4. 의존성 구조

- **내부 프로젝트:** `poly-runtime-impl`, `poly-version-adapter-v26_2` (runtimeOnly)
- **외부 라이브러리:** `slf4j-api`, `log4j-core` (개발용 로깅 환경)
