# 로드맵 — 베타 이후 순서 (2026-10-23 작성)

[`RELEASE_AND_DEPLOY.md`](../RELEASE_AND_DEPLOY.md)의 버전 단계에 따라 Forge 완전 지원([`FORGE_FULL_SUPPORT.md`](FORGE_FULL_SUPPORT.md))이 끝나면 베타(`0.2.0-beta.1`)입니다. Fabric·Forge·NeoForge·Quilt를 모두 완전히 지원하면 첫 정식 버전 `1.0.0`입니다. 이 문서는 베타 뒤에 할 일의 순서를 정합니다. 각 단계의 세부 계획은 시작할 때 `docs/plans/`에 따로 씁니다.

## 순서

| # | 일 | 버전 단계 | 세부 계획 |
|---|---|---|---|
| 1 | NeoForge 완전 지원 | beta | [`NEOFORGE_FULL_SUPPORT.md`](NEOFORGE_FULL_SUPPORT.md), **완료**(2026-10-24, `COMPATIBILITY.md` "NeoForge 완전 지원 점검") |
| 2 | Quilt 완전 지원 | beta | [`QUILT_FULL_SUPPORT.md`](QUILT_FULL_SUPPORT.md), **완료**(2026-10-24, `COMPATIBILITY.md` "Quilt 완전 지원 점검") |
| 3 | 네 로더 판정과 `1.0.0` | 정식 | `COMPATIBILITY.md`의 로더별 완전 지원 점검 |
| 4 | Minecraft 버전 확장: 최신 버전(26.2 다음)과 레거시 버전(26.1 이전)을 함께 | 1.0 이후 | 최신은 [`new-minecraft-version.md`](../contributing/new-minecraft-version.md)와 26.3 준비 자료 [`MINECRAFT_26_3.md`](MINECRAFT_26_3.md), 레거시는 1.21 준비 자료 [`MINECRAFT_1_21.md`](MINECRAFT_1_21.md), 1.16.5 준비 자료 [`MINECRAFT_1_16_5.md`](MINECRAFT_1_16_5.md)와 [ADR-0019](../adr/0019-obfuscated-minecraft-versions.md) |

## 왜 이 순서인가

- **로더 지원이 `1.0.0`의 조건입니다.** 지원하는 Minecraft 버전 수는 조건이 아닙니다.
- **버전을 늘리면 그 뒤 모든 일이 늘어납니다.** 지원 버전마다 버전 어댑터와 게임 계층(26.2 기준 약 1,000줄과 약 9,800줄)이 따로 있습니다. 게임 계층의 수정은 모든 지원 버전에 반영합니다(`AGENTS.md` 원칙 6). 버전마다 모드 묶음과 검증 작업도 따로 돕니다. NeoForge·Quilt를 지금의 지원 버전(26.1 계열, 26.2)에서 먼저 끝내면, 그 뒤 버전을 더할 때 함께 따라옵니다.
- **NeoForge는 Forge 작업을 바로 이어받습니다.** 실제 로더를 Poly 모드 로더 위에서 실행하는 방식(ADR-0021), 로더를 넘는 의존·차단 연쇄, 묶음 검증 틀이 이미 있습니다. NeoForge는 FancyModLoader가 ModLauncher를 흡수한 다른 구조라, Forge 코드를 그대로 쓰지는 못합니다.
- **Quilt는 Fabric 위에 올립니다.** 설계서 §3.1의 방향대로 Fabric 브리지 위에 `quilt.mod.json`과 Quilt Standard Libraries 대응을 더합니다. `fabric.mod.json`도 든 Quilt 모드는 이미 Fabric 모드로 실행됩니다.
- **최신 버전과 레거시 버전을 한 단계로 묶는 이유.** 둘 다 "지원 버전을 늘리는 일"이고, 같은 구조(ADR-0011의 버전 목록, 버전별 어댑터·게임 계층)를 씁니다. 레거시 버전은 난독화된 게임 이름을 다뤄야 해서(ADR-0019) 일이 더 큽니다. 한 번에 설계해야 같은 일을 두 번 하지 않습니다.

## 이 순서에서 감수하는 것

- 4단계 전까지 26.2보다 새로운 Minecraft 버전(26.3 등)은 지원하지 않습니다. 그동안 인기 모드가 새 버전으로 옮겨 가면, 묶음을 26.2에서 만들 수 있는 모드가 줄어들 수 있습니다. 이 영향은 묶음을 다시 만들 때(`modCorpus`) 모드 수로 확인합니다.
- 지원하는 버전의 핫픽스는 따로 다룹니다. 변환 대상과 게임 계층이 쓰는 멤버가 바뀌지 않은 핫픽스는 새 모듈 없이 `gradle/minecraft-versions.properties`의 `hotfixes`에 더할 수 있습니다(26.1.1·26.1.2처럼, ADR-0011 4번). 이것은 4단계를 기다리지 않습니다.

## 바꾸는 방법

순서를 바꾸면 이 문서의 표와 "왜 이 순서인가"를 함께 고치고, 바꾼 날짜와 이유를 적습니다.
