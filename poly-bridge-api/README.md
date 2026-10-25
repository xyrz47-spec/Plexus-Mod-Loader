# poly-bridge-api

## 이 모듈이 하는 일
로더 브리지 SPI(`ModLoaderBridge`)와 공통 어휘(`CompatibilityStatus`, `CompatibilityFinding`, `BridgeAssessment`, `BridgedDependency`, `BridgedAccessWidening`)를 정의합니다. 브리지는 `ModJarContents`로 메타데이터가 가리키는 jar 안 파일(Fabric class tweaker 등)을 읽습니다. 브리지는 의존성·별칭·내장 ID·자기 런타임 패키지를 보고만 하고, 판정은 런타임이 로더를 넘어 합니다([ADR-0014](../docs/adr/0014-cross-loader-dependencies-and-link-check.md)).

## 무엇을 소유하는가
`dev.polymodloader.compat.bridge`

## 무엇을 하지 않는가
브리지를 찾거나 호출하지 않습니다(런타임의 `ForeignModResolver`).

## 의존성
`poly-runtime-api`, `poly-transform-api`(api: 호스트가 선언하는 `TransformRuleDefinition`)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bridge-api:check
```

호스트 로더(`GameHost`, Forge)는 패치한 게임에 맞춘 Poly 모드 로더 규칙(`transformRules()`)을 낼 수 있고(대상이 게임 jar 밖, 예를 들어 NeoForge의 universal jar에 있으면 그 jar를 `transformTargetJars()`로 넘김), 타이틀 화면 브랜딩에 더할 줄(`addBrandingLines`)을 받습니다. 둘 다 기본값은 아무것도 하지 않습니다.

## 대표 흐름
검색 단계에서 메타데이터 파일 종류로 브리지를 고릅니다. `assess` 결과가 `BLOCKED`면 기능별 진단을 출력하고, 로드할 수 있으면 게임 클래스로더에 추가한 뒤 엔트리포인트를 호출합니다.

## 알려진 제한
[ADR-0009](../docs/adr/0009-loader-bridges.md), [ADR-0014](../docs/adr/0014-cross-loader-dependencies-and-link-check.md)를 보세요.
