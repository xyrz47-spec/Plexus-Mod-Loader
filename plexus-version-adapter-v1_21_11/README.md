# plexus-version-adapter-v1_21_11

## 이 모듈이 하는 일
Minecraft 1.21.11 전용 지식을 담습니다. 클라이언트/서버 진입점과 내장 변환 규칙(브랜드, 타이틀 화면 모드 수, 레지스트리·페이로드 처리, Native API 이벤트 훅 등 수십 개)이 있습니다.

## 무엇을 소유하는가
`dev.plexusmodloader.game.version.v1_21_11.Minecraft1_21_11Adapter`와 `META-INF/services` 등록

## 무엇을 하지 않는가
다른 버전을 지원하지 않습니다. 이 모듈 밖에 Minecraft 이름을 두지 않습니다.

## 의존성
`plexus-version-adapter-api`. Minecraft jar에는 컴파일 의존하지 않습니다. 이름은 `javap`로 검증한 문자열입니다.

## 주요 진입점
`Minecraft1_21_11Adapter`

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-bootstrap:serverBootSmokeTest
```

실제 1.21.11에서 규칙 승인과 적용을 확인합니다(`serverBootSmokeTest -PplexusMinecraft=1.21.11`). 클라이언트는 아직 실행하지 않았습니다.

## 대표 흐름
`plexus:client_brand`(CLIENT)와 `plexus:server_brand`(양쪽)가 `"vanilla"`를 `"plexus"`로 바꿉니다. 실패 정책은 DISABLE_RULE입니다.

`plexus:client_title_mod_counts`(CLIENT, DISABLE_RULE)는 타이틀 화면 `TitleScreen.extractRenderState`가 버전 글자를 그리는 유일한 `GuiGraphicsExtractor.text(Font, String, int, int, int)` 호출을 게임 계층의 `client.TitleScreenModCounts.onVersionText`로 바꿉니다(`RedirectCall`, ADR-0013). 훅은 그 줄을 그리고 그 위에 로더별 모드 수를 그립니다. MinecraftForge의 패치에는 이 호출이 없어서 Forge에서는 이 규칙이 꺼지고, `plexus-bridge-forge`의 `plexus:client_title_mod_counts_forge`가 대신 Forge 브랜딩 위에 그립니다.

## 26.1과 다른 점

**실험 버전입니다**(ADR-0019). `plexus-version-adapter-v26_1`을 복사했습니다. 다시 매핑한 1.21.11 jar에서 `javap`로 찾아 대상을 바꾼 규칙은 다음과 같습니다.

- `plexus:use_entity`: `Player.interactOn(Entity, InteractionHand)`
- `plexus:client_hud`: `Gui.render`
- `plexus:client_creative_tabs_hovered`·`_drawn`: `CreativeModeInventoryScreen.render`·`renderBg`
- `plexus:client_title_mod_counts`: `TitleScreen.render`의 `GuiGraphics.drawString`
- `plexus:client_world_painters`: `LevelRenderer.submitEntities`(`renderer.state.LevelRenderState`)
- `plexus:client_shader_uniforms`: `GlRenderPass.setPipeline`(1.21.11의 `RenderPass`는 인터페이스)
- `plexus:client_fov`: `GameRenderer.getFov`

`BuiltInRulesTest`(게임 계층 모듈)가 모든 규칙을 다시 매핑한 jar에 적용해 봅니다. `gameVersions()`는 `1.21.11`뿐입니다. 핫픽스는 없습니다.

## 알려진 제한
원본 클라이언트 jar SHA-1은 `ba2df812…`입니다. 클라이언트 전용 규칙은 창 없이 변환 계획·사전조건·바이트코드 검증·훅 시그니처까지만 확인했습니다. Fabric 모드는 아직 1.21.11에서 막지 않습니다(intermediary 이름, ADR-0019).
