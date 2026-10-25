# poly-bootstrap

## 이 모듈이 하는 일
JVM 진입점(`PolyLauncherMain`)과 Minecraft 개발 실행 작업을 제공합니다.

## 무엇을 소유하는가
`dev.polymodloader.bootstrap`, 개발용 log4j 설정 `src/dev/log4j2-dev.xml`, 작업 `runClient`/`runServer`/`serverBootSmokeTest`/`worldCreateSaveTest`/`clientWorldReloadTest`/`prepareMinecraft*`

## 무엇을 하지 않는가
로딩 로직은 다루지 않습니다(그것은 `poly-runtime-impl`).

## 의존성
`poly-runtime-impl`, `poly-version-adapter-v26_2`(runtimeOnly), slf4j-api; `polyDevMod`로 `examples:minimal-native-mod`

## 주요 진입점
`dev.polymodloader.bootstrap.PolyLauncherMain`. 시스템 속성: `poly.launchTarget`(`client`/`dedicated_server`/`game_test_server`), `poly.gameJar`, `poly.modsDir`, `poly.devModJars`, `poly.transformDumpDir`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest
```

네트워크가 필요합니다. EULA에 동의하지 않은 상태로 서버를 부팅해 예상 로그 8줄을 확인합니다.

## 대표 흐름
Gradle 작업이 launch spec을 읽어 클래스 경로(Poly 모드 로더 + Minecraft 라이브러리)와 인수를 구성합니다. 그다음 `PolyLauncherMain`이 `PolyRuntime`을 실행합니다.

## 알려진 제한
`runServer`는 사용자가 `run/server/eula.txt`에 직접 동의해야 실행됩니다. 개발 실행은 오프라인 계정을 씁니다.
