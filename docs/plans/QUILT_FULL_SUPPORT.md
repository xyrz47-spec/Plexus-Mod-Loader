# Quilt 완전 지원 계획 — 완료 (2026-10-23 작성, 2026-10-24 완료)

[`ROADMAP.md`](ROADMAP.md)의 2단계입니다. NeoForge 완전 지원 뒤에 시작합니다. "완전히 지원"은 `compatibilityCorpus`와 [`COMPATIBILITY.md`](../COMPATIBILITY.md)의 결과로만 판단합니다.

원칙은 Fabric·Forge 때와 같습니다.
- **로더의 의미를 추측해 다시 만들지 않습니다.** `quilt.mod.json`의 뜻은 Quilt의 명세(RFC 0002)와 Quilt Loader의 실제 동작을 기준으로 합니다.
- **실패하는 검증을 먼저 만들고, 그것을 통과시킵니다.**

> 이 문서는 초안입니다. "지금 상태"의 사실은 2026-10-23에 저장소 코드, Quilt Maven·meta, Modrinth API, 받은 jar에서 직접 확인했습니다. "Q0에서 확인"으로 표시한 것은 아직 확인하지 않은 것이므로, Q0 조사 결과로 이 계획을 고친 뒤 시작합니다.

## 가장 먼저 알아야 할 것

- **Quilt Loader는 26.x에 있습니다. Quilt Standard Libraries(QSL)는 없습니다.** 로드맵의 "QSL 대응"은 지금 대응할 대상이 없습니다. 아래 "지금 상태"의 근거를 보고 완료 기준을 정했습니다.
- **26.x에서 Quilt를 표시한 모드는 거의 모두 Fabric jar입니다.** 이미 Fabric 모드로 실행됩니다. 남는 일은 `quilt.mod.json`만 든 jar(실제 예: Simple Voice Chat의 Quilt 판)를 읽고, 그 jar가 쓰는 Quilt Loader API(`org.quiltmc.loader.api`)를 제공하는 것입니다.

## 완료 기준

"Quilt 완전 지원"이라고 쓰려면 다음이 모두 참이어야 합니다. 지원하는 모든 Minecraft 버전(26.1 계열, 26.2)에서입니다. 여섯 기준의 틀은 [Fabric 완전 지원](FABRIC_FULL_SUPPORT.md)과 같습니다.

1. `quilt.mod.json`만 든 실제 모드 묶음(Quilt 묶음)이 **클라이언트**에서 저장된 월드에 들어가고, 틱이 돌고, 저장하고 나올 수 있다.
2. 같은 묶음이 전용 서버 시작과 게임 테스트 서버(월드 생성·틱·저장)를 통과한다.
3. 설치 프로그램으로 만든 런처 프로필과 서버에서 Quilt 모드가 있는 채로 게임이 시작된다.
4. 실제 Quilt 모드가 쓰는 Quilt Loader 공개 API와 `quilt.mod.json` 항목에서 Poly 모드 로더가 거부하는 것이 없다. 제공하지 않는 항목(예: loader plugin)은 목록으로 남기고, 그 항목을 쓰는 모드가 묶음에 없음을 보인다.
5. 빠지는 Quilt 모드는 Quilt Loader에서도 빠지거나 실행되지 않는 경우뿐이다.
6. Quilt 모드를 넣은 상태의 서버 틱·시작 시간을 측정해 [`PERFORMANCE.md`](../PERFORMANCE.md)에 남긴다.

QSL이 26.x용으로 나오면 "QSL을 쓰는 모드"를 기준 1·2에 더합니다(Q0에서 다시 확인). Quilt 모드를 Fabric 모드와 한 게임에서 함께 실행하는 것은 기준 안입니다. Quilt Loader도 Fabric 모드를 함께 로드하기 때문입니다.

## 지금 상태 (2026-10-23, 사실)

