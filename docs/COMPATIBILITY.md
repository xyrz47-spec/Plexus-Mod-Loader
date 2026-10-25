# 호환성 — Fabric / NeoForge / Forge (Phase 0 실험 결과)

- 기준일: 2026-10-19
- 대상: Minecraft 26.2, Poly 모드 로더 0.1.0-alpha.2
- 관련: 설계서 §13, §15.4, §21-10

## 현재 지원 상태 (2026-10-24, Quilt·NeoForge 완전 지원)

| 로더 | 상태 | 근거 |
|---|---|---|
| Poly 모드 로더 Native | **Verified** | `nativeApiGameTests` 52개, 클라이언트·서버·월드 저장 검증 |
| Fabric (진입점 `preLaunch`·`main`·`client`·`server`와 `::` 값, Fabric Loader API, 언어 어댑터, 중첩 jar, 다른 모드 의존) | **Verified** | 진입점을 Fabric Loader와 같은 게임 내 지점에서 실행(ADR-0020). Native·Fabric 모드 사이 의존과 별칭(`provides`)을 `dependencyCrossLoader`로 검증 |
| Fabric (Mixin·MixinExtras, class tweaker의 `accessible`·`extendable`·`mutable`·`inject-interface`·`extend-enum` 줄, `@Environment`) | **Verified** | Mixin 0.8.7(FabricMC 0.17.4)과 MixinExtras 0.5.5를 Fabric Loader와 같은 순서로 적용(ADR-0018, ADR-0020). `fabricMixin`·`fabricClassTweaker` 시나리오. 실제 모드 묶음(26.2): 클라이언트에서 140개가 월드에 들어가 200틱을 보내고 저장 후 종료까지(`corpusFabricClient`), 전용 서버 시작 100개, 게임 테스트 서버 98개. 26.1도 같은 검증(아래) |
| Fabric (Fabric Loader 내부 클래스) | **Partial** | 실제 모드가 쓰는 것만 제공: `FabricLauncherBase.getLauncher()`·`FabricLauncher`(`addToClassPath` 등), `FabricLoaderImpl.getGameProvider()`·`GameProvider`의 게임 설명 메서드. 그 밖의 내부 클래스와 멤버는 링크 검사 `111`로 막힘(Fabric 자체 실행기 `Knot` 등). 그 밖의 기능별 판정은 `100`·`107`·`108`·`125`·`126`·`132`, 링크 검사 `112`·`118`·`119`·`128`·`135` |
| Quilt (`quilt.mod.json`) | **Verified** | 2026-10-24, ADR-0027. 지원하는 세 버전(26.2, 26.1, 26.1.2)에서 완료 기준 6개를 확인함(아래 "Quilt 완전 지원 점검"). 처음 실험 지원(26.2만): `quilt.mod.json`을 Quilt Loader의 의미로 읽음(버전만 쓰면 `^`, `optional`·any/all 묶음·`unless`, `provides`), `quilt_loader.jars`의 중첩 모드(필요할 때만 로드하는 모드는 `307`), Quilt Loader 0.31.0-beta.4 공개 API(`org.quiltmc.loader.api`, 묶지 않은 내부 클래스는 `111`), 진입점 `main`·`client`·`server`·`pre_launch`, Quilt 모드의 Mixin·access widener·환경별 클래스 제거. QSL 진입점은 실행하지 않음(`303`, QSL은 26.x 판이 없어 Quilt Loader도 같음), QSL이 필요한 모드는 `114`+`308`로 뺌. 근거: `compatibilityMatrix`의 `quiltOnly`·`quiltFabricPoly`·`quiltBlocked`(Quilt·Fabric·Poly 모드 함께), 실제 모드 Simple Voice Chat Quilt 빌드(`voicechat-quilt-2.6.25+26.2`)가 게임 테스트 서버에서 중첩 Fabric API 모듈 6개와 함께 로드되어 Mixin 적용·음성 서버 시작·GameTest·저장(`modProbe`). Quilt Loader와 다르게 정한 것: 두 메타데이터가 다 있는 jar는 Fabric 모드로 실행(`310`), Maven `group:id` 의존은 ID로만 맞춤. 2026-10-24 실제 Quilt 묶음(Modrinth의 Quilt 전용 판 전부, 26.2 114개·26.1 97개·26.1.2 50개): 세 버전에서 게임 테스트 서버(`corpusQuilt`)·전용 서버(`corpusQuiltServer`)·Forge 모드와 함께(`corpusQuiltForge`)·설치한 서버(`serverInstallTest`) 통과, 빠지는 모드는 실제 Quilt Loader 0.31.0-beta.4의 전용 서버와 비교해 Quilt Loader에서도 빠지거나 실행되지 않음(`quiltLoaderReference`, 아래 "Quilt 완전 지원 점검"). 클라이언트(`corpusQuiltClient`)는 세 버전, 설치한 런처 프로필(`launcherProfileTest`)은 26.2에서 통과 |
| NeoForge (`META-INF/neoforge.mods.toml`) | **Verified** (2026-10-24, 26.1·26.1.2·26.2). Fabric·Quilt 모드와 한 게임에서도 **Verified**(2026-10-25, 26.1·26.1.2·26.2): 게임 테스트 서버·전용 서버·클라이언트 Mixin 검사(헤드리스), 창 없는 접속(놀이 단계까지), 클라이언트 창 실행(Fabric·NeoForge 묶음으로 저장된 월드 입장·200틱·저장·종료). Quilt 모드와 함께는 게임 테스트 서버(`corpusQuiltNeoForge`)로 확인. 한계: MinecraftForge 모드와 한 게임에서는 실행하지 않음(`221`), NeoForge 패치와 맞지 않는 Fabric 주입은 실행하지 않음(`138`), 호스트 설정을 고르기 전에 로드된 클래스로의 NeoForge mixin(`220`), 수신 객체를 바닐라에 맞춘 주입(`222`), 겹친 default는 NeoForge 쪽(`223`). 아래 "NeoForge와 Fabric·Quilt 모드를 한 게임에서". MinecraftForge 모드와 한 게임에서는 아님(`221`) | ADR-0026(채택): Poly 모드 로더가 NeoForge 자신의 FML 11을 실행하고(26.1 `26.1.0.19-beta`, 26.1.1 `26.1.1.15-beta`, 26.1.2 `26.1.2.122`, 26.2 `26.2.0.93`; 26.1·26.1.1은 NeoForge에 베타 빌드만 있음), 기본값으로 켜짐(`-Dpoly.neoforge=off`로 끔). 2026-10-24: 실제 모드 묶음이 게임 테스트 서버에서 월드 생성·GameTest·저장(`corpusNeoForge`, 26.2 38개·26.1.2 40개·26.1 35개 중 31개 jar(테스트 모드 포함), 게임 레이어 모듈 수가 NeoForge 자신의 설치와 같음), 전용 서버에서 EULA 확인까지(`corpusNeoForgeServer`), 설치한 서버에서 NeoForge 모드로 게임 테스트 서버·전용 서버(`serverInstallTest`, 네 버전). 빠지는 모드는 NeoForge 자신의 서버에서도 멈추는 것뿐(아래 "NeoForge 완전 지원 점검"). FML이 거부할 모드는 FML 시작 전에 그 모드만 뺌(`217`). FML의 `ModList`가 Poly 모드에 답함(`218`), 로그·타이틀 화면에 NeoForge 모드 수가 따로 나옴. Fabric·Quilt 모드와 함께 넣으면 하나의 Mixin이 모두를 맡아 함께 실행(ADR-0026 30~40, 아래 표). 클라이언트에서 저장된 월드 입장·200틱·저장·종료(`corpusNeoForgeClient`, NeoForge 모드 26.2 54개·26.1.2 55개·26.1 36개, `neoForgeClient`), 설치한 런처 프로필에서도 같음(`launcherProfileNeoForgeTest`, 26.2·26.1.2) |
| MinecraftForge (`META-INF/mods.toml`) | **Verified** | Poly 모드 로더가 Forge(26.1 62.0.9, 26.1.2 64.1.5, 26.2 65.1.5)와 FML을 실행하고 Forge 모드를 FML에 넘김(ADR-0021). 지원하는 세 버전에서 완료 기준 6개를 확인함(아래 "Forge 완전 지원 점검"). 26.2 게임 테스트 서버에서 실제 모드 묶음 38개 중 32개 실행, 월드 생성·GameTest·저장(`corpusForge`), 서버의 클라이언트 전용 모드 6개는 `209`로 뺌(빠지는 이유마다 실제 Forge와 비교: [`plans/FORGE_SUPPORT.md`](plans/FORGE_SUPPORT.md) "B5"). access transformer·Forge Mixin은 `forgeGameTest`. Fabric 모드와 함께: `corpusFabricForge`에서 Fabric 모드 90개(+확인용 테스트 모드 `fabric-host-patch-probe`)와 함께 월드 생성·GameTest·저장(G8), Forge 패치와 맞지 않는 Fabric mixin은 `138`(2026-10-23: 실행되지 않는 주입 28곳 → 25곳). Forge 패치가 더하거나 바꾼 멤버와 겹쳐 통째로 빠지던 Fabric API mixin 4개는 이제 적용됨(네트워킹 접근자, `fabric:remove` 태그 제거, 데이터 생성 발전 과제 빌더; `212`·`213`, `corpusFabricForge`의 `fabric-host-patch-probe`). Fabric API의 `FabricDefaultAttributeRegistry.MODIFY`는 Forge가 필드 타입을 바꿔 Forge와 함께 동작하지 않음(부르면 `ClassCastException` 대신 `214`). 실제 Fabric API와 Forge(`forgeFabricApiGameTest`, 바닐라와 같은 줄을 `fabricApiGameTest`로 비교): `PlayerBlockBreakEvents` BEFORE·AFTER·CANCELED(생존·창작, 블록 엔티티 포함), 조건이 맞지 않는 Fabric 리소스 조건 데이터 건너뛰기, 조합 남는 아이템(Fabric·Forge 인터페이스의 같은 default 충돌은 Forge 쪽 default로, 재정의는 그대로)이 동작. 레시피 남는 아이템 Redirect 4곳은 실행되지 않지만 Forge 패치가 같은 자리에서 스택 기반 `getCraftingRemainder`를 불러 Fabric 아이템의 `getCraftingRemainder(ItemStack)` 재정의가 쓰임(바이트코드로 확인, 게임 안에서 Fabric 아이템으로는 확인 안 함). 아직 실행되지 않는 것: Fabric API 유체 수영·익사·보트(`fabric-content-registries-v0` 5곳), 수면 이벤트의 침대 방향·점유 상태(Forge는 `BlockState.getBedDirection`·`setBedOccupied`), `ItemStack.useOn` wrap(Forge는 `onItemUse`), 인챈트 테이블의 `canBeEnchantedWith`(Forge는 `ItemStack.canApplyAtEnchantingTable`), `Language.loadDefault` Redirect, data generation 2곳, Lithium 3곳(`Level.setBlock` 호퍼 갱신은 Forge `markAndNotifyBlock`으로 옮겨짐), Architectury 스포너 4곳, Balm 작물 1곳, 두 로더용 빌드가 함께 있으면 Forge 빌드만(`139`). 서로의 모드 목록: FML의 `ModList.getModFileById`·`isLoaded`가 Fabric 모드·Poly 모드를, Fabric의 `isModLoaded`가 Forge 모드를 봄(ADR-0025, `forgeFabricGameTest`; `corpusFabricForge`에서 Fabric 모드 90개를 FML이 답함, 2026-10-23), Poly 모드의 `ModContext.mods()`가 모든 로더의 모드를 봄. `getModContainerById`는 Forge 모드만. Physics Mod(Forge)와 Sodium(Fabric)을 함께 넣으면 블록 파편과 래그돌이 Physics Mod만 넣었을 때처럼 그려짐(`physicsProbeSodiumClient` 스크린숏, 26.2). 클라이언트: `forgeClient`·`corpusForgeClient`에서 저장된 월드에 들어가 200틱 후 저장·종료(G7). 26.1(Forge 62.0.9): 실제 묶음 36개 중 18개로 월드 생성·GameTest·저장(`corpusForge`; 빠지는 18개 중 12개는 실제 Forge도 모드 로딩을 멈추는 버전 범위·Mixin 설정, 6개는 서버의 클라이언트 전용), 26.1.2(Forge 64.1.5): 26.1.2 묶음 34개 중 28개, 클라이언트(`corpusForgeClient`), 전용 서버 EULA 확인까지(`corpusForgeServer`). 실제 Forge에서도 실패하는 빌드 6개는 묶음 실행에서 뺌(`brokenUnderForge`). 설치한 서버: 26.2·26.1.2(`serverInstallTest`), 설치한 런처 프로필: 26.2·26.1.2(`launcherProfileForgeTest`) |

