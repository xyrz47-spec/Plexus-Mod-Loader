# custom-system-mod

## 이 모듈이 하는 일
`poly:game_brand` 시스템을 교체해 Minecraft가 보고하는 브랜드를 `poly/example`로 바꿉니다.

## 무엇을 소유하는가
`com.example.poly.brand.ExampleBrandProvider`, `poly.mod.json`

## 무엇을 하지 않는가
Minecraft 클래스를 참조하지 않습니다.

## 의존성
`poly-system-api`(compileOnly)

## 주요 진입점
`ExampleBrandProvider`(`replacesSystems`에 선언, 권한 `system.provider.replace`)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:clientWorldReloadTest
```

클라이언트 로그 `poly:game_brand reported 'poly/example' to Minecraft (CLIENT)`를 확인합니다.

## 대표 흐름
Poly 모드 로더가 메타데이터의 교체 선언을 읽어 충돌이 없음을 확인한 뒤 제공자를 만듭니다. 그러면 26.2 훅이 Minecraft에 `poly/example`을 돌려줍니다.

## 알려진 제한
개발 실행(`runClient` 등)에 항상 포함되므로, 개발 중 게임 브랜드는 `poly/example`로 보입니다.
