# poly-runtime-api

## 이 모듈이 하는 일
모든 Poly 모드 로더 공개 API가 공유하는 기반 타입을 제공합니다. API 안정성 등급 표시(`@ApiStability`, `StabilityLevel`)와 실행 측(`GameSide`)이 여기에 있습니다.

## 무엇을 소유하는가
`dev.polymodloader.api.stability`, `dev.polymodloader.api.runtime` 패키지

## 무엇을 하지 않는가
모드 생명주기, 런타임 구현, Minecraft 타입은 다루지 않습니다. 다른 Poly 모드 로더 모듈에 의존하지 않습니다.

## 의존성
없음(JDK만)

## 주요 진입점
`ApiStability`, `StabilityLevel`, `GameSide`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-runtime-api:check
```

## 대표 흐름
모든 공개 타입에 `@ApiStability(...)`를 붙입니다. 이 등급은 JavaDoc, IntelliJ 문서 팝업, 런타임 리플렉션에 노출됩니다.

## 알려진 제한
런타임 버전·기능 감지 API(설계서 §5.3 A)는 아직 없습니다.