**Minecraft 26.3 (2026-10-25, 1.2.0부터 지원).** 위 표의 상태는 26.1·26.1.2·26.2의 실제 모드 묶음 결과입니다. 26.3은 실제 모드 묶음(`mod-corpus-26.3.json`)을 아직 돌리지 않았고, 다음만 확인했습니다: `nativeApiGameTests` 62개, `worldCreateSaveTest`, `serverBootSmokeTest`, `compatibilityMatrix`(Fabric·Quilt·NeoForge 테스트 모드, 로더를 넘는 의존과 차단 연쇄), `neoForgeWorldTest`(NeoForge 26.3.0.98), `forgeWorldTest`(Forge 26.3-66.1.0), 클라이언트 `clientWorldReloadTest`(모드 셰이더·유니폼·키 바인딩). 그래서 26.3의 Fabric·Quilt·NeoForge·Forge는 실제 모드로는 **Untested**이고, 테스트 모드로만 확인했습니다. 진행은 `plans/MINECRAFT_26_3.md`에 있습니다.

### 크래시 없이 판정하는 규칙

Poly 모드 로더는 모든 모드에 대해 게임 코드가 돌기 전에 둘 중 하나를 정합니다. 로드하거나, 이유(오류 코드)와 함께 제외합니다.

| 상황 | 결과 |
|---|---|
| 외부 모드의 필수 의존성이 없음·차단됨·버전이 맞지 않음(`minecraft`, `java` 포함) | 그 모드를 제외합니다. 의존하는 모드도 연쇄로 제외되고, 각각 "설치됐지만 차단됨: …"처럼 모든 이유를 받습니다(`POLY-COMPAT-114`) |
| 외부 모드가 `breaks`로 선언한 모드가 있음 | 제외(`115`) |
| `conflicts` | 경고만(`116`) |
| 같은 ID를 쓰는 모드가 또 있음 | 외부 모드를 제외(`117`) |
| 클래스가 이 게임 버전에 없는 메서드·클래스를 참조함 | 제외(`112`). 참조만 검사하고 실제 실행 경로는 분석하지 않습니다 |
| Fabric Loader 런타임(`FabricLoader` 등)을 씀 | 제외(`111`). Poly 모드 로더는 `net.fabricmc.api` 인터페이스만 제공합니다 |
| 메타데이터 형식이 틀림, 깨진 jar, FML 라이브러리 | 제외(`100`·`200`, `POLY-DISCOVERY-003`, `POLY-COMPAT-205`). 다른 모드는 계속 판정합니다 |
| Poly 모드가 차단된 외부 모드에 의존함 | 게임 코드가 돌기 전에 시작을 멈추고 이유를 알립니다(`POLY-DEPENDENCY-009`). Native의 다른 의존성 오류와 같은 정책입니다 |

중첩 jar는 풀어서 각각 하나의 모드로 판정합니다. 바깥 jar가 로드될 때만 안쪽도 로드되고(`121`), 같은 모드의 사본은 하나만 씁니다(`122`)([ADR-0015](adr/0015-fabric-nested-jars.md)).

로드되는 모드는 같은 로더 안에서 의존 대상의 엔트리포인트가 먼저 돕니다. Fabric 모드는 Fabric Loader와 같은 게임 내 지점(Minecraft bootstrap 뒤)에서 초기화하고, Poly 모드는 그보다 먼저 초기화합니다. 그래서 Poly 모드가 Fabric 모드에 의존하면 로드 순서와 클래스는 보장하지만, 그 Fabric 모드의 초기화가 먼저 끝나는 것은 보장하지 않습니다. 자세한 결정은 [ADR-0014](adr/0014-cross-loader-dependencies-and-link-check.md)와 [ADR-0020](adr/0020-fabric-mixin-and-runtime-fidelity.md)에 있습니다.

## 실험 3 — 실제 인기 모드 124개 (2026-10-21)

| 항목 | 값 |
|---|---|
| 명령 | `.\gradlew.bat :poly-bootstrap:compatibilityCorpus` (네트워크 필요) |
| 모드 목록 | [`poly-bootstrap/compat/mod-corpus-26.2.json`](../poly-bootstrap/compat/mod-corpus-26.2.json). Modrinth에서 26.2용으로 가장 많이 받은 Fabric·NeoForge·Forge 모드 각 40개와 그 필수 의존 모드를 담았습니다. jar 125개이고, 3개는 26.2 버전이 없습니다. 버전과 SHA-512를 고정하며, jar는 `build/`에만 받고 커밋하지 않습니다 |
| 판정 | 클라이언트·서버 각각: 모드 하나씩, 그리고 전부 함께(`corpusAssessment`, 실제 게임 jar와 라이브러리로 링크 검사) |
| 실행 | 게임 테스트 서버에서 로더별 묶음 3개와 전부+개발용 모드 묶음 1개(`corpusFabric`, `corpusNeoForge`, `corpusForge`, `corpusAll`) |

결과:

| | Fabric | NeoForge | Forge |
|---|---|---|---|
| 외부 모드 수 | 52 | 36 | 35 |
| 로드 가능 | 0 | 0 | 0 |
| 판정 중 예외·게임 크래시 | 0 | 0 | 0 |

막는 이유(모드 수, 한 모드에 여러 개일 수 있음): Mixin 45(Fabric `101`)+34(FML `202`), FML 런타임 71(`201`), 중첩 jar 26(`103`), class tweaker 26(`102`), Access Transformer 5(`203`), 기본이 아닌 언어 어댑터 5(`106`), `preLaunch` 3(`105`), Fabric Loader 런타임 1(`111`, `placeholder-api`), 차단된 의존성 1(`114`, `mezz_config` → `fabric-api`). 전용 서버에서는 클라이언트 전용 모드 19개가 `108`로 더 막힙니다.

- `placeholder-api`는 링크 검사 이전에는 로드됐습니다. Fabric Loader 런타임을 쓰므로, 다른 모드가 부르는 순간 게임 도중에 실패할 수 있었습니다. 지금은 시작 전에 제외됩니다.
- `kotlinforforge`, `mezz_config`(NeoForge 판)는 이전에 "모드가 아님"으로 보고됐습니다. 지금은 FML 라이브러리(`205`)로 보고됩니다.
- 실제 인기 모드를 실행하려면 Mixin 서비스, 중첩 jar, Fabric Loader API 구현(Fabric), FML 런타임(NeoForge·Forge)이 필요합니다. 아직 없습니다. 이 실험이 보여 주는 것은 "실행 불가능한 조합을 크래시 없이 정확히 거른다"는 점입니다. "인기 모드가 돈다"는 뜻이 아닙니다.

### 중첩 jar 지원 뒤 (2026-10-21, ADR-0015)

| | 값 |
|---|---|
| 새로 판정한 중첩 모드 | 142 |
| 중첩 모드를 막는 이유 | Mixin 77, 바깥 jar 차단 55(`121`), class tweaker 54, 차단된 의존성 12(`114`), 다른 사본 사용 4(`122`), 언어 어댑터 2 |
| 직접 설치한 모드 중 로드 가능 | 1: `fabric-api` 0.161.0. 단, 하위 모듈 44개 중 `fabric-api-base` 하나만 로드되므로 `POLY-COMPAT-124`(일부만 동작) |
| 막히는 이유가 바뀐 모드 | `fabric-language-kotlin`: 중첩 jar(`103`) 대신 Fabric Loader `LanguageAdapter` API(`111`). `mezz_config`: `fabric-api` 차단(`114`) 대신 Fabric API 모듈 클래스 없음(`111`) |
| 판정 중 예외·게임 크래시 | 0 |

### Fabric Loader API 제공 뒤 (2026-10-21, ADR-0016)

| | 값 |
|---|---|
| 로드 가능한 외부 모드 | 17: `placeholder-api` 3.1.0-beta.1, `fabric-language-kotlin` 1.14.2와 그 Kotlin 라이브러리 13개, `fabric-api` 0.161.0(하위 모듈 1/44, `124`), `fabric-api-base` |
| 게임 테스트 서버 | 위 17개를 올린 채 정상 시작(`corpusFabric`, `corpusAll`) |
| 남은 주된 차단 | Mixin(Fabric 45, FML 34), FML 런타임 71, class tweaker 26, Fabric API 하위 모듈 클래스(`111`, 예: `mezz_config`) |
| 판정 중 예외·게임 크래시 | 0 |

### Fabric class tweaker 적용 뒤 (2026-10-21, ADR-0018)

