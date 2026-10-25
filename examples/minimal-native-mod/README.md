# minimal-native-mod

## 이 모듈이 하는 일
가장 작은 Poly 모드입니다. common과 client 엔트리포인트가 호출됐다는 사실만 로그로 남깁니다.

## 무엇을 소유하는가
`com.example.poly.minimal`, `poly.mod.json`

## 무엇을 하지 않는가
게임 내용(블록·아이템 등)을 추가하지 않습니다. 해당 API가 아직 없습니다.

## 의존성
`poly-lifecycle-api`, slf4j-api(둘 다 compileOnly. 실행할 때는 Poly 모드 로더와 Minecraft가 제공)

## 주요 진입점
`MinimalNativeMod`(common), `MinimalNativeModClient`(client)

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest
```

스모크 테스트가 `Hello from minimal_native_mod 0.1.0 on DEDICATED_SERVER` 로그를 확인합니다.

## 대표 흐름
Poly 모드 로더가 jar의 `poly.mod.json`을 읽어 검증하고 의존성(`poly >=1.0.0 <2.0.0`)을 확인한 뒤, 엔트리포인트를 호출합니다.

## 알려진 제한
게임 객체에 접근하는 예제는 Phase 4(Native API 확대) 이후에 추가합니다.
