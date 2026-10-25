# poly-lifecycle-api

## 이 모듈이 하는 일
모드가 구현하는 엔트리포인트 인터페이스와, Poly 모드 로더가 엔트리포인트에 넘기는 `ModContext`를 정의합니다.

## 무엇을 소유하는가
`dev.polymodloader.api.lifecycle` 패키지

## 무엇을 하지 않는가
엔트리포인트 호출 순서와 오류 처리는 다루지 않습니다. 그것은 `poly-runtime-impl`의 `EntrypointInvoker`가 맡습니다.

## 의존성
`poly-runtime-api`(api)

## 주요 진입점
`CommonModInitializer`, `ClientModInitializer`, `DedicatedServerModInitializer`, `ModContext`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-lifecycle-api:check
```

## 대표 흐름
`poly.mod.json`의 `entrypoints.common/client/server`를 읽습니다. Poly 모드 로더는 의존성 순서로 common 엔트리포인트를 모두 호출한 뒤, 측별 엔트리포인트를 호출합니다.

## 알려진 제한
게임 `main` 이전에만 호출됩니다. 레지스트리·월드 같은 게임 객체가 생긴 뒤의 생명주기 이벤트는 아직 없습니다. 종료 작업은 `ModContext.onShutdown`으로 등록하며, JVM이 종료될 때 실행됩니다. 게임의 "월드 닫힘" 같은 세부 종료 시점과는 아직 연결되지 않았습니다.