- **저장소: `Untested`.** `quilt.mod.json`을 읽는 코드가 없습니다. `quilt.mod.json`만 든 jar는 `POLY-DISCOVERY-002`로 로드하지 않고, `fabric.mod.json`도 든 jar는 Fabric 모드로 판정합니다(`COMPATIBILITY.md`). 모드 묶음을 만드는 `ModCorpusTask`의 로더 목록은 `fabric`, `neoforge`, `forge`이고 `quilt`는 없습니다.
- **Quilt Loader**(Quilt Maven `org.quiltmc:quilt-loader`, meta API `meta.quiltmc.org/v3`):
  - 최신은 `0.31.0-beta.4`(Maven `lastUpdated` 2026-09-04), 마지막 베타가 아닌 판은 `0.30.1`입니다.
  - 2026-04-01 커밋 "Treat all minecraft versions after 25.0 as being unobfuscated." 26.x의 난독화 없는 게임을 다룹니다. Fabric Loader 0.19.5까지 내려받아 합쳤습니다(2026-08-29 "Downstream fabric 0.19.5").
  - meta의 게임 목록에 26.1, 26.1.1, 26.1.2, 26.2, 26.3이 있습니다. `26.2` + `0.31.0-beta.4` 런처 프로필의 `mainClass`는 `org.quiltmc.loader.impl.launch.knot.KnotClient`, 라이브러리는 `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`, ASM 9.10.1, `quilt-config` 1.3.3, `quilt-json5` 1.0.4+final입니다. meta는 모든 게임 버전에 모든 로더 버전을 내주므로, 이것만으로 26.x에서 동작한다는 근거는 아닙니다. 실제 동작은 Q0에서 확인합니다.
  - jar에 `net.fabricmc.loader.api.*`·`net.fabricmc.api.*`가 들어 있습니다. Quilt Loader가 Fabric Loader API를 직접 제공합니다.
- **QSL과 Quilted Fabric API(QFAPI): 26.x용이 없습니다.**
  - Quilt Maven `org.quiltmc:qsl` 최신 `10.0.0-alpha.5+1.21.1`(2025-09-21). `org.quiltmc.quilted-fabric-api:quilted-fabric-api`의 가장 새 게임 버전은 `11.0.0-alpha.3+0.102.0-1.21`입니다(`latest` 표시는 `7.7.0+0.92.2-1.20.1`, 2024-12-02).
  - GitHub `QuiltMC/quilt-standard-libraries`의 기본 브랜치는 `1.21.5`, 마지막 push 2026-01-14입니다. Modrinth 프로젝트 `qsl`의 게임 버전은 1.21에서 끝납니다.
- **Modrinth**(검색 API, `project_type:mod`, 프로젝트 단위 분류이므로 그 게임 버전의 판이 Quilt를 표시했다는 뜻은 아님):

  | 게임 버전 | `quilt` 표시 | 그중 `fabric` 표시 없음 | 참고: `fabric` 표시 |
  |---|---|---|---|
  | 26.1 | 3505 | 1 | 10983 |
  | 26.1.2 | 3432 | 1 | 13304 |
  | 26.2 | 3445 | 1 | 13274 |

  "`fabric` 표시 없음" 1개는 `more-beets`(datapack 분류, forge·neoforge·quilt 표시)입니다.