명령: `.\gradlew.bat :poly-bootstrap:compatibilityCorpus`. 클라이언트·서버 판정(`corpusAssessment`)과 게임 테스트 서버 4개 묶음이 모두 통과했습니다.

| | 이전 | 이후 |
|---|---|---|
| class tweaker로 막힘(`102`) | 직접 설치 26, 중첩 54 | 0 (`102` 폐기) |
| class tweaker 파일 | — | 80개 모두 읽힘. 형식 오류(`125`)·다른 이름 공간(`126`)·26.2에 없는 대상(`128`) 0 |
| interface injection으로 막힘(`127`) | — | 직접 2, 중첩 40. 모두 Mixin(`101`)도 씀 |
| class tweaker가 있는 모드의 남은 차단 | — | Mixin 74. 나머지: `cloth-config`는 `::` 진입점(`106`), `veinminer`는 로드되지 않은 Fabric API 클래스(`111`), `pridelib`(중첩)은 바깥 jar·의존 모드 차단(`121`·`114`, 서버는 `108`), 사본 하나는 `122` |
| 함께 로드 가능한 외부 모드 | 17 (직접 3) | 18 (직접 3). 새로 로드되는 것: `fabric-api` 안의 `fabric-transitive-access-wideners-v1` |
| 게임 테스트 서버 | `corpusFabric` 17 로드·157 차단, `corpusAll` 18·248 | `corpusFabric` 18·156, `corpusAll` 19·247. 접근 확대 433개 적용(거부 0), 정상 시작 |
| 판정 중 예외·게임 크래시 | 0 | 0 |

- 실제 모드의 class tweaker는 모두 `official` 이름 공간입니다. 머리글은 `accessWidener v1` 8, `v2` 12, `classTweaker v1` 51, `v2` 2개입니다. 단어 사이에 탭이나 여러 칸 공백을 쓴 파일도 있습니다.
- class tweaker를 쓰는 모드 대부분이 Mixin도 써서, 로드 가능한 모드 수는 1개만 늘었습니다. 이 결과가 보여 주는 것은 두 가지입니다. 실제 파일 80개를 Fabric과 같은 문법으로 오류 없이 읽고 26.2 jar와 대조할 수 있다는 점, 그리고 접근 확대 433개를 적용해도 서버가 정상 시작한다는 점입니다. 그 모드들의 기능이 동작한다는 뜻은 아닙니다.

### Mixin과 Fabric 런타임 충실도 뒤 (2026-10-22, ADR-0020)

명령: `.\gradlew.bat :poly-bootstrap:compatibilityCorpus`. 클라이언트·서버 판정(`corpusAssessment`)과 실행 묶음 5개(`corpusFabric`, `corpusFabricServer`, `corpusNeoForge`, `corpusForge`, `corpusAll`)가 모두 통과했습니다. 같은 날 `compatibilityMatrix`(`fabricMixin` 포함)와 `nativeApiGameTests`도 통과했습니다.

| | 이전 (ADR-0018 뒤) | 이후 |
|---|---|---|
| 함께 로드 가능한 외부 모드(중첩 포함) | 18 | 클라이언트 127, 전용 서버 100 |
| 그중 직접 설치한 Fabric 모드(52개 중) | 3 | 클라이언트 42, 전용 서버 28 |
| Fabric API 하위 모듈(44개) | 2 | 클라이언트 44, 전용 서버 37(나머지 7개는 클라이언트 전용, `108`) |
| 실제 서버 시작(`corpusFabricServer`, `net.minecraft.server.Main`) | — | 99개 로드, Mixin 적용·초기화·Fabric API 레지스트리 동결을 거쳐 EULA 확인까지 |
| 게임 테스트 서버 | `corpusFabric` 18 로드·156 차단, `corpusAll` 19·247 | `corpusFabric` 97·74, `corpusAll` 98·160. 월드 생성, GameTest, 틱, 저장까지 |
| 판정 중 예외·게임 크래시 | 0 | 0 |

직접 설치한 Fabric 모드 중 남은 차단:

- 클라이언트 10개
  - 같은 ID(`117`): `collective`, `iris`, `lambdynlights`, `zoomify`. 같은 모드의 jar가 여러 로더용으로 묶음에 두 번 이상 들어 있어서이며, 실제 설치에서는 생기지 않습니다.
  - Fabric Loader 내부 클래스(`111`)와 없는 게임 멤버(`112`): `customskinloader-bootstrap`
- 전용 서버 24개: 클라이언트 전용 모드 19개(`108`)와 같은 ID(`117`)
- NeoForge·Forge 모드는 그대로 0개입니다(FML 런타임 `201`, Mixin `202`). 이 결정 범위 밖입니다.

이 결과를 읽는 법:

- 게임 테스트 서버는 전용 서버도 클라이언트도 아닙니다. Fabric API는 `net.minecraft.server.Main`에서만 레지스트리를 동결하므로, `corpusFabric`·`corpusAll`은 하네스 모드(`test-mods/fabric-gametest-harness`)가 같은 일을 합니다. Xaero 모드 2개는 `DedicatedServer`에서만 서버 준비를 마치므로 게임 테스트 서버 묶음에서 뺐습니다. 두 가지 모두 Fabric Loader에서 같은 진입점으로 실행해도 똑같이 실패합니다.
- 로드와 시작·틱·저장이 된다는 뜻입니다. 각 모드의 기능을 하나하나 확인한 것은 아닙니다.

#### 클라이언트 (같은 날, `corpusFabricClient`)

명령: `.\gradlew.bat :poly-bootstrap:corpusFabricClient` (게임 창이 열림). 묶음의 Fabric jar 42개와 `fabric-mixin` 테스트 모드를 넣고, `worldCreateSaveTest`가 저장한 월드에 들어갑니다.

- 결과: 외부 모드 137개 로드(38개 제외), 137개 초기화, Iris·Sodium 시작, 테스트 모드의 클라이언트 Mixin 적용, `joined the game`까지 통과.
- 처음 실행에서 찾아 고친 것:
  - 문맥 클래스로더가 Fabric `preLaunch` 뒤에야 게임 클래스로더로 바뀌었습니다. 그래서 Sodium의 `preLaunch` 중 ImmediatelyFast의 Mixin 설정 플러그인이 `ServiceLoader`로 서비스를 찾지 못했습니다. 이제 Mixin 시작 전에 바꿉니다(Fabric의 Knot과 같음).
  - Lithium의 `math.sine_lut` Mixin은 `Mth.SIN`을 없앱니다(Fabric Loader에서도 같음). 테스트 모드가 이것을 받아들이게 했습니다.
- 렌더링 결과를 사람이 눈으로 확인하지는 않았습니다. 셰이더 팩은 쓰지 않았습니다.
- "wrong loader" 안내 모드(JourneyMap Forge jar 안의 Fabric 스텁)는 `preLaunch`에서 일부러 시작을 멈춥니다. Fabric Loader에서도 같습니다.

### Minecraft 26.1 실제 모드 묶음 (2026-10-22)

명령: `.\gradlew.bat "-PpolyMinecraft=26.1" -PmodCorpusRefresh :poly-bootstrap:modCorpus`로 목록을 만들고(`compat/mod-corpus-26.1.json`), 그다음 `"-PpolyMinecraft=26.1" :poly-bootstrap:compatibilityCorpus`를 실행했습니다. jar는 버전마다 `build/poly/mod-corpus/<버전>/`에 받습니다.

- 목록: Modrinth에서 26.1용으로 가장 많이 받은 Fabric·NeoForge·Forge 모드 각 40개와 필수 의존 모드, 모두 127개(Fabric 42, NeoForge 44, Forge 41).
- 결과 (모두 통과, 판정 중 예외·게임 크래시 0):
  - 실제 전용 서버 시작(`corpusFabricServer`): 외부 모드 88개 로드, 초기화, EULA 확인까지
  - 게임 테스트 서버(`corpusFabric`): 86개 로드, 월드 생성, GameTest, 저장
  - `corpusAll` 89개, `corpusForge` 1개(여러 로더용 jar의 Fabric 쪽), `corpusNeoForge` 0개
- 26.1 묶음의 모드 수는 아직 26.2처럼 하나씩 검토하지 않았습니다. 그래서 시나리오는 정확한 수 대신 시작·저장이 되는지만 확인합니다(`reviewedCorpus`).

### Fabric 완전 지원 점검 (2026-10-22)

[`plans/FABRIC_FULL_SUPPORT.md`](plans/FABRIC_FULL_SUPPORT.md)의 완료 기준을 두 지원 버전에서 다시 확인했습니다.

| | 26.2 | 26.1 |
|---|---|---|
| 클라이언트(`corpusFabricClient`, 게임 창): 월드 입장, 200틱, "저장 후 타이틀로", 클라이언트 종료 | 140개 로드·초기화, 통과 | 128개, 통과 |
| 실제 전용 서버 시작(`corpusFabricServer`) | 100개, EULA 확인까지 | 93개 |
| 게임 테스트 서버(`corpusFabric`): 월드 생성, GameTest, 틱, 저장 | 98개 | 91개 |
| 클라이언트·서버 판정(`corpusAssessment`), 함께 로드 가능 | 128 / 100 | 122 / 95 |

직접 설치한 Fabric 모드 중 남은 차단은 Fabric Loader도 막는 경우뿐입니다.

- 같은 ID(`117`): 같은 모드의 여러 로더용 jar가 묶음에 함께 들어 있어서입니다(`collective`, `iris`, `lambdynlights`, `zoomify`).
- 서버의 클라이언트 전용 모드(`108`).
- 26.1의 버전 요구(`114`): `jei`(안의 `mezz_config`가 Minecraft 26.1.2 요구), `reeses-sodium-options`(Sodium 0.9.1 이상 요구, 0.8.9 설치).

이번 점검에서 찾아 고친 것:

- **CustomSkinLoader가 옛 버전 규칙을 고름.** 이 모드는 시스템 클래스로더에서 `version.json`을 읽어 버전을 알아냅니다. 개발용 실행은 게임 jar를 `-Dpoly.gameJar`로만 넘겨서 찾지 못했고, 26.2에 없는 `SkinManager$3`을 대상으로 잡아 Sodium 시작이 실패했습니다. 런처와 서버 설치는 게임 jar를 클래스 경로에 두므로, 개발용 실행도 그렇게 바꿨습니다. 이제 26.2·26.1 모두 맞는 규칙(47개 중 15개)으로 스킨 클래스를 변환합니다.
- **게임 jar가 클래스 경로에 있으면 FML이 바닐라 jar를 찾음.** FML은 게임 클래스의 `getResource`로 게임 jar 위치를 찾는데, 기본 `getResource`는 부모를 먼저 봅니다. 게임 클래스로더가 리소스도 클래스처럼 자기 jar·모듈에서 먼저 찾게 했습니다. 런처 프로필로 Forge를 설치할 때도 같은 문제가 생겼을 것입니다.
- **다른 로더 코드 경로의 게임 멤버로 막힘.** `collective`의 NeoForge 네트워크 처리 클래스는 NeoForge가 Minecraft에 붙이는 `hasChannel`을 씁니다. 그 클래스는 NeoForge API를 써서 Fabric에서는 로드되지 않습니다. 이제 이런 클래스의 없는 게임 멤버는 `113`으로 기록만 합니다. `collective`가 서버와 클라이언트에서 로드됩니다.

