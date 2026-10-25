# poly-version-adapter-v26_3

## 이 모듈이 하는 일
Minecraft 26.3 전용 지식을 담습니다. 클라이언트/서버 진입점과 브랜드 변환 규칙 2개가 있습니다.

## 무엇을 소유하는가
`dev.polymodloader.game.version.v26_3.Minecraft26_3Adapter`와 `META-INF/services` 등록

## 무엇을 하지 않는가
다른 버전을 지원하지 않습니다. 이 모듈 밖에 Minecraft 이름을 두지 않습니다.

## 의존성
`poly-version-adapter-api`. Minecraft jar에는 컴파일 의존하지 않습니다. 이름은 `javap`로 검증한 문자열입니다.

## 주요 진입점
`Minecraft26_3Adapter`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest
```

실제 26.3에서 규칙 승인과 적용을 확인합니다. 클라이언트는 `:poly-bootstrap:runClient`로 확인합니다.

## 대표 흐름
`poly:client_brand`(CLIENT)와 `poly:server_brand`(양쪽)가 `"vanilla"`를 `"poly"`로 바꿉니다. 실패 정책은 DISABLE_RULE입니다.

`poly:client_title_mod_counts`(CLIENT, DISABLE_RULE)는 타이틀 화면 `TitleScreen.extractRenderState`가 버전 글자를 그리는 유일한 `GuiGraphicsExtractor.text(Font, String, int, int, int)` 호출을 게임 계층의 `client.TitleScreenModCounts.onVersionText`로 바꿉니다(`RedirectCall`, ADR-0013). 훅은 그 줄을 그리고 그 위에 로더별 모드 수를 그립니다. MinecraftForge의 패치에는 이 호출이 없어서 Forge에서는 이 규칙이 꺼지고, `poly-bridge-forge`의 `poly:client_title_mod_counts_forge`가 대신 Forge 브랜딩 위에 그립니다.

`poly:builtin_registries_refreeze`(모든 쪽, DISABLE_RULE, `poly:content_registration` 뒤)는 `BuiltInRegistries.freeze()`의 유일한 `bindBootstrappedTagsToEmpty(Registry)` 호출을 게임 계층의 `BuiltInRegistryFreeze.bindBootstrappedTagsToEmpty`로 바꿉니다(`RedirectCall`, ADR-0026 38). 훅은 얼지 않은 레지스트리에는 바닐라처럼 태그를 묶고, 이미 언 레지스트리에서는 묶기를 건너뜁니다. Fabric API는 모드 초기화 뒤 내장 레지스트리를 한 번 더 얼리는데, NeoForge 아래에서는 NeoForge의 `GameData.freezeData`가 이미 묶고 얼렸기 때문입니다. 레지스트리를 한 번만 얼리는 실행(바닐라, Fabric만, NeoForge만)에서는 동작이 같습니다. 확인: 26.3 클라이언트·서버 jar와 NeoForge 26.2.0.93이 패치한 클라이언트 jar(`javap`).

`poly:custom_payload_encoding`(모든 쪽, DISABLE_RULE)은 `CustomPacketPayload$1.writeCap`의 유일한 `StreamCodec.encode` 호출을 게임 계층의 `HostPayloadHooks.encode`로 바꿉니다(`RedirectCall`, Mixin 단계 뒤, ADR-0026 40). 호스트 로더가 없으면 원래 호출과 같고, NeoForge가 호스트이면 NeoForge 자기 페이로드를 NeoForge 코덱으로 씁니다.

## 26.2와 다른 점

1.2.0부터 지원 버전입니다(`gradle/minecraft-versions.properties`의 `supported`). 현황은 [`docs/plans/MINECRAFT_26_3.md`](../docs/plans/MINECRAFT_26_3.md)에 있습니다. `poly-version-adapter-v26_2`를 `newMinecraftVersion -Pexperimental=true`로 복사했습니다. 26.3 jar에서 `javap`로 찾아 대상을 바꾼 규칙은 다음 하나입니다.

- `poly:client_shader_uniforms`: `com.mojang.renderpearl.frontend.FrontendRenderPass.setPipeline(CompiledRenderPipeline)`(26.3의 `RenderPass`는 인터페이스이고 이 클래스가 유일한 구현)

`BuiltInRulesTest`(게임 계층 모듈)가 모든 규칙을 26.3 jar에 적용해 봅니다. `gameVersions()`는 `26.3`뿐입니다. 핫픽스는 없습니다.

## 알려진 제한
대상 jar SHA-1은 client `2dc72797…`, server bundler `823e2250…`입니다. 26.3 이상은 새 어댑터 모듈이 필요합니다.