- **26.2 다운로드 상위 100개**(Quilt 표시)를 판 단위로 보면 100개 모두 26.2 판이 `quilt`를 표시하고, Fabric 판과 다른 Quilt 전용 판은 `simple-voice-chat` 하나입니다(`quilt-2.6.25+26.2`, 26.1 계열은 `quilt-2.6.25+26.1.2`). 상위 9개 jar를 받아 본 결과:

  | 모드 | jar | 메타데이터 |
  |---|---|---|
  | modmenu | `modmenu-20.0.3.jar` | `fabric.mod.json` |
  | lithium | `lithium-fabric-0.25.3+mc26.2.jar` | `fabric.mod.json` |
  | immediatelyfast | `ImmediatelyFast-Fabric-1.16.5+26.2.jar` | `fabric.mod.json` |
  | xaeros-minimap | `xaerominimap-fabric-26.2-26.6.1.jar` | `fabric.mod.json` |
  | entitytexturefeatures | `entity_texture_features-7.2.6-26.2-fabric.jar` | `fabric.mod.json` |
  | veinminer | `veinminer-fabric-2.12.1.jar` | `fabric.mod.json` |
  | continuity | `continuity-3.0.1+26.2.jar` | `fabric.mod.json` |
  | jade | `Jade-mc26.2-Fabric-26.2.11.jar` | `fabric.mod.json` |
  | simple-voice-chat | `voicechat-quilt-2.6.25+26.2.jar` | `quilt.mod.json`만 |

  모드 묶음은 Simple Voice Chat의 Fabric 판(`voicechat-fabric-2.6.24+26.2.jar`)을 이미 씁니다. 상위 100개 밖의 Quilt 전용 판 수는 Q0에서 셉니다.
- **Simple Voice Chat Quilt 판의 `quilt.mod.json`이 쓰는 항목:** `schema_version` 1, `quilt_loader`의 `group`·`id`·`version`·`provides`(`voicechat_api`)·`intermediate_mappings`(`net.fabricmc:intermediary`)·`metadata`·`entrypoints`(`main`, `client`, `modmenu`)·`depends`(`quilt_loader` `>=0.30.0`, `minecraft` `26.2.x`, `java` `>=25`)·`breaks`(`fabric-api` `<0.152.1+26.2` 등)·`jars`(중첩 Fabric API 모듈 8개, QSL 아님), 최상위 `mixin`, 다른 모드용 최상위 항목(`modmenu`, `multiconnect`).
  - 진입점 클래스는 `net.fabricmc.api.ModInitializer`·`ClientModInitializer`를 구현합니다.
  - 클래스가 참조하는 Quilt API는 `org.quiltmc.loader.api`의 `QuiltLoader`, `ModContainer`, `ModMetadata`, `Version`, `entrypoint.EntrypointContainer`, `minecraft.MinecraftQuiltLoader`뿐입니다.
- **`quilt.mod.json` 명세**(QuiltMC/rfcs `specification/0002-quilt.mod.json.md`): 위 항목 외에 `plugins`(loader plugin), `language_adapters`, `load_type`(`always`/`if_possible`/`if_required`), `repositories`, `access_widener`, `minecraft.environment`(`*`/`client`/`dedicated_server`), 의존 객체의 `optional`·`unless`·`reason`과 `any`/`all` 객체가 있습니다. 버전 지정에서 연산자 없는 `1.0.0`은 "1.0.0 이상 2.0.0 미만"입니다. Fabric의 같은 표기와 뜻이 같은지는 Q0에서 확인합니다.
- **라이선스:** Quilt Loader와 QSL 모두 Apache-2.0(GitHub 저장소 표시, QSL은 Modrinth도 같음). Quilt Loader에는 Fabric Loader에서 내려받은 코드가 있습니다. 코드를 옮기면 `THIRD_PARTY_NOTICES.md`에 더합니다.

## Q0 조사 결과 (2026-10-23)