알아 둘 것:

- CustomSkinLoader가 있으면 클라이언트를 끈 뒤 "Client shutdown from post-main" 크래시 리포트가 남습니다. 이 모드의 스레드 풀이 비데몬 스레드를 1분 동안 남겨 두는데, Minecraft의 종료 감시는 15초 뒤에 리포트를 쓰고 JVM을 멈춥니다. 월드는 그 전에 저장됩니다. Fabric Loader에서도 같습니다.
- 모드의 기능을 하나하나 써 본 것은 아닙니다. 로드, 초기화, 월드 입장, 틱, 저장, 종료까지입니다. 렌더링 결과를 사람이 눈으로 보지 않았습니다.

### Forge 완전 지원 점검 (2026-10-23)

[`plans/FORGE_FULL_SUPPORT.md`](plans/FORGE_FULL_SUPPORT.md)의 완료 기준을 지원하는 세 버전에서 확인했습니다. 26.1.1은 Forge 63.0.2로 실행하지만 묶음을 따로 두지 않았습니다.

| | 26.2 (Forge 65.1.5) | 26.1 (Forge 62.0.9) | 26.1.2 (Forge 64.1.5) |
|---|---|---|---|
| 클라이언트(`corpusForgeClient`, 게임 창): 저장된 월드 입장, 200틱, 저장 후 종료 | Forge 모드 40개, 통과 | 26개(36개 중 10개는 Forge 64 이상 요구 등으로 `209`), 통과 | 36개, 통과 |
| 게임 테스트 서버(`corpusForge`): 월드 생성, GameTest, 저장 | 38개 중 32개 | 36개 중 18개 | 34개 중 28개 |
| 전용 서버(`corpusForgeServer`): Forge의 실행 핸들러로 EULA 확인까지 | 41개 중 35개 | 37개 중 19개 | 37개 중 31개 |
| Fabric 모드와 함께(`corpusFabricForge`) | 39개 중 33개 | 37개 중 21개 | 35개 중 30개 |
| 설치한 런처 프로필(`launcherProfileForgeTest`, 게임 창) | Forge 테스트 모드 3개 | - | 묶음의 Forge 모드 36개 |
| 설치한 서버(`serverInstallTest`) | `forge_hello`로 게임 테스트 서버·전용 서버 | - | Forge만(`forge_hello`는 Forge 65 이상용) |
| Forge 런타임 기능 | access transformer, Mixin, 변환 서비스, 모드 위치 지정자. JS coremod는 Forge 26.x에 없음 | 같음 | 같음 |
| 성능 | [`PERFORMANCE.md`](PERFORMANCE.md) 측정 6 | 같은 코드 경로, 따로 재지 않음 | 같음 |

빠지는 Forge 모드(`209`)는 실제 Forge에서도 빠지거나 실행되지 않는 경우뿐입니다. 이유마다 Forge의 바이트코드와 실제 Forge로 확인했습니다([`plans/FORGE_SUPPORT.md`](plans/FORGE_SUPPORT.md) "B5").

- 서버의 클라이언트 전용 모드(세 버전 모두 6개 안팎). 클라이언트에서는 실행됩니다.
- Forge·Minecraft·언어 로더 버전 범위(26.1의 9개, 모두 26.1.2용 빌드). FML은 이런 모드가 하나라도 있으면 모드 로딩 전체를 멈춥니다. Poly 모드 로더는 그 모드만 뺍니다.
- Mixin 0.8.7이 시작을 멈추는 Mixin 설정(없는 설정 파일, `JAVA_25`): Balm, Boatiview, No Chat Reports(26.1), No Chat Reports(26.1.2).

실제 Forge에서도 실패해 묶음 실행에서 뺀 빌드(`brokenUnderForge`): GlitchCore 26.1.0.4·TerraBlender 26.1.0.2(Mixin 설정을 매니페스트에 선언하지 않음), Cherished Worlds 16.0.1+26.1.2(리스너가 하나뿐인 클래스를 EventBus가 거부), Better Advancements 0.6.0.76(Forge가 읽지 않는 access widener로만 패키지 전용 클래스를 엶), Rhino 2101.2.7(Minecraft 1.21 빌드, `pack.mcmeta`가 없어 Forge가 로딩 경고를 내고 기본 이벤트 버스를 끔). libIPN은 Modrinth가 26.3용 jar를 26.2용으로도 표시해 26.2용 빌드로 고정했습니다.

이번 점검에서 고친 것:

- **초기 서비스 jar 안의 모드(Sodium).** Sodium의 NeoForge jar는 초기 서비스가 든 FML 라이브러리이고 모드는 그 안의 JarJar에 있습니다. FML은 그런 jar를 초기 서비스로 먼저 싣고 mods 폴더 검색에서 건너뛴 뒤 안의 모드를 JarJar에서 꺼냅니다. Poly 모드 로더의 검색이 같은 jar를 FML에 다시 넘겨 FML이 라이브러리로만 보았고, 클라이언트에서 Iris·Sodium Extra·Reese's Sodium Options가 Sodium이 없다고 멈췄습니다(`corpusNeoForgeClient` 첫 실행). 이제 FML이 이미 찾은 jar는 넘기지 않고, mods 폴더 밖의 NeoForge 모드(개발 실행)도 FML의 초기 서비스 검색에 보입니다(회귀 시나리오 `neoForgeEarlyService`, `compatibilityMatrix`에 포함; `serverInstallTest`는 설치한 서버의 mods 폴더에서).
- **JarJar 목록.** `217` 점검이 중첩 jar를 `META-INF/jarjar/` 폴더에서만 찾았습니다. FML처럼 `META-INF/jarjar/metadata.json`이 가리키는 jar로 바꿨습니다(Lambda Dynamic Lights 26.1.2 빌드의 API가 `META-INF/jars/`에 있음).

- **잠금 파일에서 바뀐 jar가 남음.** `modCorpus`가 잠금 파일에 없는 jar를 지웁니다.
- **런처 프로필은 26.2에서만.** `launcherProfileForgeTest`가 다른 버전에서는 그 버전 묶음의 Forge 모드로 실행합니다.
- **자동 실행이 월드의 백업 확인에 답하지 못함.** 26.1에서는 모드 데이터 팩 때문에 월드를 열 때 바닐라가 실험 설정 확인을 묻습니다(실제 Forge도 같음). 테스트 모드가 플레이어처럼 답합니다.

알아 둘 것:

- Forge는 모드 로딩에 경고가 하나라도 있으면 경고 화면을 띄우기 전에 기본 이벤트 버스를 끄고 다시 켜지 않습니다. 그 뒤로는 게임 이벤트가 어떤 모드에게도 가지 않습니다. 실제 Forge와 같은 동작이지만, 경고를 내는 모드가 있으면 다른 모드가 조용히 동작하지 않을 수 있습니다.
- 모드의 기능을 하나하나 써 본 것은 아닙니다. 로드, 초기화, 월드 입장, 틱, 저장, 종료까지입니다. Fabric 모드와 Forge 모드를 한 게임에서 함께 실행하는 것(G8)은 완료 기준 밖이며, 실행되지 않는 Fabric 주입 목록이 `138`로 남습니다.
- 실제 Minecraft Launcher로 실행한 기록은 없습니다. 런처 프로필 실행은 런처의 실행 방식을 재현한 것입니다.

### Quilt 완전 지원 점검 (2026-10-24)

[`plans/QUILT_FULL_SUPPORT.md`](plans/QUILT_FULL_SUPPORT.md)의 완료 기준을 26.2, 26.1, 26.1.2에서 확인했습니다. 헤드리스 작업과 창이 열리는 작업(세 버전의 클라이언트, 런처 프로필)이 모두 통과해 상태는 `Verified`입니다. 26.2 클라이언트의 첫 두 실행은 명령줄 길이와 NeoFullbright(아래, Quilt Loader에서도 실행할 수 없는 빌드) 때문에 실패했고, 고친 뒤 통과했습니다.

**Quilt 묶음.** `modCorpusQuilt`가 Modrinth에서 그 게임 버전에 Quilt용이고 Fabric용이 아닌 판이 있는 **모든** 프로젝트와 그 판이 필수로 적은 모드를 고정합니다(`poly-bootstrap/compat/mod-corpus-quilt-<버전>.json`, jar는 `build/`에만). 26.2는 Quilt 전용 판 114개 + 필수 3개(Fabric API, Cloth Config, Fusion), 26.1은 97개 + 1개(Fabric API 0.155.3+26.1.2), 26.1.2는 50개 + 1개입니다. 계획서 Q0의 수와 같습니다. 대부분(26.2에서 65개)은 데이터 팩을 모드로 묶은 리소스 모드이고, 클래스가 있는 모드는 30여 개입니다.

