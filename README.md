<p align="center">
  <img src="logo.svg" alt="Poly Mod Loader" width="96" height="96">
</p>

<h1 align="center">Poly Mod Loader</h1>

<p align="center">
  A high-performance multi-loader and Native bytecode engine for Minecraft: Java Edition 26.1 – 26.3<br>
  <a href="PROJECT.md"><strong>Project Status & Overview</strong></a> ·
  <a href="DEVELOPING.md"><strong>Developer Guide</strong></a> ·
  <a href="ARCHITECTURE.md"><strong>Architecture</strong></a> ·
  <a href="https://www.polymc.net/docs/">Documentation</a> ·
  <a href="https://www.polymc.net/download/">Download</a> ·
  <a href="CHANGELOG.md">Changelog</a> ·
  <a href="#한국어">한국어</a>
</p>

<p align="center">
  <img alt="Minecraft 26.1 – 26.3" src="https://img.shields.io/badge/Minecraft-26.1%20%E2%80%93%2026.3-62b47a">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-e76f00">
  <img alt="status stable" src="https://img.shields.io/badge/status-stable-f5b700">
  <img alt="license MPL-2.0" src="https://img.shields.io/badge/license-MPL--2.0-555">
</p>

---

Poly Mod Loader is a mod loader that keeps Minecraft's launch foundation as it is and extends or replaces the game systems on top of it. It runs Fabric, Forge, NeoForge and Quilt mods, and provides its own **Native API**.

A **Poly Mod** is a mod made with the Native API that runs on Poly Mod Loader, the same way people say Fabric mod or Forge mod.

> It supports Minecraft 26.1, 26.1.1, 26.1.2, 26.2 and 26.3. On 26.3, mods of the other loaders are checked with test mods; the popular-mod runs below are on 26.1 and 26.2. Every Native API is still in preview and may change. Back up worlds you care about.

## Install (players)

