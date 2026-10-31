# plexus-early-window — Plexus 모드 로더의 초기 로딩 창

게임이 시작되는 동안 게임 창이 열리기 전까지 초기 로딩 창을 표시하는 모듈입니다. 게임 프로세스와 완전히 분리된 전용 Java(Swing) 프로세스로 실행되어 게임 런타임의 그래픽 컨텍스트나 헤드리스 설정에 영향을 주지 않고 로딩 화면을 보여 줍니다.

## 이 모듈이 하는 일
- 클라이언트 부팅 시 별도의 작은 Java(Swing) 프로세스를 실행하여 게임 창이 첫 로딩 프레임을 그리기 전까지 초기 로딩 화면을 표시합니다.
- 모든 로더(Fabric, Quilt, Forge, NeoForge, Plexus 네이티브 모드)의 단일 및 혼합 실행 환경에서 공통으로 적용됩니다.
- 게임 프로세스와 창 라이브러리(Minecraft 26.1·26.2의 GLFW, 26.3의 SDL)를 공유하지 않습니다.
- 마인크래프트의 `Main` 진입점이 설정하는 `java.awt.headless=true` 설정의 영향을 받지 않고 독립적으로 동작합니다.
- 첫 프레임 이전의 바닐라 로딩 화면과 완전히 동일한 외형을 렌더링합니다:
  - Mojang 빨간색 배경(239, 50, 61) 또는 단색 로고 옵션 활성화 시 검은색 배경.
  - 클라이언트 jar의 `assets/minecraft/textures/gui/title/mojangstudios.png`에서 직접 읽은 Mojang Studios 로고.
  - 바닐라 로딩 화면과 동일한 위치의 진행 막대.
- 게임 계층의 `PlexusLoadingOverlay`가 인게임 화면에 그리는 것과 동일한 Plexus 표식을 표시합니다:
  - 오른쪽 아래: 회전하는 금색 큐브(`plexus_cube.png`, 32 x 32 크기 24프레임)와 `Plexus Mod Loader` 명칭.
  - 진행 막대 아래: 각 로더별로 로드된 모드 수 집계(예: `Mods: Fabric 1 · Plexus 4`).
  - 왼쪽 아래: Plexus 모드 로더가 수행 중인 현재 작업 내용(예: `Found 5 mod files`, `Starting NeoForge`, `Starting Minecraft 26.2`).
- 창 아이콘으로 NeoForge 아이콘 아트와 동일한 `plexus_icon.png`를 사용하며, 그래픽 자산은 `tools/loading-screen/generate_plexus_assets.py`로 생성됩니다.
- 게임 실행 인수 `--width`/`--height`(기본값 854 x 480)와 `--gameDir`의 `options.txt`(`guiScale`, `darkMojangStudiosBackground`)를 읽어 게임 창 크기 및 화면 중앙 위치를 일치시킵니다.
- Windows와 Linux에서 화면 픽셀 크기를 일치시키기 위해 `-Dsun.java2d.uiScale=1`을 적용합니다.
- 키보드 포커스를 요청하지 않도록 설정하여, 나중에 그 위에 열리는 게임 창이 자연스럽게 포커스를 가져가도록 합니다.
- Windows 환경에서는 별도의 콘솔 창이 뜨지 않도록 `javaw.exe`를 실행하며 최대 힙은 `-Xmx64m`로 제한됩니다.

## 무엇을 소유하는가
- `EarlyWindow`: 게임 프로세스 측 진입점으로, 프로세스 생명주기 관리 및 명령 전송을 담당합니다(`start`, `show`, `status`, `progress`, `underBar`, `snapshot`, `close`).
- `EarlyWindowSettings`: 게임 실행 인수와 `options.txt`에서 창 제목, 크기, 배율, 배경 모드, 로고 경로를 파싱하는 불변 레코드입니다.
- `EarlyWindowMain`: 보조 JVM 프로세스의 메인 클래스로, 표준 입력을 읽고 Swing 컴포넌트를 구동합니다.
- `EarlyWindowProtocol`: 프로세스 간 표준 입력 기반 명령 프로토콜(`show`, `status`, `progress`, `under`, `snapshot`, `close`) 직렬화 및 구문 분석기입니다.
- `LoadingScreenLayout`: 마인크래프트 26.1, 26.2, 26.3의 `LoadingOverlay` 및 `Window.calculateScale` 역공학 수치를 바탕으로 한 레이아웃 연산 클래스입니다.
- `LoadingScreenPainter`: 로딩 화면 배경, 로고, 막대, 큐브 애니메이션, 텍스트를 렌더링하는 페인터입니다.
- `LoadingScreenState`: 현재 표시할 이름, 상태 텍스트, 막대 아래 텍스트, 진행률(0~1)을 담는 레코드입니다.
- `PixelLetters`: 게임 계층 `PlexusLoadingOverlay`와 동일한 5 x 7 픽셀 폰트 렌더러입니다.
- `EarlyWindowArt`: 큐브 시트(`plexus_cube.png`), 아이콘(`plexus_icon.png`), 클라이언트 jar 내 로고를 로드하는 유틸리티입니다.

