# plexus-system-api

## 이 모듈이 하는 일
교체 가능한 Plexus 모드 로더 시스템의 공개 계약을 정의합니다. 시스템 ID, 제공자, 조회, 그리고 첫 시스템 `plexus:game_brand`의 계약이 있습니다.

## 무엇을 소유하는가
`dev.plexusmodloader.api.system`(`SystemId`, `PlexusSystem`, `SystemProvider`, `SystemContext`, `SystemLookup`), `...system.brand.GameBrandSystem`

## 무엇을 하지 않는가
교체를 결정하거나 구현체를 만들지 않습니다(그것은 `plexus-system-registry`). Minecraft 타입을 노출하지 않습니다.

## 의존성
`plexus-runtime-api`(api)

## 주요 진입점
모드: `SystemProvider<T>`를 구현하고 `plexus.mod.json`의 `replacesSystems`에 선언합니다. 조회는 `ModContext.systems().get(Contract.class)`로 합니다.

## 빌드·테스트
```powershell
.\gradlew.bat :plexus-system-api:check
```

## 대표 흐름
모드 선언 → 충돌 해결 → 제공자 생성·검증 → 버전 어댑터 훅이 `SystemLookup`으로 활성 구현을 사용합니다.

## 알려진 제한
시스템은 아직 `plexus:game_brand` 하나입니다. 확장(`EXTEND`)은 없고 전체 교체만 지원합니다.