- **Quilt Loader는 26.2에서 실제로 동작합니다.** meta의 서버 프로필(`0.31.0-beta.4`, 주 클래스 `KnotServer`)과 바닐라 26.2 서버 jar로, Simple Voice Chat의 Quilt 판(`voicechat-quilt-2.6.25+26.2.jar`)을 넣고 시작하면 "Loading Minecraft 26.2 with Quilt Loader 0.31.0-beta.4", 모드 11개(중첩 Fabric API 모듈 6개, MixinExtras, Quilt Loader 포함)를 로드하고, 모드가 초기화되고, EULA 확인까지 갑니다. 경고는 "Not remapping mods because intermediary is missing!" 하나입니다(26.x는 난독화가 없음).
- **Modrinth 전수 조사**(검색 API, `categories:quilt` + 26.1·26.1.1·26.1.2·26.2 + `project_type:mod`): 프로젝트 4,007개. 그중 Fabric 표시가 없는 Quilt 판(26.x)이 있는 프로젝트는 **124개, 판 382개**입니다(26.2 114개, 26.1 97개, 26.1.1 49개, 26.1.2 50개 프로젝트). 프로젝트마다 26.2와 26.1 계열의 가장 새 판을 받아 본 결과:

  | | 26.2 | 26.1 계열 |
  |---|---|---|
  | `quilt.mod.json`이 있는 jar | 100 (그중 `fabric.mod.json`도 있음 6) | 86 (6) |
  | 실제로는 `fabric.mod.json`만 있는 jar(표시만 Quilt) | 12 | 10 |
  | 그 밖(Paper 플러그인 1, 받지 못함 1) | 2 | 2 |
  | `quilt.mod.json` jar 중 클래스가 있는 모드 | **32** | **29** |
  | 클래스 없는 리소스 모드(데이터팩을 모드로 묶음) | 68 | 57 |
  | 그중 QSL `quilt_resource_loader`가 필요 | 66 | 56 |

- **쓰는 항목.** `quilt_loader`의 `group`·`id`·`version`·`depends`·`metadata`·`intermediate_mappings`가 거의 전부이고, `entrypoints`(26.2 24개), `jars`(2), `environment`(2), `breaks`·`provides`·`access_widener`(각 1). 최상위는 `mixin`(21), `minecraft`(10), `environment`, 다른 모드용 항목(`modmenu`, `multiconnect`, `custom`). **loader plugin(`plugins`), `load_type`, `language_adapters`를 쓰는 모드는 없습니다.**
- **진입점.** `main`·`client`(Fabric의 `ModInitializer`·`ClientModInitializer` 구현), `modmenu`, `fabric-datagen`, 그리고 `init`·`client_init`·`server_init`. `init`은 QSL의 진입점입니다. Quilt Loader 자신은 `main`·`client`·`server`만 부르므로(`game/minecraft/Hooks`), QSL이 없는 26.x에서는 `init`만 쓰는 모드(26.2: `chest-on-a-ghast`, `minecraft-sync`, `real-football`, `simple-elytra-bounce`, `log-cleaner-multiloader`)는 Quilt Loader에서도 초기화되지 않습니다. 그 가운데 4개는 `init`에 Fabric의 `ModInitializer`를 둡니다.
- **Quilt API 참조.** 클래스가 있는 모드 32개 중 10개가 `org.quiltmc.loader.api`만 씁니다(`QuiltLoader`, `ModContainer`, `ModMetadata`, `Version`, `entrypoint.EntrypointContainer`, `entrypoint.PreLaunchEntrypoint`, `minecraft.MinecraftQuiltLoader`). QSL을 쓰는 모드는 `anti-crash-fix` 하나(`org.quiltmc.qsl.base.api.entrypoint.ModInitializer`, `quilt_base` 의존)이고, QSL이 없으므로 Quilt Loader에서도 실행되지 않습니다.
- **API 구조.** `org.quiltmc.loader.api.QuiltLoader`의 정적 메서드는 `QuiltLoaderImpl.INSTANCE`에 맡깁니다(Fabric Loader API와 같은 모양). ADR-0016처럼 API 클래스는 Quilt Loader jar에서 가져오고 구현 자리를 Poly 모드 로더가 맡는 방식이 맞습니다(Q1에서 정함).
- **버전 지정의 뜻이 다릅니다.** `quilt.mod.json`에서 연산자 없는 `1.0.0`은 `^1.0.0`(같은 주 버전 안)입니다(`VersionConstraintImpl.parse`, `V1ModMetadataReader.readVersionSpecifier`: "Spec says that 1.0.0 is the same as ^1.0.0"). Fabric에서는 그 버전과 정확히 같아야 합니다. `26.2.x` 같은 `.x` 표기는 둘 다 범위입니다. 그래서 `quilt.mod.json`의 의존은 Quilt의 뜻으로 읽어야 합니다.
- **완료 기준에 쓰는 묶음의 크기.** 26.2에서 Quilt 전용으로 실행할 대상은 클래스가 있는 32개(그중 `init`만 쓰는 것과 QSL이 필요한 것은 Quilt에서도 실행되지 않음)와, QSL이 필요 없는 리소스 모드 2개입니다. 리소스 모드 66개는 QSL이 없어 Quilt Loader에서도 빠지는 경우(완료 기준 5)입니다.