## 무엇을 하지 않는가
- 게임 프로세스 내부에서 직접 창을 열거나 게임의 그래픽 컨텍스트(GLFW, SDL)를 제어하지 않습니다.
- 창 프로세스 시작에 실패하거나 통신이 끊겨도 게임 실행을 중단시키지 않습니다(로그 한 줄을 남기고 정상 진행).
- Mojang Studios 로고 이미지를 저장소에 복사하여 보관하지 않습니다(클라이언트 jar에서 직접 읽음).
- Forge 및 NeoForge의 FML 내부 진행 메시지를 수집하거나 표시하지 않습니다.
- macOS 환경에서는 창을 표시하지 않습니다(아직 검증되지 않음).
- 로고 경로가 정의되지 않은 1.21.11 실험 버전이나 전용 서버(DEDICATED_SERVER)에서는 창을 표시하지 않습니다.
- 시스템 속성 `-Dplexus.earlyWindow=off`가 지정된 환경에서는 창을 열지 않습니다.

## 의존성
- JDK 표준 라이브러리의 `java.desktop` 모듈만 사용합니다.
- 그 외 다른 내부 모듈이나 외부 라이브러리, 게임 라이브러리에 컴파일 시 의존하지 않습니다.

## 주요 진입점
- `dev.plexusmodloader.earlywindow.EarlyWindow`: 게임 프로세스 측에서 창 프로세스를 시작하고 제어하는 유일한 공개 진입점입니다.
- `dev.plexusmodloader.earlywindow.EarlyWindowSettings`: 게임 실행 인수 및 옵션 파일로부터 초기 설정을 빌드하는 팩토리 메서드를 제공합니다.
- `dev.plexusmodloader.earlywindow.EarlyWindowMain`: 보조 JVM 프로세스에서 실행되는 메인 진입점입니다.

## 빌드·테스트
```powershell
# 단위 테스트 및 검사
.\gradlew.bat :plexus-early-window:check

# 클라이언트 월드 재로딩 및 초기 로딩 창 동작 확인 (창이 열림)
.\gradlew.bat :plexus-bootstrap:clientWorldReloadTest
```
- `EarlyWindowSettingsTest`: `--width`, `--height` 및 `options.txt` 옵션 파싱 동작 검증.
- `EarlyWindowProtocolTest`: 표준 입력 명령 직렬화 및 구문 해석 검증.
- `LoadingScreenLayoutTest`: 26.1, 26.2, 26.3 버전별 화면 배율 및 좌표 계산 검증.
- `LoadingScreenPainterTest`: 스냅샷 이미지 생성 및 렌더링 무결성 검증.
- 헤드리스 검증: `worldCreateSaveTest`, `compatibilityMatrix`, `forgeGameTest`, `neoForgeWorldTest`.
- 창이 열리는 검증: 26.2와 26.3의 `clientWorldReloadTest`, 26.2의 `neoForgeClient`, 26.2의 `forgeClient`.

## 대표 흐름
1. 클라이언트 실행 시 `plexus-runtime-impl`의 `StartupWindow`가 `EarlyWindowSettings.forGame`으로 창 설정을 구성하고 `EarlyWindow.start`를 호출하여 보조 프로세스를 실행합니다.
2. 보조 프로세스(`EarlyWindowMain`)는 숨겨진 상태로 시작되며, 준비가 완료되면 게임 프로세스가 `show` 명령을 전송하여 화면 중앙에 초기 창을 엽니다.
3. FML 호스트 로더가 감지되면 NeoForge는 헤드리스 모드로 시작하고, MinecraftForge는 `ForgeEarlyWindowOff`를 통해 자체 초기 창을 비활성화합니다.
4. Plexus 모드 로더가 모드를 탐색하는 동안 `status("Found 5 mod files")`와 `progress(0.15f)` 등의 명령을 보내 진행률과 상태를 갱신합니다.
5. 모드 해석이 완료되면 `underBar` 명령을 통해 막대 아래에 로더별 모드 수(예: `Mods: Fabric 1 · Plexus 4`)를 전달합니다.
6. 호스트 로더 준비 시 `status("Starting NeoForge")` 등을 전달하고, 마인크래프트 `Main` 실행 시 `status("Starting Minecraft 26.2")`를 보낸 뒤 스냅샷 디렉터리가 지정되어 있으면 `snapshot` 명령으로 `early-window.png`를 저장합니다.
7. 게임 창이 열리고(`opening()`, 20초 안전 타이머 시작) 게임 계층의 `PlexusLoadingOverlay`가 첫 로딩 화면 프레임을 그리면 `GameWindowListener.shown()`이 호출됩니다.
8. `shown()` 호출 0.3초 후 `close` 명령을 보내거나 표준 입력을 닫아 초기 창 프로세스를 종료하고 게임 창에 포커스를 넘깁니다(최대 3분 초과 시 자동 종료).

## 알려진 제한
- 게임 자체의 창이 아니라 보조 프로세스이므로 게임 창이 처음 열릴 때 최대 0.3초 동안 두 개의 창이 동시에 존재합니다.
- 초기 로딩 동안 최대 64 MiB 힙을 점유하는 보조 JVM 프로세스가 추가로 실행됩니다.
- macOS 환경은 아직 지원하지 않습니다.
- Forge 및 NeoForge FML 자체의 내부 진행 메시지는 Plexus 로딩 창에 전달되지 않습니다.
- 진행 막대는 측정된 시간이 아니라 Plexus 모드 로더의 사전 정의된 단계별 진행률을 따릅니다.
- Quilt 단독 실행 창 동작, macOS, Linux, 런처 프로필 테스트(`launcherProfileTest`), 1.21.11 실험 버전은 아직 확인되지 않았습니다.
