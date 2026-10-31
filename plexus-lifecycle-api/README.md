# plexus-lifecycle-api

## 이 모듈이 하는 일
모드가 구현하는 엔트리포인트 인터페이스와, Plexus 모드 로더가 엔트리포인트에 넘기는 `ModContext`를 정의합니다.

## 무엇을 소유하는가
`dev.plexusmodloader.api.lifecycle` 패키지

## 무엇을 하지 않는가
엔트리포인트 호출 순서와 오류 처리는 다루지 않습니다. 그것은 `plexus-runtime-impl`의 `EntrypointInvoker`가 맡습니다.

## 의존성
`plexus-runtime-api`(api)

## 주요 진입점
`CommonModInitializer`, `ClientModInitializer`, `DedicatedServerModInitializer`, `ModContext`

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-lifecycle-api:check
```

## 대표 흐름
`plexus.mod.json`의 `entrypoints.common/client/server`를 읽습니다. Plexus 모드 로더는 의존성 순서로 common 엔트리포인트를 모두 호출한 뒤, 측별 엔트리포인트를 호출합니다.

## 알려진 제한
엔트리포인트는 게임 `main` 이전에만 호출됩니다. 그 뒤의 시점(서버 시작·틱·정지, 월드 로드·저장·언로드 등)은 엔트리포인트가 아니라 이벤트로 받습니다: `ModContext.events()`에 `plexus-event-api`의 `ServerEvents`나 `plexus-game-api`의 이벤트를 등록합니다. 종료 작업은 `ModContext.onShutdown`으로 등록하며, JVM이 종료될 때 실행됩니다. 월드가 닫힐 때 할 일은 `ServerEvents.STOPPED` 같은 이벤트로 받습니다.
