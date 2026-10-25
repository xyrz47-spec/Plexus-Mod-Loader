# poly-installer

## 이 모듈이 하는 일
플레이어용 설치 프로그램입니다. 공식 Minecraft Launcher의 폴더(`.minecraft`)에 Poly 모드 로더를 설치하고 "Poly 모드 로더 <Minecraft 버전>" 프로필을 추가합니다(ADR-0017). 전용 서버도 폴더 하나에 설치합니다(`ServerInstaller`). 배포 파일은 둘입니다.

| 파일 | 내용 |
|---|---|
| `PolyModLoader-<버전>.jar` | 설치 프로그램. 창(Swing)과 명령줄(`install`, `uninstall`)을 모두 제공합니다. Java 17 이상에서 실행됩니다 |
| `PolyModLoader-<버전>.exe` | Windows용. 위 jar를 품은 작은 네이티브 프로그램입니다. 런처가 받아 둔 Java로 jar를 실행하므로 Java를 따로 설치하지 않아도 됩니다 |

## 무엇을 소유하는가
- `dev.polymodloader.installer`: 설치 로직, 창, 명령줄, 프로필 아이콘
- `native-windows/`: `.exe` 래퍼(Rust, 외부 crate 없음)
- 작업: `installerPayload`, `installerJar`, `installerExe`, `installerExeDistribution`, `installerDistributions`

## 무엇을 하지 않는가
- 게임을 실행하지 않습니다. 실행은 런처가 `dev.polymodloader.bootstrap.PolyLauncherMain`으로 합니다.
- 클라이언트용으로는 Minecraft 파일을 내려받지 않습니다. 바닐라 버전은 런처가 받습니다. 서버용으로는 Mojang 서버 jar를 받습니다(아래).
- EULA에 동의하지 않습니다. 서버의 `eula.txt`도 쓰지 않습니다.
- 서버의 Java를 설치하지 않습니다. 서버는 Java 25 이상이 필요합니다.

## 의존성
- `gson`: 설치 jar 안에 함께 들어갑니다.
- 설치할 내용(`installerRuntime`)은 `:poly-bootstrap`의 실행 클래스 경로입니다. Minecraft가 같은 버전으로 제공하는 gson·slf4j-api는 뺍니다(ADR-0005).