| | 26.2 | 26.1 | 26.1.2 |
|---|---|---|---|
| 게임 테스트 서버(`corpusQuilt`): 월드 생성, GameTest, 저장, 저장된 월드의 데이터 팩에 Quilt 모드 | 외부 모드 135개 로드(중첩·필수 모드 포함, 80개가 Quilt), Simple Voice Chat의 음성 서버 시작 | 118개(69개 Quilt) | 73개(25개 Quilt) |
| 전용 서버(`corpusQuiltServer`): EULA 확인까지 | 133개 | 116개 | 71개 |
| Quilt 전용 판 중 서버에서 로드 | 114개 중 93개 | 97개 중 79개 | 50개 중 34개 |
| Quilt 전용 판 중 클라이언트 판정에서 로드 가능(`corpusAssessmentQuilt`, 게임 없이) | 114개 중 103개 | 97개 중 81개 | 50개 중 38개 |
| 클라이언트(`corpusQuiltClient`, 게임 창): 저장된 월드 입장, 200틱, 저장 후 종료 | 통과(2026-10-24): 외부 모드 155개(88개 Quilt, 67개 Fabric; NeoFullbright 뺌) | 통과(2026-10-24): 131개(73개 Quilt) | 통과(2026-10-24): 88개(30개 Quilt) |
| 클라이언트 쪽 Mixin 전부 적용(`mixinAuditQuiltClient`, 게임 없이) | 통과(NeoFullbright를 뺀 대상 클래스 461개) | 통과(408개) | 통과(411개) |
| Forge 모드와 함께(`corpusQuiltForge`, Forge 묶음과 한 게임) | 통과: 외부 모드 123개, Forge 모드 파일 47개 중 41개 | 통과(Forge 62.0.9): 113개, 40개 중 24개 | 통과(Forge 64.1.5): 68개, 38개 중 33개 |
| 설치한 서버(`serverInstallTest`, `quilt_hello`로 게임 테스트 서버와 전용 서버) | 통과 | 통과 | 통과 |
| 설치한 런처 프로필(`launcherProfileTest`에 `quilt_hello`, 게임 창) | 통과(2026-10-24) | - | - |
| 테스트 모드 시나리오(`quiltOnly`, `quiltFabricPoly`, `quiltBlocked`) | 통과 | 통과 | (26.1 모듈과 같음) |
| 실제 Quilt Loader 0.31.0-beta.4와 비교(`quiltLoaderReference`, 전용 서버) | 114개 중 113개 같음 | 97개 중 96개 같음 | 50개 중 48개 같음 |
| 성능 | [`PERFORMANCE.md`](PERFORMANCE.md) 측정 7 | 같은 코드 경로, 따로 재지 않음 | 같음 |

**Quilt Loader API와 `quilt.mod.json` 항목(완료 기준 4).** 세 버전의 묶음에 loader plugin(`301`), 언어 어댑터(`302`), 변경 가능한 파일 시스템(`306`)을 쓰는 모드는 없습니다. 링크 검사에서 Quilt Loader의 제공하지 않는 클래스(`111`, `org.quiltmc.loader`)로 막힌 Quilt 모드도 없습니다. 제공하지 않는 것: loader plugin, Quilt 언어 어댑터, `QuiltFileSystems`·`api.config`·`api.gui`·`api.plugin`·`api.filesystem`(ADR-0027 결정 4), QSL 진입점 실행(`303`).

**빠지는 Quilt 모드는 실제 Quilt Loader에서도 빠지거나 실행되지 않는 경우뿐입니다(완료 기준 5).** `quiltLoaderReference`가 실제 Quilt Loader를 바닐라 전용 서버에서 필수 모드와 Quilt 전용 판 하나씩으로 실행해 비교했습니다(보고서 `build/reports/poly/quilt-loader-reference-<버전>.txt`). 26.2에서 빠지는 21개:

- Quilt Loader도 시작을 멈추는 메타데이터 7개(`300`): `intermediate_mappings`가 없음(기본 `hashed`, "doesn't support hashed mappings") 3개, `org.quiltmc.experimental:mojang`("does not have the experimental support for Mojang mappings enabled") 1개, SPDX가 아닌 라이선스 문자열 `All Rights Reserved` 2개, JSON이 아닌 `quilt.mod.json`("Placeholder file…") 1개. Quilt Loader의 오류 문구가 Poly 모드 로더의 `300` 설명과 같습니다.
- Quilt Loader도 멈추는 버전 요구 2개(`114`): `"versions": ">=26.2 <26.3"`처럼 공백으로 이은 범위는 Quilt 규칙으로 하나의 비교라서 26.2를 받지 않습니다(Quilt Loader: "requires version [26.2 <26.3, ∞) of minecraft").
- 서버의 클라이언트 전용 모드 11개(`305` 6개, `108` 5개). Quilt Loader도 서버에서 로드하지 않습니다. 하나(Armor Visibility)는 `fabric.mod.json`과 `quilt.mod.json`을 다 들고 `quilt.mod.json`에는 환경이 없어 Quilt Loader가 로드하지만, `client` 진입점과 `client` Mixin만 있어 서버에서는 Quilt Loader에서도 코드가 실행되지 않습니다.
- 모드가 아닌 jar 1개(Paper 플러그인 `xdlib-paper`, `POLY-DISCOVERY-002`). Quilt Loader도 "unknown" 파일로 둡니다.

26.1·26.1.2는 같은 유형에 하나가 더 있습니다. Yeth's TPA(`yeths-tpa-quilt-1.21-26.1.2`)는 Minecraft 1.21의 intermediary 이름(`net.minecraft.class_2168` 등)으로 컴파일된 빌드라 Poly 모드 로더가 `112`로 빼고, Quilt Loader는 로드하지만 26.x에서는 다시 매핑하지 않아("Not remapping mods because intermediary is missing!") 그 클래스들이 없으므로 실행되는 순간 실패합니다. 26.1의 Armor Visibility는 Quilt Loader에서도 버전 요구(`>=26.1 <26.4`)로 멈춥니다. 클라이언트에서 빠지는 것은 서버와 같은 메타데이터 판정(`300` 7개, 공백으로 이은 버전 범위의 `114`: Cyan 모드 2개, 26.1의 Eslium)과 서버 전용 모드(Real-Football은 두 메타데이터 모두 서버 전용, Yeth's TPA·Fleisync는 `fabric.mod.json`이 `server`)입니다.

**함께 알아 둘 것.**

- **리소스 모드와 QSL.** 26.2 묶음의 리소스 모드 65개는 `quilt_resource_loader`에 의존하지만 `"unless": "fabric-resource-loader-v0"`라서, Fabric API가 있으면 Quilt Loader도 Poly 모드 로더도 이 의존을 따지지 않습니다. Fabric API의 리소스 로더가 Quilt 모드로도 데이터 팩을 만들고, `corpusQuilt`가 저장된 월드에서 그 데이터 팩들이 켜져 있는지 확인합니다. Fabric API 없이는 `114`+`308`로 빠지며 Quilt Loader도 같습니다.
- **서버의 클라이언트 전용 중첩 모듈.** Simple Voice Chat처럼 Fabric API의 클라이언트 모듈을 중첩한 모드에 붙던 `124`("works only in part")는 과장이었습니다. 실제 Quilt Loader도 전용 서버에서 그 모듈을 로드하지 않고 아무것도 알리지 않습니다. 이제 다른 쪽 전용으로만 빠진 중첩 모드는 `124`에 세지 않습니다(Fabric 모드도 같음).
- **Quilt용 Fabric jar의 `quilt_loader` 의존.** Better Shulker Boxes, F34, InvSearch Storage Indexer, MTA NYC Subway Signs는 `fabric.mod.json`에서 `quilt_loader`를 요구합니다. Quilt Loader처럼 받습니다(Fabric 브리지의 내장 모드). `FabricLoader.isModLoaded("quilt_loader")`는 Fabric Loader처럼 `false`입니다.
- **InvSearch Storage Indexer**는 웹 서버를 데몬이 아닌 스레드로 시작해, EULA 확인에서 멈춘 전용 서버의 JVM이 끝나지 않습니다(Quilt Loader에서도 4분 제한 시간까지 남음). 전용 서버 실행에서만 뺍니다.
- **Quilt 모드와 Forge 모드를 한 게임에서**는 Fabric 모드와 같은 규칙입니다. Quilt와 Forge 메타데이터를 다 든 jar는 Forge가 실행하고(26.2 묶음의 Modrinth 데이터 팩 모드 6개), Forge가 자기 빌드를 로드하는 Simple Voice Chat의 Quilt 빌드는 `139`로 빠집니다.
- **찾아 고친 것: 개발용 실행의 클래스 경로 jar.** Gradle이 긴 클래스 경로를 `Class-Path`만 든 jar로 줄이면, 게임 클래스로더가 그 jar를 통해 바닐라 게임 jar까지 Forge의 패치된 게임보다 앞에 두었고, FML이 바닐라 게임을 찾아 Open Parties and Claims의 Forge용 Mixin이 실패했습니다(Quilt 묶음 + Forge 묶음). 런처와 설치한 서버의 실행에는 해당하지 않습니다.
- **클라이언트 첫 실행에서 고친 것(2026-10-24).** 26.2는 명령줄이 Windows 한도를 넘어 클라이언트가 시작하지 못했습니다(이제 인수 파일). 26.1.2는 Even Better Moon and Sun의 Mixin 주입 지점 `target`(`…PoseStack;pushPose:()V`)을 Mixin 0.8.7이 거부해 `SkyRenderer`가 로드되지 않고 월드에 들어가지 못했습니다. Fabric Loader·Quilt Loader도 같은 Mixin을 쓰므로 같고, 이제 시작 전에 `140`으로 빠집니다(26.1의 1.1.0도 같은 줄). 26.1은 클라이언트가 바로 끝났는데 로그가 다음 실행에 덮여 원인을 남기지 못했습니다. 이제 버전마다 로그를 따로 둡니다.
- **실제 Quilt Loader도 클라이언트에서 실행할 수 없는 빌드(클라이언트 실행에서 뺌).** NeoFullbright 1.0+mc26.2(`fabric.mod.json`만 있음)는 `ClientLanguageMixin`의 static이 아닌 `@ModifyArg` 핸들러가 static 메서드를 겨냥해, `ClientLanguage`를 로드할 때 Mixin 0.8.7이 거부하고(`non-static callback method … targets a static method which is not supported`, `ModifyArgInjector.checkTarget`) 클라이언트가 초기화 중에 멈춥니다. Fabric Loader 0.19.5와 Quilt Loader 0.31.0-beta.4도 같은 Mixin(`net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7`)으로 적용하므로 같습니다. 대상 메서드의 static 여부는 게임 클래스와 Mixin의 대상 선택을 다시 구현해야 알 수 있어 시작 전 판정으로 만들지 않았고, 대신 `mixinAuditQuiltClient`·`mixinAuditQuiltServer`가 묶음의 모든 Mixin을 Mixin 자신으로 대상 클래스에 적용해 봅니다(게임 없이, 창 없음). 세 버전의 Quilt 묶음에서 실패는 이 하나뿐이고, `corpusQuiltClient`와 클라이언트 감사에서 뺍니다(`brokenOnQuiltClient`). 서버에서는 클라이언트 전용(`108`)이라 원래 빠집니다.
- **게임 없는 클라이언트 판정과 실제 클라이언트의 차이.** `corpusAssessmentQuilt`는 26.1·26.1.2 클라이언트에서 F34와 Armor Visibility를 `111`(빠진 Fabric API 클래스)로 판정합니다. 실제 클라이언트(`corpusQuiltClient`)에서 F34는 로드됩니다(판정만의 차이). Armor Visibility는 실제 클라이언트에서도 `111`입니다. 이 버전들의 Fabric API(0.155.3+26.1.2)에 그 모드가 쓰는 `ScreenEvents$AfterForeground`가 없어서입니다. 실제 Quilt Loader는 26.1에서는 버전 요구로 멈추고, 26.1.2에서는 로드하지만 그 클래스가 없어 클라이언트 초기화가 실행되는 순간 실패합니다(바이트코드와 Fabric API jar로 판단, 실행하지 않음).
- **로드되지만 모드 자신의 결함이 있는 것(성능 측정에서 발견).** Omni는 공용 Mixin이 클라이언트 클래스 `LocalPlayer`를 `instanceof`로 검사해, 서버에서 몹이 뛰는 순간 `NoClassDefFoundError`로 멈춥니다(Quilt Loader의 환경 제거도 이 참조는 지우지 않으므로 같음). Craftable Bucket of Sulfur Cube(와 Easy 판)는 `minecraft:tick`에 없는 함수 `sulfur:tick`을 적어, 게임이 `minecraft:tick` 태그 전체를 거부하고 어떤 데이터 팩의 틱 함수도 돌지 않습니다(바닐라 `TagLoader`). 묶음 실행은 몹이 뛰기 전에 끝나서 통과합니다.
- 실제 Quilt Loader로 클라이언트를 실행해 비교하지는 않았습니다. 모드의 기능을 하나하나 써 본 것은 아닙니다.

