# Forge 완전 지원 계획 — 베타로 가는 길 (2026-10-23 작성)

[`RELEASE_AND_DEPLOY.md`](../RELEASE_AND_DEPLOY.md)의 버전 단계에 따라, **Forge 모드를 완전히 지원하는 릴리스부터 `beta`**(`0.2.0-beta.1`)입니다. "완전히 지원"은 `compatibilityCorpus`와 [`COMPATIBILITY.md`](../COMPATIBILITY.md)의 결과로만 판단합니다. 이 계획은 [Fabric 완전 지원](FABRIC_FULL_SUPPORT.md)과 같은 여섯 기준을 Forge에 적용하고, 남은 일을 정합니다. 원칙도 같습니다. **실패하는 검증을 먼저 만들고, 그것을 통과시킵니다.** 지금까지의 Forge 작업(G0~G9)은 [`FORGE_SUPPORT.md`](FORGE_SUPPORT.md)에 있습니다.

## 완료 기준

"Forge 완전 지원"이라고 쓰려면 다음이 모두 참이어야 합니다. 지원하는 모든 Minecraft 버전(26.1 계열, 26.2)에서입니다.

1. 실제 인기 Forge 모드 묶음이 **클라이언트**에서 저장된 월드에 들어가고, 틱이 돌고, 저장하고 나올 수 있다.
2. 같은 묶음이 게임 테스트 서버(월드 생성·틱·저장)를 통과하고, 전용 서버 시작은 Forge의 실행 핸들러가 게임을 시작해 EULA 확인까지 간다. 전용 서버는 Forge가 모드를 로드하기 전에 EULA를 확인하고, Poly 모드 로더는 EULA에 동의하지 않기 때문입니다.
3. 설치 프로그램으로 만든 런처 프로필과 서버에서 Forge 모드가 있는 채로 게임이 시작된다.
4. Forge 런타임에서 Poly 모드 로더가 빼거나 거부하는 기능이 없다(JS coremod, 변환 서비스, 모드 위치 지정자, access transformer, Mixin).
5. 빠지는 Forge 모드는 Forge에서도 빠지거나 실행되지 않는 경우뿐이다(FML 요구 조건 `209`, 서버의 클라이언트 전용 모드).
6. Forge 모드를 넣은 상태의 서버 틱·시작 시간을 측정해 문서에 남긴다.

Fabric 모드와 Forge 모드를 한 게임에서 함께 실행하는 것(G8, `138`)은 이 기준 밖입니다. 따로 다룹니다.

## 지금 상태 (2026-10-23)

| 기준 | 26.2 | 26.1 |
|---|---|---|
| 1. 클라이언트 | `corpusForgeClient` 통과(G7). libIPN 고정 뒤 다시 통과: Forge 모드 40개 | 26.1(Forge 62.0.9) `corpusForgeClient` 통과: 36개 중 26개 실행, 월드가 실험 설정 확인을 물으면 플레이어처럼 답함. 26.1.2(64.1.5): 36개 |
| 2. 게임 테스트 서버·전용 서버 | `corpusForge` 38개 중 32개, 6개는 서버의 클라이언트 전용 `209`. 전용 서버 시작 41개 중 35개(`corpusForgeServer`, B4) | 26.1: `corpusForge` 36개 중 18개(18개 `209`, B5). 26.1.2: 34개 중 28개. 전용 서버 시작(B4) |
| 3. 설치한 프로필·서버 | `launcherProfileForgeTest`, `serverInstallTest`(G9) | 26.1.2 `serverInstallTest`, `launcherProfileForgeTest`(26.1.2 묶음 Forge 모드 36개, B6) |
| 4. Forge 런타임 기능 | access transformer·Mixin(G5), 변환 서비스·모드 위치 지정자(G6, Crash Assistant·CustomSkinLoader). JS coremod는 Forge 26.x에 없음(B3) | 같음 |
| 5. 빠지는 모드 점검 | 서버의 클라이언트 전용 6개만 남음(B5) | 버전 범위 9개·Mixin 3개는 실제 Forge도 멈춤, 나머지 6개는 서버의 클라이언트 전용(B5) |
| 6. 성능 | 측정 6(B7) | 26.2에서만 측정(26.1은 같은 코드 경로) |

## 단계