## 바꿔야 할 곳 (조사 결과, 아직 고치지 않음)

| 위치 | 지금 하는 일 | Quilt에서 |
|---|---|---|
| `poly-runtime-impl` `discovery/ModFileKind` | 메타데이터 파일로 jar의 로더를 정함. `quilt.mod.json` 없음 | 새 종류. 두 파일을 다 든 jar를 어느 쪽으로 볼지 Q1에서 정함 |
| `discovery/ModFileScanner` | `ModFileKind` 순서대로 찾아 첫 것을 `found`, 나머지를 `alsoFor`로 | 같은 틀에 더함 |
| `discovery/NestedJarExtractor` | 중첩 jar가 `fabric.mod.json`이 없으면 `POLY-DISCOVERY-002` | `quilt_loader.jars`(문자열 배열)를 읽음 |
| `PolyRuntime`, `discovery/ForeignModResolver` | `POLY-DISCOVERY-002` 보고, `onlyFabric`, `ServiceLoader`로 `ModLoaderBridge`를 찾음 | Quilt 모드를 같은 판정 흐름에 넣음 |
| `poly-bridge-api` `ModLoaderBridge`, `BridgedDependency` | 브리지 하나가 메타데이터 경로 하나(`metadataPath()`) | 같은 브리지가 두 형식을 읽을지, 새 브리지를 둘지 Q1에서 정함 |
| `poly-bridge-fabric` `FabricBridge`, `loader/FabricModMetadata`, `FabricModDependency`, `FabricModContainer`, `FabricLoaderRuntime`, `mixin/MixinCompatibility`, `MixinConfigEntry`, `ClassTweakerFile` | `fabric.mod.json`의 진단, 의존, 진입점, Mixin, access widener | `quilt.mod.json`을 같은 내부 모델로 옮김. `org.quiltmc.loader.api`를 Fabric Loader API처럼 제공 |
| `poly-bridge-forge` `ForeignModIndex`, `ForgeModRequirements`; `poly-lifecycle-api` `LoadedMods` | 로더를 넘는 의존에서 `fabric.mod.json`의 ID를 봄 | Quilt 모드 ID와 `provides`도 봄 |
| `build-logic` `ModCorpusTask` | 로더 목록 `fabric`, `neoforge`, `forge` | Quilt 전용 판을 고르는 묶음(`corpusQuilt`, 가칭) |

## 단계