## 주요 진입점
- `InstallerMain`: 인수가 없으면 창을 열고, 있으면 명령줄로 동작합니다.
- `LauncherProfileInstaller.install` / `uninstall`
- `ServerInstaller.install`(창의 "서버", 명령줄 `install-server`)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-installer:check                     # 단위 테스트 포함
.\gradlew.bat :poly-installer:installerDistributions    # build/distributions/PolyModLoader-<버전>.jar (+ Windows에서 .exe, Rust 필요)
.\gradlew.bat :poly-bootstrap:launcherProfileTest       # 새 런처 폴더에 설치한 뒤 런처처럼 실행 (창이 뜸)
.\gradlew.bat :poly-bootstrap:launcherProfileForgeTest  # 같은 설치에 Forge 테스트 모드 (창이 뜸, 26.2)
.\gradlew.bat :poly-installer:serverInstallTest         # 설치 jar로 서버를 설치하고 실행: 모드 없이 EULA 확인까지, Forge 모드로 게임 테스트 서버·전용 서버 (네트워크, 헤드리스)
```
`.exe`를 만들려면 Rust(`cargo`)가 있어야 합니다. `.exe`는 `build`에 포함되지 않습니다.

## 대표 흐름
1. `installerPayload`가 Poly 모드 로더 jar들을 `META-INF/polymodloader/libraries/<Maven 경로>`에 모읍니다. 색인 `META-INF/polymodloader/installer.json`에는 버전, Minecraft 버전, 메인 클래스, Java agent(`agent`: 그 라이브러리와 클래스, ADR-0024), 각 jar의 좌표·경로·SHA-1·크기를 적습니다.
2. 설치할 때 다음 순서로 진행합니다.
   1. 런처 폴더와 프로필 파일을 먼저 검사합니다.
   2. 라이브러리를 복사하고 SHA-1을 확인합니다.
   3. 버전 JSON과 자리표시 jar를 씁니다. JVM 인수에 `-javaagent:${library_directory}/<poly-bootstrap 경로>`를 넣습니다. agent는 Forge 모드가 있을 때만 Forge의 파일 시스템 jar를 클래스 경로에 더합니다(ADR-0024).
   4. 프로필을 추가합니다. 이전 파일은 `.polymodloader-backup`으로 남깁니다.
   5. `mods/`를 만듭니다.
3. 파일은 임시 파일에 쓴 뒤 옮깁니다. 그래서 중간에 멈춰도 반쯤 쓴 JSON이 남지 않습니다.
4. 메모리(`JavaMemory`): 창의 최대·시작 메모리, 또는 `--max-memory`·`--min-memory`를 프로필의 `javaArgs`에 `-Xms`·`-Xmx`로 씁니다. 런처는 이 값을 기본 `-Xmx2G` 대신 씁니다. 원래 있던 다른 JVM 인수는 남기고, "런처 기본값"이면 `javaArgs`를 건드리지 않습니다. 창은 PC 메모리에 맞춰 최대 메모리를 미리 고릅니다(8GB면 4G, 16GB 이상이면 6G).
5. 서버(`ServerInstaller`):
   1. Mojang 버전 목록에서 그 버전의 JSON과 서버 jar를 SHA-1로 확인해 받습니다.
   2. 서버 jar는 번들러입니다. 안의 서버 jar와 라이브러리를 `versions/`·`libraries/`에 꺼내고, 번들러 목록의 SHA-256으로 확인합니다. 번들러 자신과 같은 배치입니다.
   3. Poly 모드 로더 jar를 `libraries/`에 복사합니다.
   4. `polymodloader-server.jar`를 씁니다. 매니페스트만 든 jar이고, `Main-Class`는 `PolyServerMain`, `Class-Path`는 위 jar 전부입니다. `Launcher-Agent-Class`는 Poly 모드 로더의 Java agent입니다(ADR-0024). 그래서 `java -jar polymodloader-server.jar nogui`로 시작합니다. 게임 jar는 `ClassPathGameJarLocator`가 매니페스트 `Class-Path`에서 찾습니다.
   5. 메모리를 넣은 `run.bat`·`run.sh`와 `mods/`를 만듭니다.
6. 언어: 창은 시스템 언어(한국어면 한국어, 그 밖에는 영어)로 열립니다. 오른쪽 위 메뉴에서 English·한국어를 고르면 같은 자리에 그 언어로 창을 다시 만들고, 고른 값(클라이언트·서버, 버전, 폴더, 메모리, 체크 상자, 고급 설정, 기록)을 옮깁니다. 설치 중에는 메뉴를 막습니다. 명령줄과 설치 과정의 기록은 영어입니다.
7. 끝나면(`DesktopNotification`) 알림 영역 아이콘으로 시스템 알림을 띄우고(누르면 `mods/`가 열림) 10초 뒤 종료합니다. "완료되면 닫기"를 껐거나 경고가 있으면 창을 남깁니다. 알림 영역이 없는 데스크톱에서는 전과 같이 대화상자를 띄웁니다.

## 알려진 제한
- 실행 검증(`launcherProfileTest`)은 런처의 동작을 재현한 것입니다. 실제 Minecraft Launcher로 실행한 기록은 아직 없습니다.
- `.exe`에는 코드 서명과 아이콘이 없습니다. 그래서 Windows SmartScreen이 경고할 수 있습니다.
- 런처가 켜진 채 설치하면, 런처가 꺼질 때 프로필 파일을 덮어쓸 수 있습니다. 설치 프로그램은 이 상황을 경고만 합니다.
- 이 버전 이전의 설치 프로그램으로 만든 프로필·서버에는 Java agent가 없습니다. Forge 모드를 쓰려면 다시 설치해야 합니다(`POLY-COMPAT-206`이 알림).
- 서버 설치는 `serverInstallTest`로 검증합니다. 모드 없이 EULA 확인까지, Forge 테스트 모드로 게임 테스트 서버(월드 생성·GameTest)와 전용 서버의 EULA 확인까지입니다. `-PserverInstallMinecraft=<버전>`으로 다른 지원 버전을 설치합니다. Forge 테스트 모드는 최신 버전의 Forge용이므로 그 밖의 버전에서는 모드 없이 Forge만 실행합니다(`-Dpoly.forge=always`). 플레이어가 접속하는 것까지는 확인하지 않았습니다.
- Forge 파일 캐시(`<게임 폴더>/.poly/forge`, 41~51MB)는 `uninstall`이 지우지 않습니다.