### NeoForge 완전 지원 점검 (2026-10-24)

[`plans/NEOFORGE_FULL_SUPPORT.md`](plans/NEOFORGE_FULL_SUPPORT.md)의 완료 기준을 지원하는 버전에서 확인한 결과입니다. 26.1.1은 NeoForge `26.1.1.15-beta`로 `neoForgeWorldTest`·`serverInstallTest`만 실행하고 묶음을 따로 두지 않았습니다(Forge와 같음). 창이 열리는 작업(클라이언트, 런처 프로필)은 2026-10-24 다섯 번째 실행에서 모두 통과했고, 완료 기준을 모두 확인해 상태는 **`Verified`**입니다.

| | 26.2 (NeoForge 26.2.0.93) | 26.1 (26.1.0.19-beta) | 26.1.2 (26.1.2.122) |
|---|---|---|---|
| 클라이언트(게임 창): 저장된 월드 입장, 200틱, 저장 후 종료 | 통과(2026-10-24): `corpusNeoForgeClient` NeoForge 모드 54개(jar 44개, FancyMenu·Sodium·Iris 포함), 타이틀 화면에 모드 수 줄. `neoForgeClient`(테스트 모드 + Poly 모드 2개) | 통과(2026-10-24): 36개(jar 37개; Iris·Zoomify·Lambda Dynamic Lights는 실제 NeoForge도 실패해 뺌) | 통과(2026-10-24): 55개(jar 45개) |
| 게임 테스트 서버(`corpusNeoForge`): 월드 생성, GameTest, 저장 | 38개(묶음 36 + 하네스 + `neoforge-environment-probe`), 통과 | 35개 중 31개(`217` 4개), 통과 | 40개(묶음 38 + 하네스 + 프로브), 통과 |
| 전용 서버(`corpusNeoForgeServer`): NeoForge가 게임을 시작해 EULA 확인까지 | 37개, 통과 | 34개 중 30개, 통과 | 39개, 통과 |
| FML이 실행한 모드 / 게임 레이어 모듈(프로브 없이 같은 jar로 NeoForge 자신의 설치가 만든 모듈) | 프로브 없이 49 / 56 (56), 프로브와 50 / 57 | 39 / 45 (45), 프로브와 40 / 46 | 51 / 58 (58), 프로브와 52 / 59 |
| 설치한 서버(`serverInstallTest`) | `neoforge_hello` + 하네스로 게임 테스트 서버·전용 서버 | 하네스로 같음 | 하네스로 같음 |
| 설치한 런처 프로필(`launcherProfileNeoForgeTest`, 게임 창) | 통과(2026-10-24): `neoforge_hello`와 세션 모드, NeoForge 모드 2개 | - | 통과(2026-10-24): 그 버전 묶음의 NeoForge 모드 54개(jar 44개) |
| NeoForge 런타임 기능 | FML의 처리기(access transformer, enum 확장, Mixin, `ServiceLoader` 처리기), JarJar, 언어 공급자(Kotlin), FML 플러그인 라이브러리, 초기 서비스가 실제 모드에서 동작(서버). FML 쪽에서 바꾸는 것은 검사한 `FMLLoader` 두 호출과 `ModList` 두 메서드뿐 | 같음 | 같음 |
| 성능 | [`PERFORMANCE.md`](PERFORMANCE.md) 측정 8 | 같은 코드 경로, 따로 재지 않음 | 같음 |

빠지는 NeoForge 모드는 NeoForge 자신의 서버에서도 멈추거나 거부되는 경우뿐입니다. 모두 NeoForge 자신의 설치 서버(같은 jar와 하네스)로 확인했습니다([`plans/NEOFORGE_FULL_SUPPORT.md`](plans/NEOFORGE_FULL_SUPPORT.md) "N11").

- 서버에서 멈추는 클라이언트 모드: Sodium(FML 초기 서비스가 LWJGL을 찾음), Sodium Extra·Reese's Sodium Options·Iris(Mixin 플러그인이 Sodium 클래스를 찾음), ImmediatelyFast 1.16.5(26.2, LWJGL `MathUtil`), Zoomify(모드 생성에서 클라이언트 클래스). 서버 실행에서만 빼고 클라이언트에서 실행합니다. NeoForge 메타데이터에 클라이언트 전용 표시가 없어 Poly 모드 로더도 NeoForge처럼 스스로 빼지 않습니다.
- 26.1의 NeoForge 버전 범위(`217`): Forge Config API Port, JEI(안의 mezz_config 포함), Puzzles Lib는 26.1.2의 NeoForge를 요구합니다. NeoForge는 이런 모드가 하나라도 있으면 모드 로딩 전체를 멈추고, Poly 모드 로더는 그 모드(와 그것이 필요한 Veinminer Client)만 뺍니다.
- 26.1 묶음의 Architectury 20.0.4·Veinminer 2.10.4(26.1.0.19-beta에 없는 `BreakBlockEvent`)와 Jade 26.1.12(26.1에 없는 필드를 겨냥한 Mixin)는 `neoforge.mods.toml`로는 맞지만 NeoForge 자신의 서버에서도 멈춰 묶음 실행에서 뺍니다(`brokenUnderNeoForge`).
- 26.1 클라이언트에서만: Lambda Dynamic Lights 4.11.2+26.1.2는 Minecraft `[26.1,26.1.2]`만 요구해 FML이 로드하지만, 클라이언트 초기화가 NeoForge 26.1.2.122에만 있는 `TagsUpdatedEvent$ClientPacketReceived`를 써서 26.1.0.19-beta에서 멈춥니다(두 universal jar로 확인). 서버에서는 실행되고, 클라이언트 실행에서만 뺍니다(`brokenOnNeoForgeClient`, ADR-0026 29번).

이번 점검에서 고친 것:

- **서버 클래스 경로의 라이브러리.** NeoForge 자신의 서버는 `install_profile.json`의 LWJGL 코어·tinyfd(26.1.x)도 클래스 경로에 둡니다. 없어서 ImmediatelyFast 26.1 빌드가 서버에서 멈췄습니다. 이제 같이 받습니다.
- **로더별 모드 수.** NeoForge 모드가 Forge 칸에 세어지던 것을 따로 셉니다.
- **모드가 없는 로더의 API.** Fabric·Quilt 모드가 없으면 Fabric Loader·Quilt Loader API가 게임·모드 코드에 보이지 않습니다(실제 NeoForge처럼). 보이던 동안 Lambda Dynamic Lights 안의 Yumi가 NeoForge 클라이언트를 Fabric으로 보고 기본 설정 파일을 찾지 못해 클라이언트가 멈췄습니다(ADR-0026 27번, 2026-10-24).
- **FancyMenu와 타이틀 화면의 모드 수.** FancyMenu는 타이틀 화면의 NeoForge 브랜딩 호출을 끄고 NeoForge 줄을 직접 그립니다. 모드 수를 그 호출이 아니라 NeoForge의 브랜딩 목록(`BrandingControl.getBrandings`)에 더하도록 바꿔, FancyMenu가 그리는 브랜딩에도 들어갑니다(ADR-0026 28번, `POLY-COMPAT-219` 폐기, 2026-10-24).

알아 둘 것:

- NeoForge는 운영 환경에서 Mojang의 게임 테스트를 돌리지 않습니다(NeoForge 자신의 설치에서도). 게임 테스트 서버 검증은 테스트 하네스 모드(`neoforge-gametest-harness`)로 합니다.
- NeoForge 모드는 Fabric·Quilt 모드와 한 게임에서 실행됩니다(아래 "NeoForge와 Fabric·Quilt 모드를 한 게임에서"). MinecraftForge 모드와 함께 넣으면 Forge가 실행되고 NeoForge 모드는 이유와 함께 빠진 뒤 진단만 합니다(`221`).
- NeoForge 패치에서 꺼지는 Poly 모드 로더 내장 규칙(네 릴리스 같음, `BuiltInRulesTest`): 서버 `poly:server_brand`·`poly:payload_codecs`, 클라이언트는 그 둘과 `poly:client_brand`·`poly:client_title_mod_counts`·크리에이티브 탭 쪽 넘김 네 개. 브랜드·타이틀 화면·크리에이티브 탭 쪽은 NeoForge가 직접 합니다. `poly:payload_codecs`가 꺼지므로 NeoForge와 함께일 때 Poly 모드의 네트워크 채널은 확인하지 않았고, 동작하지 않을 수 있습니다.
- 측정 중 NeoForge 모드 생성에서 서버가 가끔 멈췄습니다. 게임 클래스로더와 FML 플러그인 클래스로더의 교착이었고 고쳤습니다(`GameClassLoaderFallbackTest`).
- 실제 Minecraft Launcher로 실행한 기록은 없습니다. 런처 프로필 실행은 런처의 실행 방식을 재현한 것입니다.

자세한 결정과 이전 실험은 [ADR-0009](adr/0009-loader-bridges.md)를 보세요. 아래는 Phase 0 기록입니다.

### NeoForge와 Fabric·Quilt 모드를 한 게임에서 (2026-10-24, ADR-0026 30~43)