| # | 일 | 검증(먼저 만듦) | 상태 |
|---|---|---|---|
| B1 | 26.1 묶음이 게임 테스트 서버를 통과 | `corpusForge -PpolyMinecraft=26.1`: 월드 생성·GameTest·저장 | **완료** (2026-10-23). GlitchCore 26.1.0.4·TerraBlender 26.1.0.2는 매니페스트에 `MixinConfigs`가 없어 실제 Forge에서도 Mixin이 적용되지 않으므로 묶음 실행에서 뺌(`brokenUnderForge`). 그 뒤 GameTest·저장 통과, 38개 중 20개 실행·18개 `209`(B5에서 점검) |
| B2 | 26.1 클라이언트 | `corpusForgeClient -PpolyMinecraft=26.1`(창이 열림) | **완료** (2026-10-23): 저장한 월드를 열고 200틱 뒤 저장·종료. Cherished Worlds 16.0.1+26.1.2는 클라이언트 설정에서 `@SubscribeEvent` 메서드가 하나인 리스너 클래스를 등록하는데, Forge의 EventBus(62.0.9의 7.0.1, 65.1.5의 7.0.5)가 이것을 조건 없이 거부(`IllegalArgumentException: Only a single listener found`, `javap` 확인)해 FML이 모드 로딩을 멈추므로 실제 Forge에서도 실패합니다. 묶음 실행에서 뺌(`brokenUnderForge`) |
| B3 | JS coremod | Forge 26.x에 JS coremod 엔진이 있는지 확인 | **완료** (2026-10-23): 해당 없음. Forge 65.1.5·62.0.9의 런타임 라이브러리(`version.json`)에는 `coremods-api`(Forge 자신의 `forge-transformers`가 쓰는 ASM 도우미)만 있고, JS 엔진(`coremods`, Nashorn)이 없습니다. `fmlloader`·`fmlcore`·universal jar에도 `coremods.json`을 읽는 코드가 없습니다. 묶음 모드(Open Parties and Claims, Xaero's Minimap, ModernUI)의 `coremods.json`은 실제 Forge도 무시하므로, Poly 모드 로더가 무시하는 것과 같습니다 |
| B4 | 묶음으로 전용 서버 시작 | `corpusForgeServer`: 묶음의 Forge 모드로 Forge의 실행 핸들러가 게임을 시작해 EULA 확인까지 | **완료** (2026-10-23): 26.2 41개 중 33개, 26.1 39개 중 21개가 요구 조건을 만족하고, Forge의 서버 실행 핸들러가 게임을 시작해 EULA 확인까지(`compatibilityCorpus`에 포함) |
| B5 | 빠지는 모드 점검 | 26.2·26.1에서 `209`로 빠지는 모드마다 Forge에서도 빠지거나 멈추는지 확인해 `COMPATIBILITY.md`에 적음 | **완료** (2026-10-23). **Mixin 설정**: 26.1의 Balm(중첩 kuma-api의 없는 `kuma_api.api.mixins.json`), Boatiview·No Chat Reports(`JAVA_25`)는 실제 Forge 62.0.9 서버에서도 Mixin 0.8.7의 `MixinInitialisationError`로 EULA 확인 전에 멈춤. 확인을 Mixin이 읽는 방식에 맞춤(`.json` 이름만, 수준은 `trim`·대문자, 중첩 jar 포함). **버전 범위**: FML은 요구 조건이 맞지 않는 모드가 하나라도 있으면 모드 로딩 전체를 멈춤(`ModSorter`·`ModLoader`, `javap`). 26.1에서 빠지는 9개는 26.1.2용 빌드(Forge 64 이상)라, 26.1.2 묶음을 만들어 Forge 64.1.5로 실행: 34개 중 28개. 26.2의 libIPN은 Modrinth 표기 오류(26.3용 jar)라 26.2용 빌드로 고정, Inventory Profiles Next와 함께 실행(38개 중 32개). **클라이언트 전용**: 서버에서만 빠지고 클라이언트에서 실행(기준 5의 허용 범위). 자세한 표는 [`FORGE_SUPPORT.md`](FORGE_SUPPORT.md) "B5" |
| B6 | 설치한 프로필·서버의 26.1 | `serverInstallTest -PserverInstallMinecraft=26.1.2`, `launcherProfileForgeTest -PpolyMinecraft=26.1.2` | **완료** (2026-10-23). **서버**: 설치 jar로 26.1.2 서버를 설치해 모드 없이 EULA 확인까지, Forge 26.1.2-64.1.5를 게임 테스트 서버(GameTest·저장)와 전용 서버(EULA 확인까지)로 실행. `forge_hello`는 Forge 65 이상용이라 26.1.x에서는 모드 없이 Forge만 실행(`-Dpoly.forge=always`). **런처 프로필**: `launcherProfileForgeTest`가 26.2가 아닌 버전에서는 그 버전 묶음의 Forge 모드로 실행. 26.1.2에서 설치한 프로필이 Forge 모드 36개를 로드하고 저장된 월드에서 200틱 후 저장·종료. 실행하며 찾은 것: Better Advancements 0.6.0.76과 Rhino 2101.2.7은 실제 Forge에서도 실패하는 빌드라 뺌([`FORGE_SUPPORT.md`](FORGE_SUPPORT.md) "B5") |
| B7 | 성능 | `startupBenchmarkForge`·`tickBenchmarkForge`, [`PERFORMANCE.md`](../PERFORMANCE.md) | **완료** (2026-10-23, 26.2): [`PERFORMANCE.md`](../PERFORMANCE.md) 측정 6. 바닐라 대 Poly 모드 로더 + Forge + 모드 32개: 시작 6.28초 → 22.86초, 틱 p50 8.13 → 12.49ms, GC 직후 힙 139 → 267 MiB. Forge 자체 실행기와는 비교하지 않음 |
| B8 | 판정과 베타 | `COMPATIBILITY.md`의 "Forge 완전 지원 점검", 상태 `Verified`, 웹사이트·README, 버전 `0.2.0-beta.1` | 완료 (2026-10-24). **판정 완료**: 세 버전에서 `check`, `serverBootSmokeTest`, `worldCreateSaveTest`, `nativeApiGameTests`, `compatibilityMatrix`, `compatibilityCorpus` 통과, 클라이언트·런처 프로필은 창을 열어 통과. `COMPATIBILITY.md` 상태 `Verified`. 웹사이트·README 갱신, 첫 베타 `0.2.0-beta.1`(2026-10-24)로 완료 |
