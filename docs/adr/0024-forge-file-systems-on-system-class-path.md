# ADR-0024: 설치한 Poly 모드 로더에서 Forge 파일 시스템을 JVM 클래스 경로에 두기 — Java agent

- 상태: 채택 (2026-10-23)
- 관련: ADR-0003(게임 클래스로더), ADR-0017(런처 프로필 설치), ADR-0021(Forge를 Forge 자신의 코드로 실행)
- 계획: [`docs/plans/FORGE_SUPPORT.md`](../plans/FORGE_SUPPORT.md) G9

## 배경

- FML은 모드 파일을 Forge의 파일 시스템으로 읽습니다. `securemodules`가 `union`, `JarJarFileSystems`가 `jij`·`path`, `roimfs`가 `roimfs`를 제공하고, `unsafe`는 securemodules가 씁니다(각 jar의 `META-INF/services`와 `getScheme()`을 `javap`로 확인, Forge 26.2-65.1.5와 26.1-62.0.9).
- Java는 파일 시스템(`FileSystemProvider`)과 URL 처리기(`URLStreamHandlerProvider`)를 **시스템 클래스로더에서만** 찾습니다. 게임 클래스로더나 모듈 레이어에 넣어서는 보이지 않습니다. Forge 자신의 실행기도 이 넷을 클래스 경로에 둡니다(G3).
- 개발 실행(Gradle 작업)은 `forgeClassPath`로 이 넷을 처음부터 클래스 경로에 넣습니다. 그러나 플레이어가 설치한 Poly 모드 로더(런처 프로필, `install-server`의 전용 서버)의 클래스 경로에는 없어서, Forge 모드가 `POLY-COMPAT-206`으로 로드되지 않았습니다.
- Forge 파일은 Forge 모드가 있을 때만 `ForgeInstallation`이 받습니다(`.poly/forge/<버전>/libraries/`, SHA-1 확인). Forge 모드가 없는 플레이어에게는 아무것도 받지 않고 클래스 경로도 바꾸지 않아야 합니다.
- **JDK의 함정.** `FileSystemProvider.installedProviders()`는 처음 불릴 때 목록을 만들고 그 뒤로는 그대로 씁니다. 그 뒤에 클래스 경로에 jar를 더해도 클래스는 보이지만 파일 시스템은 늘지 않습니다(`SystemClassPathAgentTest.javaListsItsFileSystemsOnlyOnce`). 이전 검사(`ForgeGameHost.create`)는 바로 이 메서드를 Forge 파일을 준비하기 전에 불렀습니다.

## 결정

1. **Poly 모드 로더의 Java agent.** `poly-bootstrap`에 `SystemClassPathAgent`를 둡니다. `premain`·`agentmain`은 JVM이 준 `Instrumentation`만 기억합니다. 클래스 변환기를 등록하지 않으며 그 밖의 일을 하지 않습니다. `poly-bootstrap` jar의 매니페스트에 `Premain-Class`를 둡니다.
2. **JVM이 agent를 시작하는 방법.**
   - 런처 프로필: 버전 JSON의 JVM 인수에 `-javaagent:${library_directory}/<poly-bootstrap 라이브러리의 Maven 경로>`를 넣습니다. 런처가 `${library_directory}`를 바꿉니다(Forge·NeoForge 프로필도 이 변수를 씁니다). 라이브러리 목록은 바뀌지 않습니다.
   - 전용 서버: `polymodloader-server.jar` 매니페스트에 `Main-Class` 옆으로 `Launcher-Agent-Class`를 둡니다. `java -jar polymodloader-server.jar`가 플래그 없이 agent를 시작하고, agent 클래스는 그 jar의 `Class-Path`(poly-bootstrap)에서 옵니다. `Class-Path`는 바뀌지 않습니다.
   - 설치 jar의 색인(`installer.json`)에 선택 항목 `agent`(`library`, `class`)를 둡니다. 이 항목이 없는 예전 색인도 읽습니다(agent 없이 설치). 빌드(`InstallerPayloadTask`)는 그 라이브러리가 설치 내용에 없거나, 클래스가 없거나, 매니페스트의 `Premain-Class`가 다르면 실패합니다.
3. **내부 SPI `SystemClassPath`**(`poly-bridge-api`, `@ApiStability(INTERNAL)`, `append(Path)`)를 `LaunchConfiguration` → `GameHost.Request`로 넘깁니다. agent 없이 시작했으면 비어 있습니다. 기존 생성자는 `Optional.empty()`로 그대로 동작합니다.
4. **Forge 모드가 있을 때만**(`ForgeFileSystems`, `poly-bridge-forge`):
   1. 아무것도 받기 전에, 시스템 클래스로더가 `cpw/mods/niofs/union/UnionFileSystemProvider.class`를 보거나(개발 실행) `SystemClassPath`가 있는지 확인합니다. 둘 다 없으면 `206`으로 멈추고, 프로필을 이 버전의 설치 프로그램으로 다시 설치하거나 서버를 `java -jar polymodloader-server.jar`로 시작하라고 알립니다. 이 단계에서는 `installedProviders()`를 부르지 않습니다.
   2. `ForgeInstallation.prepare`가 받은 뒤, 넷을 `Instrumentation.appendToSystemClassLoaderSearch`로 시스템 클래스로더에 더합니다. 그 뒤의 `ForgeBootModules.sort`는 클래스 경로에 있는 라이브러리를 BOOT 모듈로 만들지 않고 나누어 씁니다(개발 실행과 같은 상태).
   3. 더한 뒤에야 `installedProviders()`로 `union`, `jij`, `path`, `roimfs`를 확인합니다. 없으면 `206`이며 이유는 "JVM이 Forge jar를 더하기 전에 파일 시스템 목록을 만들었다"입니다. 더했는데 클래스가 보이지 않으면 그 사실을 이유로 씁니다.