Forge와 Fabric을 함께 실행할 때(ADR-0021 G8)처럼 하나의 Mixin(Fabric의 것)이 NeoForge 모드도 맡고, NeoForge의 FML은 자기 Mixin을 시작하지 않으며, Fabric 모드는 NeoForge의 레지스트리가 열린 채로 초기화됩니다. 아래는 모두 2026-10-24에 이 PC에서 실행한 결과입니다(헤드리스). 수는 "Loaded mods per loader" 줄의 Fabric / Quilt / NeoForge(중첩 jar 포함)이고, `138`은 NeoForge 패치 때문에 실행되지 않는 Fabric 주입으로 알린 수입니다.

| 작업 | 26.2 (NeoForge 26.2.0.93) | 26.1.2 (26.1.2.122) | 26.1 (26.1.0.19-beta) |
|---|---|---|---|
| `neoForgeFabricGameTest`: 테스트 모드(NeoForge 4, Fabric 7, Poly 2), 게임 테스트 서버 | 통과(2026-10-24 다시 실행): ID를 다르게 쓴 같은 모드(`twin-mod`/`twin_mod`)는 NeoForge 빌드만 실행되고 그 사용자 모드는 NeoForge 빌드로 충족(37번), 두 로더의 mixin이 NeoForge가 패치한 `MinecraftServer`에 함께 적용, NeoForge 모드의 MixinExtras `@ModifyReturnValue`, `requiredMods`가 없는 설정은 FML처럼 건너뜀, Fabric 모드가 `onInitialize`에서 등록한 아이템이 NeoForge의 동결 뒤에도 있음, 서로의 모드 목록, 여러 로더용 jar의 Fabric 쪽 실행(35번) | (테스트 모드는 26.2용) | (같음) |
| `corpusFabricNeoForge`: 묶음의 Fabric·NeoForge 모드, 월드 생성·GameTest·저장 | 통과: 54 / 0 / 48, `138` 65개, Cloth Config는 NeoForge 빌드만(37번) | 통과: 53 / 0 / 50, `138` 67개 | 통과: 68 / 0 / 38, `138` 63개 |
| `corpusFabricOnNeoForge`: Fabric 빌드만 NeoForge 위에서(Sinytra Connector의 경우) | 통과: 96 / 0 / 7, `138` 80개 | 통과: 95 / 0 / 7 | 통과: 89 / 0 / 7 |
| `corpusFabricNeoForgeServer`: 전용 서버, EULA 확인까지 | 통과: 53 / 0 / 47 | 통과: 52 / 0 / 49 | 통과: 70 / 0 / 36 |
| `corpusQuiltNeoForge`: Quilt 묶음과 NeoForge 모드, 저장된 월드가 Quilt 데이터 팩과 NeoForge 모드 데이터를 켬 | 통과: 50 / 73 / 56, Better Shulker Boxes가 NeoForge의 Cloth Config로 실행 | 통과: 46 / 21 / 53 | 통과: 48 / 65 / 40 |
| `corpusFabricNeoForgeClientMixins`: 클라이언트(창 없음), 두 로더의 Mixin 설정이 겨냥하는 클래스를 모두 로드 | 통과: 62 / 0 / 52, 134개 설정, 766개 클래스, 실패 0. 2026-10-25부터 빠진 mixin의 형을 쓰는 코드도 실패로 셈(39번), 0. 2026-10-25부터 같은 메서드의 default가 둘인 클래스도 실패로 셈(42번): 고치기 전 6, 고친 뒤 0. 게임 클래스가 부르는 모드 클래스의 형 사용도 셈(43번): 고치기 전 3, 고친 뒤 0 | 통과: 61 / 0 / 53, 133개 설정, 920개 클래스, 실패 0(2026-10-25 다시 실행, 42번 포함) | 통과: 81 / 0 / 34, 126개 설정, 859개 클래스, 실패 0(42번: 고치기 전 8, 고친 뒤 0) |
| `corpusFabricNeoForgeClientBootstrap`: 클라이언트(창 없음, `poly.neoforge.headless`), NeoForge 모드 로딩, 모드 초기화, `Minecraft.<init>`이 게임 창을 만들기 직전까지 | 통과: 내장 레지스트리 두 번째 얼리기 처리(38번), 루트·아이템·블록 언 상태 | 통과 | 통과 |
| `corpusFabricNeoForgeJoin`: 게임 테스트 서버에 가짜 클라이언트가 메모리 채널로 접속, 로그인·설정 단계(40번)·놀이 단계(41번) | 통과(2026-10-25 다시 실행): NeoForge의 채널 등록·협상, Fabric API의 공통 네트워킹·레지스트리 동기화, 두 로더의 설정 작업, 놀이 단계에서 Fabric API 페이로드와 NeoForge 페이로드를 주고받고 서버가 둘 다 답함, Fabric 페이로드 묶음 도착, 3초 동안 연결 유지. 고치기 전에는 창 실행과 같은 `Unknown addon`으로 끊김 | 통과(같음) | 통과(같음) |
| `corpusFabricNeoForgeClient`: 클라이언트(게임 창), 저장된 월드 입장·200틱·저장·종료 | 2026-10-24~25 창 실행: Cloth Config의 두 빌드로 `110`(37번), 모드 초기화 뒤 `Registry is already frozen`(38번), `GameRenderer.<init>`에서 `ClassCastException`(39번), 통합 서버 접속에서 `minecraft:register` 인코딩 실패(40번) 순서로 멈춤. 2026-10-25 다섯 번째 창 실행: 세 버전 모두 "PolyDev joined the game"까지 가고 약 2초 뒤 놀이 단계에서 `Unknown addon`으로 끊김(41번). 여섯 번째: 접속은 유지되고, 월드에서 그리는 중에 `Conflicting default methods`로 멈춤(42번). 일곱 번째: 통합 서버가 엔티티를 틱하다가 Lithium의 `ClassCastException`으로 멈춤(43번). 2026-10-25 여덟 번째 창 실행 통과(Fabric 62 / NeoForge 53): 저장된 월드 입장·200틱·저장·종료, 깨끗한 종료 | 2026-10-25 통과(61 / 54, 일곱·여덟 번째 창 실행) | 2026-10-25 통과(81 / 35, 일곱·여덟 번째 창 실행) |

