# poly-system-registry

## 이 모듈이 하는 일
내장 시스템 정의, 교체 충돌 해결, 제공자 생성·검증, 활성 시스템 조회를 구현합니다.

## 무엇을 소유하는가
`dev.plexusmodloader.runtime.system`(`BuiltInSystems`, `SystemDefinition`, `SystemReplacementResolver`, `SystemRegistry`, `PolyGameBrandProvider`, `SystemProblemCode`)

## 무엇을 하지 않는가
게임과 직접 연결되지 않습니다(그것은 버전 어댑터 훅). 모드를 검색하지 않습니다.

## 의존성
`poly-system-api`(api), `poly-mod-metadata`

## 주요 진입점
`new SystemReplacementResolver().resolve(mods, definitions)` → `SystemRegistry.create(definitions, result, side, modClassLoader)`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-system-registry:check
```

`SystemReplacementResolverTest` 6개, `SystemRegistryTest` 7개(잘못된 제공자 5종 포함)

## 대표 흐름
교체가 0개면 기본 구현, 1개면 그 구현입니다. 2개 이상이면 의존 관계가 있는 `override`만 인정하고, 나머지는 `PLEXUS-SYSTEM-001`입니다. 생성한 뒤에는 계약 인터페이스 구현 여부와 시스템별 검증기를 확인합니다.

## 알려진 제한
시스템 사이의 의존성과 초기화 순서는 아직 없습니다.
