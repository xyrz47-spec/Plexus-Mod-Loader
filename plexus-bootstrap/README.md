# plexus-bootstrap — JVM Entrypoint & Execution Harness

`plexus-bootstrap`는 Plexus Mod Loader의 최상위 **JVM 진입점**이자 개발 환경 실행 및 헤드리스 테스트를 총괄하는 부트스트랩 모듈입니다.

---

## 1. 주요 역할 및 책임

1. **JVM 메인 진입점 제공:**
   - 마인크래프트 실행 시 최초로 호출되는 메인 클래스 `dev.plexusmodloader.bootstrap.PlexusLauncherMain`을 소유합니다.
   - 시스템 속성을 읽어 실행 설정(`plexus-runtime-impl`의 `LaunchConfiguration`)을 만듭니다.
2. **클래스로더 계층 초기화:**
   - 마인크래프트 원본 jar와 모드 jar는 `plexus-runtime-impl`의 바이트코드 변환 클래스로더(`GameClassLoader`)가 정의합니다.
   - 실제 모드 로딩 및 의존성 해석은 `plexus-runtime-impl`의 `PlexusRuntime`으로 위임합니다.
3. **개발 환경 실행 작업 (Gradle Tasks):**
   - IDE 및 터미널에서 클라이언트, 전용 서버, 게임 테스트 서버를 즉시 구동할 수 있는 Gradle 작업들을 제공합니다.

---

## 2. 주요 클래스 및 진입점

- **`dev.plexusmodloader.bootstrap.PlexusLauncherMain`**:
  - `main(String[] args)`: JVM 시작점. 시스템 속성을 읽어 `new PlexusRuntime().launch(configuration, args)`를 호출합니다.
- **`dev.plexusmodloader.runtime.LaunchConfiguration`** (`plexus-runtime-impl`):
  - 실행 대상(클라이언트/서버/게임 테스트 서버), 게임 jar, 모드 폴더, 개발 모드 jar, 변환 덤프·캐시 폴더, 보고서 폴더 등을 담는 불변 실행 설정.

### 시스템 속성 (System Properties)

| 속성명 | 값 예시 | 설명 |
| :--- | :--- | :--- |
| `plexus.launchTarget` | `client`, `dedicated_server`, `game_test_server` | 실행할 게임 타겟 환경 |
| `plexus.gameJar` | `/path/to/minecraft.jar` | 마인크래프트 바닐라 클라이언트/서버 jar 경로 |
| `plexus.modsDir` | `/path/to/mods` | 사용자 모드가 위치한 디렉터리 |
| `plexus.devModJars` | 쉼표로 구분된 경로 | 개발 중인 모드 jar 목록 |
| `plexus.transformDumpDir` | `/path/to/dumps` | 바이트코드 변환 후 디컴파일/검사용 class 파일 덤프 디렉터리 |
| `plexus.neoforge` | `on` / `off` | NeoForge 지원 활성화 여부 (기본값: on) |
| `plexus.forge` | `auto` / `always` / `off` | Forge 지원 활성화 여부 (기본값: auto) |

---

## 3. 개발 및 테스트 작업 (Gradle Tasks)

```powershell
# 1. 클라이언트 개발 실행 (Minecraft 26.2 개발 창 실행)
.\gradlew.bat :plexus-bootstrap:runClient

# 2. 전용 서버 개발 실행 (run/server/eula.txt 동의 필요)
.\gradlew.bat :plexus-bootstrap:runServer

# 3. 헤드리스 서버 부팅 스모크 테스트 (CI 및 빠른 검증용)
.\gradlew.bat :plexus-bootstrap:serverBootSmokeTest

# 4. 월드 생성, 엔티티 틱 및 저장 무결성 테스트
.\gradlew.bat :plexus-bootstrap:worldCreateSaveTest

# 5. 게임 내 테스트 실행 (헤드리스)
.\gradlew.bat :plexus-bootstrap:gameTests
```

3~5번은 `examples/`와 `test-mods/`의 모드를 쓰는데, 이 공개 저장소에는 두 폴더가 없어서 메시지와 함께 멈춥니다.

---

## 4. 의존성 구조

- **내부 프로젝트:** `plexus-runtime-impl`, 지원하는 모든 버전의 `plexus-version-adapter-v*`, `plexus-bridge-fabric`, `plexus-bridge-forge` (runtimeOnly)
- **외부 라이브러리:** `slf4j-api` (로그 구현은 Minecraft가 가진 log4j를 씁니다)