1. Run the vanilla version you want (26.1, 26.1.1, 26.1.2, 26.2 or 26.3) once in the Minecraft Launcher, quit the game, and close the launcher.
2. Download `PolyModLoader-<version>.jar` from the [download page](https://www.polymc.net/download/) (the files are the [GitHub releases](https://github.com/xyrz47-spec/Poly-Mod-Loader/releases/latest)). The same jar runs on Windows, macOS and Linux.
3. Open it with Java 17 or newer: double-click it, or run `java -jar PolyModLoader-<version>.jar`.
4. Click **Install**.
5. Pick the **Poly Mod Loader <Minecraft version>** profile (for example **Poly Mod Loader 26.2**) in the launcher and play. Put mods in the launcher folder's `mods/`.

The [install guide](https://www.polymc.net/docs/getting-started/install/) covers details and uninstalling.

## Make mods

Poly Mods are built with Java 25 and Gradle. One command creates a project, and game tests check it without opening the game.

- [Make your first Poly Mod](https://www.polymc.net/docs/getting-started/first-mod/): build **Crystal Garden**, a mod with growing crystals, from scratch. The finished mod is `examples/crystal-garden`, which is not part of this public repository.
- [Native API guide](https://www.polymc.net/docs/native-api/) and [API reference](https://www.polymc.net/docs/api-reference/)

What the Native API covers:
- blocks, items, mobs and effects
- block entities and menus
- world generation and dimensions
- commands and permissions
- networking and join checks
- saved data and sync
- HUD, screen and shader drawing
- in-game tests
- game system replacement
- declarative bytecode transforms

The public API exposes no Minecraft classes, so a mod that uses only the Native API can declare a version range such as `"minecraft": ["26.*"]`. How the API is growing and current milestones are tracked in [PROJECT.md](PROJECT.md) and the [Native API Guide](https://www.polymc.net/docs/native-api/).

## Compatibility

| Mods | Status |
|---|---|
| Poly Mods | Supported |
| Fabric mods | Supported. Fabric mods run as under Fabric Loader, Mixin included. On 26.1 and 26.2, popular Fabric mods (138 on 26.2: Sodium, Iris, Lithium and more) play a world together on the client, save it and quit, and 100 start a real dedicated server; the only mods left out are ones Fabric Loader refuses too |
| Quilt mods | Supported. `quilt.mod.json` is read with Quilt Loader's rules, and Quilt mods get Quilt Loader's API, their Mixins and access wideners, next to Fabric, Forge and Poly Mods. On 26.1, 26.1.2 and 26.2, every Quilt-only build on Modrinth (114 on 26.2) was run: on the client they play a saved world, save it and quit, they create and save a world on the server and start a real dedicated server, and the installed launcher profile and server run them. The only mods left out are ones Quilt Loader refuses or cannot run too. Quilt Standard Libraries (QSL) have no release for 26.x, so mods that need QSL are left out with the reason. A jar with both `fabric.mod.json` and `quilt.mod.json` runs as a Fabric mod |
| NeoForge mods | Supported. Poly Mod Loader runs NeoForge and its loader FML themselves (NeoForge 26.1.0.19-beta on 26.1, 26.1.1.15-beta on 26.1.1, 26.1.2.122 on 26.1.2, 26.2.0.93 on 26.2, 26.3.0.98 on 26.3). On 26.1, 26.1.2 and 26.2, popular NeoForge mods play a saved world on the client, save it and quit (54 on 26.2, Sodium and Iris included), create and save a world on the server and start a real dedicated server, next to Poly Mods; the installed launcher profile and server run them too. The only mods left out are ones NeoForge cannot run either. NeoForge mods also run in the same game as Fabric and Quilt mods, with a limit: a Fabric or Quilt mod whose mixins do not fit NeoForge's patch is not loaded, and the log and the title screen say that Poly Mod Loader, not the mod, has to change (`POLY-COMPAT-224`). This leaves out several Fabric API modules and the Fabric mods that need them (on 26.2, 10 Fabric API modules, measured 2026-10-27). Not supported: NeoForge mods in the same game as MinecraftForge mods; the NeoForge mods are then left out with the reason (`POLY-COMPAT-221`). The first start with NeoForge mods downloads NeoForge's files |
| Forge mods | Supported. Poly Mod Loader runs Forge and FML themselves (Forge 62.0.9 on 26.1, 64.1.5 on 26.1.2, 65.1.5 on 26.2, 66.1.0 on 26.3). On 26.1 and 26.2, popular Forge mods play a saved world on the client, save it and quit (40 on 26.2), create and save a world on the server and start a real dedicated server; the installed launcher profile and server run them too. The only mods left out are ones Forge refuses too. Forge mods also run in the same game as Fabric and Quilt mods, with the same limit as NeoForge (`POLY-COMPAT-224`; on 26.2 no Fabric API module is left out, but Architectury, Balm and Lithium are, measured 2026-10-27). The first start with Forge mods downloads Forge's files |

Poly Mod Loader excludes mods it cannot run before the game starts, with the reason. This was checked against 124 real popular mods without a single exception or crash during assessment. The [compatibility page](https://www.polymc.net/docs/reference/compatibility/) and [PROJECT.md](PROJECT.md) detail the criteria and results.

## Performance

Performance is stated only from empirical measurements (see [PROJECT.md](PROJECT.md#2-core-pillars--technical-differentiators)):

- Startup time, server tick time and memory: the difference from vanilla is within measurement noise.
- Client frame time: with the development mods installed, Poly Mod Loader is slightly slower.

## Build from source

You need JDK 25 and Git. The first build automatically downloads Minecraft from Mojang's servers and verifies it against SHA-1 checksums. Minecraft assets are cached in `~/.gradle/caches/poly-minecraft` and are never redistributed in the repository.

```powershell
git clone https://github.com/xyrz47-spec/Poly-Mod-Loader.git polymodloader
cd polymodloader
.\gradlew.bat check                                   # compile, format check, tests
.\gradlew.bat :poly-installer:installerDistributions  # installers (poly-installer/build/distributions/)
.\gradlew.bat :poly-bootstrap:runClient               # development client
```

On macOS and Linux use `./gradlew`. Comprehensive module architecture, verification tasks, and contribution guidelines are detailed in **[DEVELOPING.md](DEVELOPING.md)** and **[ARCHITECTURE.md](ARCHITECTURE.md)**.

## License

Poly Mod Loader is open source under the **Mozilla Public License 2.0** ([`LICENSE`](LICENSE)). Copyright 2026 _xyrz and the Poly Mod Loader contributors. The summary below is only a guide; the license text is what applies.

- **You may** use, study, modify and redistribute Poly Mod Loader for any purpose, including commercial ones.
- **If you distribute a modified version**, the files of Poly Mod Loader that you changed stay under the MPL 2.0, and you make their source available. Your own new files can use any license.
- **Mods, modpacks and launchers** that use Poly Mod Loader choose their own license. Code in [`templates/`](templates/) and `examples/` is under MIT No Attribution, so you can copy it into your mod freely.
- **Names and logo:** the license does not cover the names "Poly" and "Poly Mod Loader" or the logo. A modified version needs its own name; see [`TRADEMARKS.md`](TRADEMARKS.md).

Releases up to 1.1.0 were distributed under the PolyLoader Non-Commercial License 1.0. Third-party components and their licenses are listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## 한국어

Poly 모드 로더는 Minecraft의 실행 기반은 그대로 두고, 그 위의 게임 시스템을 확장하고 교체하는 모드 로더입니다. Fabric, Forge, NeoForge, Quilt 모드를 실행하고, 자체 API인 **Native API**를 제공합니다.

**Poly 모드**(영어: Poly Mod)는 Native API로 만들어 Poly 모드 로더에서 실행하는 모드입니다. Fabric 모드, Forge 모드와 같은 방식으로 부르는 이름입니다.

> Minecraft 26.1, 26.1.1, 26.1.2, 26.2, 26.3을 지원합니다. 26.3에서 다른 로더의 모드는 테스트 모드로 확인했고, 아래 인기 모드 실행 결과는 26.1과 26.2의 것입니다. Native API는 모두 미리보기 단계라 바뀔 수 있습니다. 중요한 월드는 백업하세요.

### 설치 (플레이어)

1. Minecraft Launcher에서 쓰려는 바닐라 버전(26.1, 26.1.1, 26.1.2, 26.2, 26.3)을 한 번 실행한 뒤 런처를 닫습니다.
2. [릴리스 페이지](https://github.com/xyrz47-spec/Poly-Mod-Loader/releases/latest)에서 `PolyModLoader-<버전>.jar`를 다운로드합니다. Windows, macOS, Linux 모두 같은 jar를 씁니다.
3. Java 17 이상으로 엽니다. 더블클릭하거나 `java -jar PolyModLoader-<버전>.jar`로 실행합니다.
4. **설치**를 누릅니다.
5. 런처에서 **Poly 모드 로더 <Minecraft 버전>** 프로필(예: **Poly 모드 로더 26.2**)을 고르고 플레이합니다. 모드는 런처 폴더의 `mods/`에 넣습니다.

자세한 내용과 제거 방법은 [설치 안내](https://www.polymc.net/ko/docs/getting-started/install/)를 참고하세요.

### 모드 만들기

Poly 모드는 Java 25와 Gradle로 만듭니다. 명령 한 줄로 프로젝트를 만들고, 게임을 켜지 않고도 게임 테스트로 확인할 수 있습니다.

- [첫 Poly 모드 만들기](https://www.polymc.net/ko/docs/getting-started/first-mod/): 자라는 크리스털 모드 **Crystal Garden**을 처음부터 만듭니다. 완성본은 `examples/crystal-garden`이며, 이 공개 저장소에는 들어 있지 않습니다.
- [Native API 안내](https://www.polymc.net/ko/docs/native-api/)와 [API 레퍼런스](https://www.polymc.net/ko/docs/api-reference/)

Native API로 할 수 있는 일:
- 블록·아이템·몹·효과 만들기
- 블록 엔티티와 메뉴
- 월드 생성과 차원
- 명령어와 권한
- 네트워크와 접속 확인
- 저장 데이터와 동기화
- HUD·화면·셰이더 그리기
- 게임 내 테스트
- 게임 시스템 교체
- 선언형 바이트코드 변환

공개 API에는 Minecraft 클래스가 드러나지 않습니다. 그래서 Native API만 쓰는 모드는 `"minecraft": ["26.*"]`처럼 버전 범위를 적어 둘 수 있습니다.

### 호환성

| 모드 | 상태 |
|---|---|
| Poly 모드 | 지원 |
| Fabric 모드 | 지원. Mixin을 포함해 Fabric Loader에서처럼 실행됩니다. 26.1과 26.2에서 인기 Fabric 모드(26.2는 Sodium, Iris, Lithium 등 138개)를 함께 넣고 클라이언트에서 월드를 플레이하고 저장한 뒤 나오며, 100개가 실제 전용 서버 시작을 통과합니다. 빠지는 모드는 Fabric Loader도 실행하지 않는 경우뿐입니다 |
| Quilt 모드 | 지원. `quilt.mod.json`을 Quilt Loader 규칙으로 읽고, Quilt 모드에 Quilt Loader API와 Mixin·access widener를 주며 Fabric·Forge·Poly 모드와 함께 실행합니다. 26.1, 26.1.2, 26.2에서 Modrinth의 Quilt 전용 판 전부(26.2는 114개)를 넣고 클라이언트에서 저장된 월드를 플레이하고 저장한 뒤 나오며, 서버에서 월드를 만들고 저장하고 실제 전용 서버를 시작하고, 설치한 런처 프로필과 서버에서도 실행됩니다. 빠지는 모드는 Quilt Loader도 거부하거나 실행하지 못하는 경우뿐입니다. Quilt 표준 라이브러리(QSL)는 26.x 판이 없어 QSL이 필요한 모드는 이유와 함께 빠집니다. `fabric.mod.json`과 `quilt.mod.json`을 다 든 jar는 Fabric 모드로 실행합니다 |
| NeoForge 모드 | 지원. Poly 모드 로더가 NeoForge와 그 로더 FML을 그대로 실행합니다(26.1은 NeoForge 26.1.0.19-beta, 26.1.1은 26.1.1.15-beta, 26.1.2는 26.1.2.122, 26.2는 26.2.0.93, 26.3은 26.3.0.98). 26.1, 26.1.2, 26.2에서 인기 NeoForge 모드를 넣고 클라이언트에서 저장된 월드를 플레이하고 저장한 뒤 나오며(26.2는 Sodium·Iris를 포함해 54개), 서버에서 월드를 만들고 저장하고 실제 전용 서버를 시작합니다. Poly 모드와 함께도 되고, 설치한 런처 프로필과 서버에서도 실행됩니다. 빠지는 모드는 NeoForge도 실행하지 못하는 경우뿐입니다. Fabric·Quilt 모드와 한 게임에서도 실행되지만 한계가 있습니다. mixin이 NeoForge 패치와 맞지 않는 Fabric·Quilt 모드는 로드하지 않고, Poly 모드 로더 쪽을 고쳐야 한다고 로그와 타이틀 화면에 알립니다(`POLY-COMPAT-224`). 그래서 Fabric API의 여러 모듈과 그것이 필요한 Fabric 모드가 빠집니다(26.2에서 Fabric API 모듈 10개, 2026-10-27 측정). 지원하지 않는 것: MinecraftForge 모드와 한 게임에서 실행. 그때는 NeoForge 모드가 이유와 함께 빠집니다(`POLY-COMPAT-221`). NeoForge 모드를 넣고 처음 시작하면 NeoForge 파일을 받습니다 |
| Forge 모드 | 지원. Poly 모드 로더가 Forge와 FML을 그대로 실행합니다(26.1은 Forge 62.0.9, 26.1.2는 64.1.5, 26.2는 65.1.5, 26.3은 66.1.0). 26.1과 26.2에서 인기 Forge 모드를 넣고 클라이언트에서 저장된 월드를 플레이하고 저장한 뒤 나오며(26.2는 40개), 서버에서 월드를 만들고 저장하고 실제 전용 서버를 시작합니다. 설치한 런처 프로필과 서버에서도 실행됩니다. 빠지는 모드는 Forge도 실행하지 않는 경우뿐입니다. Fabric·Quilt 모드와 한 게임에서도 실행되며, NeoForge와 같은 한계가 있습니다(`POLY-COMPAT-224`, 26.2에서 빠지는 Fabric API 모듈은 없고 Architectury·Balm·Lithium이 빠짐, 2026-10-27 측정). Forge 모드를 넣고 처음 시작하면 Forge 파일을 받습니다 |

Poly 모드 로더는 실행할 수 없는 모드를 게임이 시작되기 전에 이유와 함께 제외합니다. 실제 인기 모드 124개로 이 판정을 검증했고, 판정 중 예외나 크래시는 없었습니다. 자세한 기준과 결과는 [호환성 문서](https://www.polymc.net/ko/docs/reference/compatibility/)에 있습니다.

### 성능

성능은 실측 벤치마크 데이터를 바탕으로 제공합니다([PROJECT.md](PROJECT.md#2-core-pillars--technical-differentiators)).

- 시작 시간, 서버 틱 시간, 메모리: 바닐라 마인크래프트와의 차이가 측정 편차(오차 범위) 내에 있습니다.
- 클라이언트 프레임 시간: 개발용 모드를 설치한 상태에서 Poly 모드 로더가 약간 느립니다.

### 소스에서 빌드

JDK 25와 Git이 필요합니다. 첫 빌드 시 Mojang 공식 서버에서 마인크래프트 원본 에셋을 받아 SHA-1으로 검증하며, 마인크래프트 파일은 저장소에 절대 포함되거나 재배포되지 않습니다.

```powershell
git clone https://github.com/xyrz47-spec/Poly-Mod-Loader.git polymodloader
cd polymodloader
.\gradlew.bat check                                   # 컴파일, 포맷 검사, 테스트
.\gradlew.bat :poly-installer:installerDistributions  # 설치 파일 (poly-installer/build/distributions/)
.\gradlew.bat :poly-bootstrap:runClient               # 개발용 클라이언트 실행
```

macOS 및 Linux에서는 `./gradlew`를 사용합니다. 상세한 모듈 아키텍처 및 기여 가이드는 **[DEVELOPING.md](DEVELOPING.md)** 및 **[ARCHITECTURE.md](ARCHITECTURE.md)**, 그리고 프로젝트 진행 현황은 **[PROJECT.md](PROJECT.md)**를 참고하세요.

### 라이선스

Poly 모드 로더는 **Mozilla Public License 2.0**([`LICENSE`](LICENSE))을 따르는 오픈소스입니다. Copyright 2026 _xyrz and the Poly Mod Loader contributors. 아래는 이해를 돕는 요약이며, 효력이 있는 것은 라이선스 원문입니다.

- **할 수 있는 것:** 상업적 목적을 포함해 어떤 목적으로든 사용, 소스 열람, 수정, 재배포
- **수정본을 배포할 때:** 고친 Poly 모드 로더 파일은 MPL 2.0으로 남고, 그 소스를 공개해야 합니다. 새로 만든 파일은 원하는 라이선스를 써도 됩니다.
- **모드, 모드팩, 런처:** Poly 모드 로더를 쓰는 쪽이 자기 라이선스를 정합니다. [`templates/`](templates/)와 `examples/`의 코드는 MIT No Attribution이라 모드에 자유롭게 가져다 쓸 수 있습니다.
- **이름과 로고:** 라이선스는 "Poly", "Poly 모드 로더"라는 이름과 로고를 포함하지 않습니다. 수정본은 다른 이름을 써야 합니다. [`TRADEMARKS.md`](TRADEMARKS.md)를 보세요.

1.1.0까지의 릴리스는 PolyLoader Non-Commercial License 1.0으로 배포했습니다. 포함된 제3자 구성요소의 라이선스는 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)에 있습니다.

---

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
Minecraft is a trademark of Mojang Synergies AB. Minecraft는 Mojang Synergies AB의 상표입니다.
