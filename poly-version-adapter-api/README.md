# poly-version-adapter-api

## 이 모듈이 하는 일
Minecraft 버전마다 다른 지식(게임 진입점, 내장 변환 규칙)을 담는 어댑터 계약을 정의합니다.

## 무엇을 소유하는가
`dev.polymodloader.game.version.GameVersionAdapter`, 게임 계층 계약 `dev.polymodloader.game.version.layer`(`GameLayer`, `GameLayerContext`, `LoadedModCounts`, `ModCountLines` 등)

`ModCountLines`는 타이틀 화면에 보이는 로더별 모드 수 줄(Poly·Fabric·Quilt·Forge 순서, 모드가 없는 로더는 줄 없음)과 그 번역 키(`poly.title.mod_count.*`)를 정합니다. 번역은 `src/main/resources/assets/poly/lang/`의 `en_us.json`, `ko_kr.json`에 있고, 언어 파일이 없거나 글자가 형식에 맞지 않으면 영어 줄을 씁니다.

## 무엇을 하지 않는가
특정 버전의 클래스·메서드 이름을 담지 않습니다.

## 의존성
`poly-runtime-api`, `poly-transform-api`(api)

## 주요 진입점
`GameVersionAdapter`(`ServiceLoader`로 탐색)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-version-adapter-api:check
```

계약 사용은 `PolyRuntimeTest`의 `TestGameVersionAdapter`로 검증합니다. `ModCountLinesTest`는 두 언어의 줄과 영어 대체를 검증합니다.

## 대표 흐름
런타임은 게임 jar의 `version.json` id가 `gameVersion()`과 정확히 같은 어댑터를 고릅니다. 없으면 `POLY-BOOTSTRAP-002`.

## 알려진 제한
버전 범위 매칭은 없고, 정확한 버전만 매칭합니다.