| # | 일 | 검증(먼저 만듦) | 상태 |
|---|---|---|---|
| Q0 | 조사 | 26.1·26.1.2·26.2 Modrinth 전체에서 Quilt 전용 판 수와 목록. 그 jar들이 쓰는 `quilt.mod.json` 항목과 `org.quiltmc.*` 참조(QSL 참조가 있는지). Quilt Loader 0.30.x·0.31.x로 Quilt 묶음이 실제로 26.x에서 시작하는지. 버전 지정 뜻의 차이. loader plugin을 쓰는 26.x 모드가 있는지 | 완료 (2026-10-23, "Q0 조사 결과") |
| Q1 | 결정 | 새 ADR(번호는 [`docs/adr/README.md`](../adr/README.md)의 다음 번호): Quilt 모드를 Fabric 브리지에서 읽는 방식, 두 메타데이터를 다 든 jar의 판정, Quilt Loader API를 ADR-0016처럼 Quilt Loader jar의 API 클래스로 제공할지 다시 구현할지 | 완료 (2026-10-24, [ADR-0027](../adr/0027-quilt-mods-on-poly-mod-loader.md) 제안) |
| Q2 | 메타데이터 | `quilt.mod.json` 읽기 단위 테스트(명세의 모든 항목, 잘못된 값의 진단). 새 진단은 새 `POLY-COMPAT-` 번호 || 완료(2026-10-24): `QuiltModJsonReader`·`QuiltDependencyReader`·`QuiltVersionSpecifier`와 단위 테스트, 새 코드 `300..310` |
| Q3 | 테스트 모드 | `quilt_hello`(새로 만듦): `quilt.mod.json`만, `org.quiltmc.loader.api` 호출, 중첩 Fabric API 모듈. `compatibilityMatrix`에 Quilt + Fabric + Poly 혼합 || 완료(2026-10-24, 26.2): 테스트 모드 6개(`quilt-hello` 등), `compatibilityMatrix`의 `quiltOnly`·`quiltFabricPoly`·`quiltBlocked` |
| Q4 | Quilt Loader API | 실제 모드가 쓰는 클래스부터(`QuiltLoader`, `MinecraftQuiltLoader`, `ModContainer`, `ModMetadata`, `Version`, `EntrypointContainer`). 쓰지 않는 내부 클래스는 Fabric의 `111`처럼 막고 알림 || 완료(2026-10-24): Quilt Loader 0.31.0-beta.4 공개 API를 묶고 `QuiltLoaderImpl` 등 대역 3개, 진입점·Mixin·access widener·환경별 클래스 제거, `QuiltLoaderApiLinkTest` |
| Q5 | 실제 모드 묶음: 게임 테스트 서버 | `corpusQuilt`(가칭)가 26.2 Quilt 묶음으로 월드 생성·GameTest·저장 | 완료(2026-10-24): `modCorpusQuilt`(Quilt 전용 판 전부, 26.2 114개 + 필수 3개), `corpusQuilt` 통과(외부 모드 135개, 저장된 월드에 Quilt 데이터 팩). 아래 "Q5~Q12 결과" |
| Q6 | 클라이언트 | `corpusQuiltClient`(가칭, 창이 열림) | 완료(2026-10-24): 세 버전 통과(26.2 외부 모드 155개 중 88개 Quilt, 26.1 131개, 26.1.2 88개). NeoFullbright(모든 로더에서 Mixin이 거부)는 클라이언트 실행에서 뺌. 게임 없이 `corpusAssessmentQuilt`·`mixinAuditQuiltClient` 통과 |
| Q7 | 전용 서버 | `corpusQuiltServer`(가칭): EULA 확인까지 | 완료(2026-10-24): 26.2 133개, 26.1 116개, 26.1.2 71개 |
| Q8 | 설치한 프로필·서버 | `launcherProfileTest`·`serverInstallTest`에 Quilt 모드 하나 | 서버 완료(2026-10-24, `serverInstallTest` 26.2·26.1·26.1.2에 `quilt_hello`). 런처 프로필은 `launcherProfileTest`에 `quilt_hello`를 넣어 통과(2026-10-24) |
| Q9 | 26.1 계열 | Q5~Q8을 `-PpolyMinecraft=26.1`(필요하면 26.1.2 묶음)로 | 헤드리스 완료(2026-10-24): 26.1(97개 + 1)·26.1.2(50개 + 1) 묶음으로 `corpusQuilt`·`corpusQuiltServer`·`corpusQuiltForge`·`serverInstallTest`, 26.1에서 `quiltOnly`·`quiltFabricPoly`·`quiltBlocked` |
| Q10 | 빠지는 모드 점검 | 빠지는 모드마다 실제 Quilt Loader에서도 빠지는지 확인해 `COMPATIBILITY.md`에 적음 | 완료(2026-10-24, 서버): `quiltLoaderReference`로 실제 Quilt Loader 0.31.0-beta.4와 비교. 26.2 114개 중 113개, 26.1 97개 중 96개, 26.1.2 50개 중 48개 같고, 다른 것(Armor Visibility, Yeth's TPA)은 Quilt Loader에서도 코드가 실행되지 않거나 실행되면 실패. `124` 과장 문제 해결 |
| Q11 | 성능 | `startupBenchmarkQuilt`·`tickBenchmarkQuilt`(가칭), `PERFORMANCE.md` | [`PERFORMANCE.md`](../PERFORMANCE.md) 측정 7 |
| Q12 | 판정 | `COMPATIBILITY.md`의 "Quilt 완전 지원 점검", 상태 `Verified`, 웹사이트·README | 완료(2026-10-24): 여섯 기준을 세 버전에서 확인, `COMPATIBILITY.md` 상태 `Verified`, README 갱신. 웹사이트는 릴리스 때 |

창이 열리는 작업(Q6, Q8)은 헤드리스 검증을 마친 뒤 하나씩 실행합니다(`AGENTS.md`).

### Q5 첫 실제 모드: Simple Voice Chat (2026-10-24)

Modrinth의 Quilt 전용 판 `voicechat-quilt-2.6.25+26.2.jar`(버전 `GtDHYJND`, SHA-1 `7a8150835a4da8d199aab46e79e39bd092498d67`, `quilt.mod.json`만, 진입점 `main`·`client`·`modmenu`, 중첩 Fabric API 모듈 8개, 최상위 `mixin`)를 그 jar 하나만 든 폴더로 `.\gradlew.bat :poly-bootstrap:modProbe -PmodProbeDir=<폴더>`(게임 테스트 서버, 26.2)에서 실행했습니다. jar는 저장소에 넣지 않았습니다.

- 판정: `voicechat`은 Quilt 브리지로 로드(`309` intermediate_mappings, `304` `modmenu`, `131` Mixin 설정 1개, `113` 알림들, `124` 아래 둘). 중첩 Fabric API 모듈 6개는 Fabric 브리지로 로드, `fabric-key-mapping-api-v1`·`fabric-rendering-v1`은 클라이언트 전용이라 `108`로 빠짐(Quilt Loader도 전용 서버에서는 같음). `Foreign mods: 7 loaded, 2 not loaded`, `Loaded mods per loader: 0 Poly, 6 Fabric, 1 Quilt, 0 Forge`.
- Mixin: `Mixin: 6 configuration(s) from 5 Fabric mod(s) and 1 Quilt mod(s) for DEDICATED_SERVER`. `mixin.debug.verbose`로 한 번 더 실행해 `Mixing PlayerManagerMixin from voicechat.mixins.json into net.minecraft.server.players.PlayerList`를 확인했습니다.
- `main` 실행(`[voicechat] Compatibility version 20`, `Loaded 0 plugin(s)`), 서버 시작 이벤트로 음성 서버 시작(`Voice chat server started at port …`, UDP 포트를 엶), `All 1 required tests passed`, `All dimensions are saved`. 오류·예외 없음.
- 남은 것: 클라이언트 전용 중첩 모듈 때문에 붙는 `124`("works only in part")는 Fabric 모드에도 같은 기존 규칙입니다. 전용 서버에서는 Fabric/Quilt Loader에서도 그 모듈이 빠지므로 이 알림이 과장인지는 Q10에서 정합니다. 묶음 작업(`corpusQuilt`)은 아직 없습니다.

### Q5~Q12 결과 (2026-10-24)

판정 표와 빠지는 모드마다의 이유는 [`COMPATIBILITY.md`](../COMPATIBILITY.md) "Quilt 완전 지원 점검", 결정은 [ADR-0027](../adr/0027-quilt-mods-on-poly-mod-loader.md) "Q5–Q11 구현에서 정한 것"에 있습니다.

| 기준 | 상태 | 근거 |
|---|---|---|
| 1. 클라이언트 | 통과(세 버전) | `corpusQuiltClient`(창). 게임 없이: `corpusAssessmentQuilt`, `mixinAuditQuiltClient` |
| 2. 게임 테스트 서버·전용 서버 | 통과 | `corpusQuilt`, `corpusQuiltServer`(세 버전) |
| 3. 설치한 프로필·서버 | 통과 | `serverInstallTest`(세 버전), `launcherProfileTest`(창) |
| 4. 거부하는 Quilt Loader API·항목 없음 | 통과 | 묶음에 `301`·`302`·`306`, `org.quiltmc.loader`의 `111` 없음 |
| 5. 빠지는 모드는 Quilt Loader에서도 | 통과(서버에서 실제 Quilt Loader로) | `quiltLoaderReference` |
| 6. 성능 | 측정 | `startupBenchmarkQuilt`, `tickBenchmarkQuilt` |

Quilt 모드와 Forge 모드를 한 게임에서 실행하는 것도 확인했습니다(`corpusQuiltForge`, 세 버전). 찾아 고친 것: Quilt용 Fabric jar의 `quilt_loader` 의존(Fabric 브리지의 내장 모드), 서버의 클라이언트 전용 중첩 모듈에 붙던 `124`, Gradle 클래스 경로 jar 때문에 Forge와 함께일 때 FML이 바닐라 게임을 찾던 문제, Windows가 거부하는 파일 이름.

## 범위 밖

- **Quilt Loader 자체를 Poly 모드 로더 위에서 실행.** Fabric 브리지도 Fabric Loader를 시작하지 않고, Fabric Loader jar의 공개 API 클래스와 그 API가 가리키는 내부 클래스만 담아 제공합니다(ADR-0016, `poly-bridge-fabric/build.gradle.kts`). Quilt도 같은 방식을 기본으로 보고 Q1에서 정합니다. `org.quiltmc.loader.api`가 가리키는 Quilt Loader 내부 클래스의 범위는 Q0에서 확인합니다.
- **26.x용이 없는 QSL·QFAPI 대응.** 나오면 이 계획을 고칩니다.
- **Quilt API를 Native API로 다시 구현.** `AGENTS.md` 원칙 6과 같은 이유입니다.

## 위험

- **대상 모드가 적습니다.** 상위 100개 중 Quilt 전용 판은 1개입니다. 묶음이 작으면 검증의 폭도 좁습니다. Q0의 전체 수로 묶음 크기를 정하고, 작으면 그 사실을 판정에 적습니다.
- **Quilt Loader 26.x 판이 베타입니다.** `0.31.0-beta.4`가 최신이고, GitHub `QuiltMC/quilt-loader`의 `develop` 브랜치 마지막 커밋은 2026-09-04입니다(2026-10-23 확인). API가 바뀌거나 멈출 수 있습니다.
- **버전 지정의 뜻 차이.** 같은 문자열이 `fabric.mod.json`과 `quilt.mod.json`에서 다른 범위일 수 있습니다. 섞어 판정하면 잘못 막거나 잘못 받습니다.
- **loader plugin.** Quilt Loader 내부에서 실행되는 코드라 제공하기 어렵습니다. 쓰는 모드가 있으면 막고 이유를 알립니다(새 `POLY-COMPAT-` 번호).
- **오류 코드.** 기존 `POLY-DISCOVERY-002`와 `POLY-COMPAT-1xx`의 의미는 바꾸지 않습니다.
