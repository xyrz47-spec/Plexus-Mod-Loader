<p align="center">
  <img src="logo.svg" alt="PolyLoader" width="96" height="96">
</p>

<h1 align="center">PolyLoader</h1>

<p align="center">
  A mod loader and Native API for Minecraft: Java Edition 26.1 – 26.2<br>
  <a href="https://polyloader.dothome.co.kr/download/">Download</a> ·
  <a href="https://polyloader.dothome.co.kr/docs/">Docs</a> ·
  <a href="https://polyloader.dothome.co.kr/ko/docs/">한국어 문서</a> ·
  <a href="#한국어">한국어 안내</a>
</p>

<p align="center">
  <img alt="Minecraft 26.1 – 26.2" src="https://img.shields.io/badge/Minecraft-26.1%20%E2%80%93%2026.2-62b47a">
  <img alt="Java 25" src="https://img.shields.io/badge/Java-25-e76f00">
  <img alt="status alpha" src="https://img.shields.io/badge/status-alpha-f5b700">
  <img alt="license non-commercial" src="https://img.shields.io/badge/license-PolyLoader%20Non--Commercial-555">
</p>

---

<p>This repository hosts PolyLoader's installers. The source code is not public for now.<p/>
<p>이 저장소는 PolyLoader 설치 파일을 배포하는 곳입니다. 소스 코드는 현재 비공개입니다.<p/>

## English

PolyLoader is a mod loader that keeps Minecraft's launch foundation as it is and extends or replaces the game systems on top of it. It provides its own **Native API** and also runs some Fabric mods. Mods made with the Native API are called **Poly Mods**.

> **This is an alpha.** It supports Minecraft 26.1, 26.1.1, 26.1.2 and 26.2, and the API may change. Back up worlds you care about.

### Install

1. Run the vanilla version you want (26.1, 26.1.1, 26.1.2 or 26.2) once in the Minecraft Launcher, quit the game, and close the launcher.
2. Download from the [releases page](https://github.com/xyrz47-spec/PolyLoader/releases/latest).
   - Windows: `PolyLoader-<version>.exe`. No separate Java install is needed; it uses the Java the launcher downloaded.
   - macOS and Linux: `PolyLoader-<version>.jar`. Run it with Java 17 or newer (`java -jar PolyLoader-<version>.jar`).
3. Click **Install**.
4. Pick the **PolyLoader <Minecraft version>** profile (for example **PolyLoader 26.2**) in the launcher and play. Put mods in the launcher folder's `mods/`.

The [install guide](https://polyloader.dothome.co.kr/docs/getting-started/install/) covers details and uninstalling. The installer is not code-signed, so Windows SmartScreen may warn about it. You can check downloads against the release's `SHA256SUMS.txt`.

### Compatibility

| Mods | Status |
|---|---|
| Poly Mods | Supported |
| Fabric mods | Experimental. Fabric mods run as under Fabric Loader, Mixin included; 137 popular Fabric mods (Sodium, Iris, Lithium and more) load together on the client and join a world, and 99 pass a real dedicated server start |
| Quilt mods | Not supported yet (a goal). Quilt mods that also ship `fabric.mod.json` are handled as Fabric mods |
| NeoForge mods | Not run. PolyLoader diagnoses what they need and logs it |
| Forge mods | Not run. PolyLoader diagnoses what they need and logs it |

Mods that cannot run are excluded, with the reason, before the game starts. See the [compatibility page](https://polyloader.dothome.co.kr/docs/reference/compatibility/).

### License

PolyLoader is licensed under the **PolyLoader Non-Commercial License 1.0** ([`LICENSE`](LICENSE)). Non-commercial use and distribution are allowed; commercial use, such as selling it or bundling it into paid products, needs a separate license. Third-party components and their licenses are listed in [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md).

## 한국어

PolyLoader는 Minecraft의 실행 기반은 그대로 두고, 그 위의 게임 시스템을 확장하고 교체하는 모드 로더입니다. 자체 API인 **Native API**를 제공하고, 일부 Fabric 모드도 함께 실행합니다. Native API로 만든 모드를 **Poly 모드**라고 부릅니다.

> **알파 버전입니다.** Minecraft 26.1, 26.1.1, 26.1.2, 26.2를 지원하며, API는 바뀔 수 있습니다. 중요한 월드는 백업하세요.

### 설치

1. Minecraft Launcher에서 쓰려는 바닐라 버전(26.1, 26.1.1, 26.1.2, 26.2)을 한 번 실행한 뒤 런처를 닫습니다.
2. [릴리스 페이지](https://github.com/xyrz47-spec/PolyLoader/releases/latest)에서 설치 파일을 다운로드합니다.
   - Windows: `PolyLoader-<버전>.exe`. Java를 따로 설치하지 않아도 됩니다. 런처에 들어 있는 Java를 사용합니다.
   - macOS·Linux: `PolyLoader-<버전>.jar`. Java 17 이상에서 실행합니다(`java -jar PolyLoader-<버전>.jar`).
3. **설치**를 누릅니다.
4. 런처에서 **PolyLoader <Minecraft 버전>** 프로필(예: **PolyLoader 26.2**)을 고르고 플레이합니다. 모드는 런처 폴더의 `mods/`에 넣습니다.

자세한 내용과 제거 방법은 [설치 안내](https://polyloader.dothome.co.kr/ko/docs/getting-started/install/)를 참고하세요. 설치 프로그램에 아직 코드 서명이 없어 Windows에서 SmartScreen 경고가 표시될 수 있습니다. 다운로드한 파일은 릴리스의 `SHA256SUMS.txt`로 확인할 수 있습니다.

### 호환성

| 모드 | 상태 |
|---|---|
| Poly 모드 | 지원 |
| Fabric 모드 | 실험적. Mixin을 포함해 Fabric Loader에서처럼 실행되며, Sodium, Iris, Lithium 등 인기 Fabric 모드 137개를 함께 넣고 클라이언트에서 월드에 들어가며, 99개가 실제 전용 서버 시작을 통과합니다 |
| Quilt 모드 | 아직 지원하지 않음(목표). `fabric.mod.json`도 든 Quilt 모드는 Fabric 모드로 처리합니다 |
| NeoForge 모드 | 실행하지 않음. 필요한 기능을 진단해 로그에 남깁니다 |
| Forge 모드 | 실행하지 않음. 필요한 기능을 진단해 로그에 남깁니다 |

실행할 수 없는 모드는 게임이 시작되기 전에 이유와 함께 제외합니다. 자세한 내용은 [호환성 문서](https://polyloader.dothome.co.kr/ko/docs/reference/compatibility/)에 있습니다.

### 라이선스

PolyLoader는 **PolyLoader Non-Commercial License 1.0**([`LICENSE`](LICENSE))을 따릅니다. 비상업적 목적의 사용·배포는 허용되고, 판매·유료 상품 포함 등 상업적 이용은 별도 라이선스가 필요합니다. 효력이 있는 것은 영어 원문입니다. 포함된 제3자 구성요소의 라이선스는 [`THIRD_PARTY_NOTICES.md`](THIRD_PARTY_NOTICES.md)에 있습니다.

---

NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.
Minecraft is a trademark of Mojang Synergies AB.
