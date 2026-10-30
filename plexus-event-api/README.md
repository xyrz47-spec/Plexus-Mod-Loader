# plexus-event-api

## 이 모듈이 하는 일
모드가 게임 이벤트를 받는 공개 API를 정의합니다. 타입이 있는 이벤트 키, 리스너 등록, 서버 생명주기 이벤트로 이루어집니다.

## 무엇을 소유하는가
`dev.plexusmodloader.api.event`(`EventKey`, `EventRegistry`, `EventPriority`, `EventResult`), `...event.server`(`ServerEvents`, `ServerView`, 리스너 5종)

## 무엇을 하지 않는가
이벤트를 직접 발생시키지 않습니다. 발생은 버전 어댑터 훅과 `plexus-runtime-impl`의 `EventBus`가 맡습니다. Minecraft 타입을 노출하지 않습니다.

## 의존성
`plexus-runtime-api`(api)

## 주요 진입점
`ModContext.events().register(ServerEvents.STARTED, server -> ...)`

우선순위: `ModContext.events().register(BlockEvents.BREAK, EventPriority.HIGH, (world, pos, block, player) -> EventResult.DENY)`

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-runtime-impl:test --tests *EventBusTest
.\gradlew.bat :plexus-bootstrap:worldCreateSaveTest
```

## 대표 흐름
`MinecraftServer.tickServer` 진입 훅이 서버마다 첫 틱에 `STARTED`를, 그 뒤 매 틱 `TICK`을 발생시킵니다. `stopServer` 진입 훅은 `STOPPING`을 발생시킵니다. `STARTING`(월드를 만들기 전)과 `STOPPED`(월드를 닫은 뒤)는 게임 계층의 `LifecycleHooks`가 발생시킵니다. 리스너가 예외를 던지면 그 리스너만 비활성화하고 `PLEXUS-EVENT-001`로 보고합니다.

리스너는 `EventPriority`(`HIGHEST`→`LOWEST`) 순서로, 같은 우선순위 안에서는 등록 순서로 실행됩니다. 결정 이벤트(리스너가 `EventResult`나 `InteractionResult`를 돌려주는 이벤트)는 처음으로 `PASS`가 아닌 값을 돌려준 리스너에서 끝나고, 그 뒤 리스너는 호출되지 않습니다. 결정 리스너가 `null`을 돌려주면 `PLEXUS-EVENT-003`으로 보고하고 비활성화합니다.

## 알려진 제한
이 모듈의 이벤트는 서버 생명주기뿐입니다. 게임 이벤트(블록·엔티티·플레이어·월드)는 `plexus-game-api`에 있습니다. NeoForge의 `receiveCanceled`(이미 취소된 이벤트를 받아 취소를 되돌리는 리스너)는 표현할 수 없습니다. `ServerView.dedicated()`는 Minecraft가 보고하는 값이라, 게임 테스트 서버는 `false`입니다.