5. `POLY-COMPAT-206`의 뜻(Forge를 준비하지 못해 Forge 모드를 로드하지 않음)은 그대로이고, 이유만 구체적으로 바뀝니다.

## 고르지 않은 것

- **Forge의 네 jar를 런처 라이브러리로 모든 플레이어에게:** Forge 모드가 없는 플레이어도 받고 클래스 경로에 둡니다. 비용이 0이어야 한다는 조건에 어긋납니다. 버전도 Forge 릴리스마다 달라질 수 있습니다.
- **두 번째 JVM(Forge가 있으면 클래스 경로를 바꿔 다시 시작):** 런처가 넘긴 인수·작업 폴더·창·로그를 다시 맞춰야 하고, 서버 호스트의 프로세스 관리와도 어긋납니다.
- **JDK 내부(`jdk.internal.loader`, 리플렉션으로 `installedProviders` 캐시 비우기):** `--add-opens`가 필요하고 JDK마다 깨질 수 있습니다.
- **자체 시스템 클래스로더(`-Djava.system.class.loader`):** 모든 플레이어의 클래스 로딩을 바꿉니다. 게임 jar를 클래스 경로에 두는 ADR-0017의 배치와도 얽힙니다.
- **매니페스트 `Class-Path`가 캐시 폴더를 가리키게:** 아직 받지 않은 jar를 가리키고, 버전이 바뀌면 설치를 다시 써야 합니다. 런처 프로필에는 쓸 수 없습니다.

## 근거(실험, 2026-10-23)

- `ForgeFileSystemsTest` 7개: Forge `version.json`에서 네 jar 고르기(없으면 `IOException`), agent 없이 거부(받기 전), 더한 뒤 파일 시스템 확인, "목록을 먼저 만듦" 보고, 효과 없는 추가 보고, 이미 클래스 경로에 있으면 그대로 둠.
- `SystemClassPathAgentTest` 4개(`:poly-bootstrap:test`, 자식 JVM): `-javaagent:<빌드한 poly-bootstrap jar>`와, `Launcher-Agent-Class`만 둔 매니페스트 jar(`java -jar`, agent는 `Class-Path`에서)에서 네 jar를 더한 뒤 `installedProviders()`에 `union`·`jij`·`path`·`roimfs`가 있습니다. agent 없이는 더할 수 없고, 목록을 먼저 만들면 클래스는 보여도 파일 시스템은 없습니다.
- `LauncherProfileInstallerTest`, `ServerInstallerTest`: `-javaagent:${library_directory}/…` 인수, `net.minecraftforge` 라이브러리 없음, `Launcher-Agent-Class`와 바뀌지 않은 `Class-Path`, 색인의 `agent` 읽기.
- `:poly-installer:serverInstallTest`(설치 jar로 설치한 전용 서버, 26.2):
  1. 모드 없이: EULA 확인까지. `.poly/forge`가 없고 로그에 Forge가 없습니다.
  2. `mods/`에 `forge-hello`를 넣고 `java -Dpoly.launchTarget=game_test_server -jar polymodloader-server.jar …`: agent가 네 jar를 더하고, FML이 `forge_hello`를 만들고, 공통 설정 이벤트, GameTest 통과. `206` 없음.
  3. 같은 모드로 `java -jar polymodloader-server.jar nogui`: Forge의 실행 핸들러가 게임을 시작해 EULA 확인까지.

## 결과

- 설치한 Poly 모드 로더(런처 프로필, 전용 서버)에서 Forge 모드가 개발 실행과 같은 상태로 실행됩니다. Forge 모드가 없으면 받는 것도, 클래스 경로의 변화도 없습니다. agent는 `Instrumentation`을 기억하는 일만 합니다.
- 이 버전 이전의 설치 프로그램으로 만든 프로필과 서버에는 agent가 없습니다. Forge 모드를 넣으면 `206`이 다시 설치하라고 알립니다.
- `installedProviders()`를 Forge 설정 전에 부르는 코드가 생기면(Poly 모드 로더 자신이든 JVM 시작 단계의 다른 agent든) Forge가 멈춥니다. 그 경우 `206`의 이유가 그 사실을 말합니다.
- 런처 프로필 쪽은 런처를 재현한 `launcherProfileForgeTest`(창이 뜸)로 확인합니다. 실제 Minecraft Launcher로 실행한 기록은 ADR-0017과 마찬가지로 아직 없습니다.
