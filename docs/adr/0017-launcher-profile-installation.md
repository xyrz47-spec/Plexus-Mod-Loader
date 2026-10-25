# ADR-0017: Minecraft Launcher 프로필 설치와 클래스 경로의 게임 jar

- 상태: 채택 (2026-10-21)
- 관련: ADR-0003(게임 클래스로더), ADR-0005(게임과 함께 쓰는 라이브러리), ADR-0011(여러 Minecraft 버전)

## 배경
지금까지 Poly 모드 로더는 Gradle 작업으로만 실행할 수 있었습니다. 이 작업은 게임 jar를 `-Dpoly.gameJar`로 넘기고 클래스 경로에는 넣지 않습니다. 플레이어가 쓰는 공식 Minecraft Launcher는 다르게 동작합니다.
- 다른 버전을 상속(`inheritsFrom`)하는 버전 JSON을 읽습니다. 라이브러리와 인수는 바닐라 것에 이어 붙입니다.
- 바닐라 게임 jar를 **클래스 경로에 넣습니다**. 그 위치를 따로 알려 주지는 않습니다.
- 버전 JSON의 `mainClass`를 실행합니다. 작업 폴더는 게임 폴더입니다.

## 결정
1. 설치 프로그램 `poly-installer`가 런처 폴더에 다음을 씁니다.
   - `libraries/`: Poly 모드 로더 jar. 경로는 Maven 배치이고, SHA-1을 확인합니다.
   - `versions/polymodloader-<poly>-<mc>/<id>.json`: `inheritsFrom: <mc>`, `mainClass: dev.polymodloader.bootstrap.PolyLauncherMain`, JVM 인수 `-Dpoly.launchTarget=client`, 라이브러리 목록(`url: ""` = 설치 프로그램이 넣은 파일).
   - 빈 자리표시 jar.
   - `launcher_profiles.json`(Microsoft Store 런처면 `launcher_profiles_microsoft_store.json`도)의 프로필. 다른 내용은 그대로 둡니다.
2. 라이브러리 목록에서 Minecraft가 같은 버전으로 제공하는 gson·slf4j-api(ADR-0005)와 gson의 컴파일 전용 주석 jar는 뺍니다. 같은 라이브러리를 클래스 경로에 두 번 두지 않기 위해서입니다.
3. `PolyLauncherMain`은 `poly.gameJar`가 없으면 클래스 경로에서 루트에 `version.json`이 있는 jar 하나를 게임 jar로 씁니다. 없거나 둘 이상이면 `POLY-BOOTSTRAP-006`으로 멈춥니다. 클래스 이름은 보지 않습니다. 버전 어댑터가 읽는 것과 같은 파일만 봅니다.
4. 게임 jar가 시스템 클래스로더에도 보이지만 그대로 둡니다. `GameClassLoader`는 게임 jar에 있는 클래스를 자식 우선으로 직접 정의합니다(ADR-0003). 따라서 게임 클래스는 변환된 한 벌만 쓰입니다. 리소스는 부모 우선으로 찾지만 두 곳의 내용이 같습니다.
5. Windows용 `.exe`는 설치 jar를 품은 작은 네이티브 프로그램입니다(Rust, 외부 crate 없음). 플레이어가 Java를 따로 설치하지 않도록, 런처가 내려받은 Java 런타임을 먼저 찾아 jar를 실행합니다. 찾는 순서는 `JAVA_HOME`, 런처 런타임(최신 먼저), `PATH`입니다.
6. 전용 서버 설치는 이번 범위가 아닙니다.

## 근거(실험, 2026-10-21)
- `LauncherProfileInstallerTest` 9개: 설치, 재설치, Microsoft Store 파일, 손상된 라이브러리 교체, 체크섬이 틀린 설치 파일 거부, 잘못된 버전·폴더·프로필 파일 거부(아무것도 쓰지 않음), 제거, 명령줄.
- `ClassPathGameJarLocatorTest` 3개.
- `:poly-bootstrap:launcherProfileTest`
  - 설치 jar로 새 런처 폴더에 설치합니다.
  - 런처와 같은 방식으로 프로필을 실행합니다. 상속 병합, 라이브러리 규칙, 런처 변수, 바닐라 log4j 설정, 게임 폴더에서 시작을 따릅니다.
  - `mods/`의 Poly 모드 2개와 Fabric 모드 1개가 로드됐습니다. 접속 확인을 통과하고 월드에 들어갔습니다. 라이브러리 중복과 SHA-1 불일치는 없었습니다.
- `.jar`와 `.exe`로 임시 폴더에 설치해, 기존 프로필이 남는 것을 확인했습니다.

## 결과
- 플레이어가 Gradle 없이 Poly 모드 로더를 설치하고 실행할 수 있습니다.
- 위 실행 검증은 런처의 동작을 재현한 것입니다. 실제 Minecraft Launcher(독립 실행형·Microsoft Store)로 실행한 결과는 아직 기록이 없습니다. 런처가 `url: ""` 라이브러리를 받지 않고 SHA-1만 확인한다는 점은 Forge 설치 프로그램과 같은 방식에 기댄 것입니다.
- `.exe`에는 코드 서명이 없어 SmartScreen 경고가 뜰 수 있습니다.