- **Sinytra Connector와 비교.** `PERFORMANCE.md` 측정 9는 같은 26.2 Fabric 묶음을 실제 NeoForge 위의 Connector 3.1.0-beta.3로 실행했고, Architectury, Balm, Lithium(NeoForge가 패치한 메서드에서 mixin 주입 검사 실패), Puzzles Lib(Connector의 jar 변환이 `slice`를 읽지 못함), Forge Config API Port의 Fabric 빌드(`net.neoforged.fml.config`가 겹쳐 `LinkageError`)에서 서버가 멈췄습니다. `corpusFabricOnNeoForge`(26.2)에서는 다섯 모두 Fabric 모드로 로드되고 월드 생성·GameTest·저장까지 갑니다. 대신 NeoForge 패치와 맞지 않는 주입은 실행하지 않고 `138`로 알립니다. 모드마다 기능을 확인하지는 않았습니다. Zoomify 2.17.0의 하나의 jar는 NeoForge가 맡는데, Fabric 모드만 넣으면 NeoForge 쪽 의존(NeoForge용 YACL, 언어 공급자 `klf`)이 없어 Connector에서는 NeoForge가 멈춥니다. Poly 모드 로더는 NeoForge 쪽을 `217`로 빼고(NeoForge만 넣은 `neoForgeProbe`로 확인) Fabric 모드가 함께라면 Fabric 쪽을 실행합니다(ADR-0026 35번, 테스트 모드로 확인, Zoomify 자체는 클라이언트 모드라 서버 묶음에서는 확인하지 않음). 성능은 NeoForge와 함께인 Poly 모드 로더를 재지 않았으므로 비교하지 않습니다.
- **같은 모드의 두 빌드.** Fabric 빌드와 NeoForge 빌드가 함께 있으면 NeoForge 빌드를 실행하고 Fabric 빌드는 뺍니다(`139`, Forge와 같음). 26.2 `corpusFabricNeoForge`에서 36개입니다(Architectury, Balm, Lithium, Puzzles Lib, Forge Config API Port, JEI, Jade 등). 2026-10-24부터는 ID를 로더마다 다르게 쓴 같은 모드도 찾습니다(ADR-0026 37): 같은 ID, FML 표기로 바꾼 ID, 제공 ID, 또는 클래스의 절반 이상이 한 NeoForge jar에 있음. 26.2 묶음에서 새로 찾은 것은 Cloth Config(`cloth-config`/`cloth_config`)와 그 안의 basic-math, 클라이언트에서 Reese's Sodium Options와 Sodium Extra, 그리고 Fabric 빌드 안에 중첩된 라이브러리 중 NeoForge 빌드가 클래스를 그대로 든 것(veinminer의 공통 jar, More Culling의 conditional-mixin)입니다. 두 빌드가 대부분 같은 코드이면(빠진 빌드 클래스의 절반 이상) 빠진 빌드가 필요한 모드는 NeoForge 빌드가 대신 충족합니다. 예: Quilt 묶음의 Better Shulker Boxes는 NeoForge의 Cloth Config로 계속 실행됩니다. Forge 옆에서도 같습니다: FusionFix(Quilt)가 이제 Forge의 Fusion으로 실행됩니다(`corpusQuiltForge` 26.2, 외래 모드 124개 로드, 전에는 123개). 그래서 아래 표의 Fabric 수가 이전보다 2~5개 적습니다(빠진 것은 모두 NeoForge 빌드가 실행). 고치기 전에는 클라이언트에서 Cloth Config의 두 빌드가 다 예제 설정을 등록해 `110`으로 멈췄습니다(`corpusFabricNeoForgeClient`, 2026-10-24 창 실행, 세 버전 모두). 두 쪽 모두 운영 환경으로 판정됐고(Fabric `isDevelopmentEnvironment()` false, FML `Assuming we're launching production`), 개발 환경 판정은 원인이 아니었습니다.
- **실행되지 않는 Fabric 주입(`138`).** 대부분 Fabric API입니다. NeoForge가 같은 기능을 자기 코드로 하는 곳(레지스트리 동기화, 블록 부수기·아이템 사용 이벤트 일부, 렌더 파이프라인 등)입니다. Fabric API의 리소스 로더는 동작합니다: Quilt 모드의 데이터 팩이 NeoForge의 모드 데이터와 함께 켜집니다. NeoForge와 함께일 때 Fabric API의 각 기능이 동작하는지는 모드마다 확인하지 않았습니다.
- **`220`.** 26.2 클라이언트에서 1개: Iris(NeoForge)의 설정 플러그인이 선택 도중 `BlockGetter`를 로드해, Lithium(NeoForge)의 `world.raycast.BlockGetterMixin`이 적용되지 않습니다. NeoForge만 넣어도 이 mixin은 조용히 적용되지 않습니다.
- **빠지는 것.** NeoForge만 있을 때와 같은 이유로 빠지는 모드(위 "NeoForge 완전 지원 점검")는 같이 빠집니다. 26.1에서는 More Beets 1.0(Quilt와 NeoForge용 데이터 팩)이 NeoForge 26.1.0.19-beta의 게임 테스트 서버를 멈추게 해(NeoForge만 넣은 `neoForgeProbe`로도 같음) `corpusQuiltNeoForge`에서 뺐습니다.
- **알려진 차이.** Fabric의 Mixin은 FML의 access transformer·NeoForge coremod보다 먼저 적용됩니다(FML만 있을 때는 access transformer 뒤). 클라이언트에서는 Fabric 모드가 NeoForge의 모드 로딩 뒤에 초기화되므로(Fabric Loader가 고르는 지점) NeoForge가 등록 직후에 하는 일(크리에이티브 탭 정렬 등)이 Fabric 모드의 항목을 보지 못할 수 있고, 확인하지 않았습니다. Fabric·Quilt 모드가 있으면 Fabric Loader API가 NeoForge 모드에도 보입니다(Connector와 같음). 여러 로더용 라이브러리가 그것으로 로더를 고르면 NeoForge 쪽 대신 Fabric 쪽 경로를 탑니다. Lambda Dynamic Lights 안의 Yumi가 그렇고, NeoForge가 실행하는 모드의 파일도 Fabric 쪽에서 찾을 수 있게 해 기본 설정 파일을 찾습니다(ADR-0026 36번, `corpusFabricNeoForge`의 `neoforge-environment-probe`가 세 버전에서 `true`). 다른 라이브러리는 확인하지 않았습니다.
- **MinecraftForge 모드와 함께는 아직 아닙니다.** Forge가 실행되고 NeoForge 모드는 이유와 함께 빠집니다(`221`, `corpusAll`). 이유는 ADR-0026 34번입니다: 26.2 서버 jar에서 두 로더가 다 바꾸는 바닐라 메서드 2,342개 중 736개(288개 클래스)는 같은 명령을 서로 다르게 바꾸고, `Entity`·`Level`·`BlockEntity` 등 6개 클래스는 상위 클래스를 서로 다르게 바꿉니다.

## Phase 0 당시 지원 상태

| 로더 | 상태 | 근거 |
|---|---|---|
| Poly 모드 로더 Native | **Verified** (샘플 모드 1개, 클라이언트·전용 서버) | `runClient`, `serverBootSmokeTest`, `PolyRuntimeTest` |
| Fabric | **Blocked** | 브리지 없음. Poly 모드 로더가 감지하고 `POLY-COMPAT-001`로 보고한 뒤 로드하지 않음 |
| NeoForge | **Blocked** | 위와 같음 |
| MinecraftForge | **Blocked** | 위와 같음 |

상태 정의(설계서 §13.3): `Verified` / `Partial` / `Experimental` / `Blocked` / `Untested`. jar가 로드됐다는 사실만으로 `Verified`라고 쓰지 않습니다.

## 실험 1 — 분류와 명시적 차단

공식 Maven에서 받은 jar를 SHA-1로 검증하고 `run/client/mods/`에 넣은 뒤, `runClient`로 실행했습니다.

| jar | 출처 SHA-1 | Poly 모드 로더 분류 결과 |
|---|---|---|
| `fabric-api-0.161.0+26.2.jar` | `332da34e…` 일치 | `POLY-COMPAT-001 … FABRIC … Blocked … NOT loaded` |
| `neoforge-26.2.0.93-universal.jar` | `6c86caca…` 일치 | `POLY-COMPAT-001 … NEOFORGE … Blocked … NOT loaded` |
| `forge-26.2-65.1.5-universal.jar` | `0f335ff3…` 일치 | `POLY-COMPAT-001 … FORGE … Blocked … NOT loaded` |

결과: Poly 모드 로더 Native 샘플 모드와 게임은 정상 실행됐습니다(창 제목 `Minecraft* 26.2`). 차단된 jar는 조용히 무시되지 않고 경고로 보고됐습니다.

## 실험 2 — 각 로더가 쓰는 변환·초기화 수단 (jar 구조 분석)

| 항목 | Fabric API 0.161.0+26.2 | NeoForge 26.2.0.93 | Forge 65.1.5 |
|---|---|---|---|
| 메타데이터 | `fabric.mod.json` | `META-INF/neoforge.mods.toml` (`modLoader=javafml`) | `META-INF/mods.toml` (`javafml`) |
| 중첩 jar | 44개 (`jars`) | — | — |
| Mixin | 설정 59개, Mixin 클래스 487개 | `neoforge.mixins.json` 1개 | 없음 (universal jar 기준) |
| 접근 변경 | class tweaker 34개 | Access Transformer 652줄 | Access Transformer 731줄 |
| 엔트리포인트 | `main`, `client` | `@Mod` 생성자 + 이벤트 버스 | `@Mod` + 이벤트 버스 |

의미:
- Fabric 모드를 실행하려면 **Mixin 의미를 정확히 재현**해야 합니다. 중첩 jar 해제와 class tweaker 처리도 필요합니다. 엔트리포인트 호출만으로는 Fabric API 자체도 동작하지 않습니다.
- NeoForge와 Forge는 수백 줄의 Access Transformer를 전제합니다. 바닐라 패치는 universal jar 밖의 별도 산출물에 있습니다.

## 조사 요약 (공식 문서·소스, 2026-10-19 확인)

- **난독화 제거:** Mojang은 26.1부터 Java Edition을 난독화하지 않습니다. Fabric은 Yarn·Intermediary를 26.x에서 더 이상 쓰지 않습니다(Intermediary에 26.x 산출물 없음). NeoForge도 Mojang 이름을 씁니다. 따라서 **26.x에서는 세 로더의 모드가 같은 이름 공간(Mojang 원래 이름)을 씁니다.** 매핑 충돌 위험이 크게 줄었습니다.
- **Fabric:** Loader 0.19.5가 최신 안정판입니다.
  - 부팅 순서: `Knot`이 `GameProvider` 탐색 → 클래스로더 생성 → 모드 검색·freeze → class tweaker → `MixinBootstrap.init`과 모드별 Mixin 설정 → `preLaunch` → 게임 실행.
  - 클래스 로드 시 변환 순서: 엔트리포인트 패치 → 환경 제거·class tweak → Mixin.
- **NeoForge:** FancyModLoader가 ModLauncher를 흡수·제거했습니다.
  - 자체 `TransformingClassLoader`와 JPMS 게임 레이어를 씁니다.
  - `ClassProcessor` SPI(AT, enum 확장, Mixin, dist 정리)를 씁니다.
  - Mixin 포크는 Fabric과 같은 `net.fabricmc:sponge-mixin`입니다(FML 0.17.4, Fabric Loader 0.17.5).
- **MinecraftForge:** 26.x에서도 ModLauncher 10 + securemodules + 원본 Mixin 0.8.7 구조입니다. NeoForge와는 별개 스택입니다.
- **Sinytra Connector:** 3.1.0-beta.3+26.2(베타)로 NeoForge 위에서 Fabric 모드를 실행합니다. 공식 FAQ의 실패 원인은 다음과 같습니다.
  - NeoForge 패치가 바꾼 메서드를 겨냥한 Mixin
  - 로더별로 분리된 코드베이스
  - NeoForge에서는 Fabric 초기화 시점에 레지스트리가 이미 동결됨

## 혼합 실행의 핵심 위험

| 위험 | 이유 | 심각도 |
|---|---|---|
| 게임 클래스 소유권 | Knot, FML, Poly 모드 로더가 모두 `net.minecraft`를 직접 정의하려 함. 한 로더만 소유 가능 | 치명 |
| Mixin 서비스 중복 | Fabric과 NeoForge가 각자 `IMixinService`를 등록. Mixin 상태는 전역 | 치명 |
| JPMS 대 평면 클래스 경로 | FML은 모듈 레이어(분할 패키지 불가), Fabric 모드는 평면 클래스 경로 전제 | 높음 |
| 변환 순서 | Fabric은 고정 순서, FML은 프로세서 그래프, NeoForge 바이너리 패치가 Fabric Mixin 대상을 바꿀 수 있음 | 높음 |
| 레지스트리 시점 | Fabric은 `onInitialize`에서 등록, NeoForge는 `RegisterEvent`의 unfreeze 구간에서만 등록 | 높음 |
| 네트워크 협상 | NeoForge는 필수 채널 누락 시 접속 거부, 레지스트리 동기화 방식이 서로 다름 | 높음 |
| 이벤트 의미 | Fabric `Event<T>` 배열과 NeoForge 우선순위·취소 버스, 서로 간 순서 미정의 | 중간 |
| 클라이언트 클래스의 서버 로딩 | Fabric `environment` 제거와 NeoForge `@Mod(dist)` 방식이 다름 | 중간 |

## 다음 실험 제안 (Phase 5 전)

1. Mixin 0.17.x(`net.fabricmc:sponge-mixin`)를 Poly 모드 로더 `GameClassLoader` 위에 `IMixinService`로 붙여, 단일 Mixin 설정(의존성 없는 소형 Fabric 모드 1개)을 적용합니다.
2. Fabric `GameProvider`/Knot을 쓰지 않고 `fabric.mod.json`의 `main`/`client` 엔트리포인트만 호출하는 경로가 어디까지 가능한지 측정합니다.
3. NeoForge는 바이너리 패치 의존도 때문에, Fabric 실험 이후로 미룹니다.

## 출처

- https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition
- https://docs.fabricmc.net/develop/porting/mappings/ , https://fabricmc.net/2026/06/15/262.html
- https://meta.fabricmc.net/v2/versions/loader
- https://github.com/FabricMC/fabric-loader (Knot.java, KnotClassDelegate.java, FabricMixinBootstrap.java, MinecraftGameProvider.java)
- https://docs.fabricmc.net/develop/loader/fabric-mod-json , https://docs.fabricmc.net/develop/class-tweakers/
- https://github.com/neoforged/FancyModLoader , https://neoforged.net/news/2025-retrospection/
- https://docs.neoforged.net/docs/gettingstarted/modfiles , https://docs.neoforged.net/docs/concepts/events , https://docs.neoforged.net/docs/concepts/registries
- https://files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json , https://github.com/MinecraftForge/MinecraftForge/blob/26.2/settings.gradle
- https://github.com/Sinytra/Connector , https://moddedmc.wiki/en/project/connector/latest/docs/faq , https://github.com/Sinytra/Launchpad
