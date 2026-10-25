# build-logic

## 이 모듈이 하는 일
저장소 공통 Gradle 설정(`poly.java-conventions`)과 Minecraft 개발 환경 플러그인(`poly.minecraft-dev`)을 Java로 구현합니다.

## 무엇을 소유하는가
`dev.polymodloader.devtools.gradle`, `...gradle.minecraft`(다운로드·검증·실행 작업), `...gradle.mod`(모드 제작 도구: `newPolyMod` 프로젝트 생성, `initPolyMod` 템플릿 이름 바꾸기, `packageModTemplate` 템플릿 zip, `polyResources` 리소스 생성, `checkPolyResources` 리소스 검사), `...gradle.release`(설치 파일 내용, `publishSdk`의 SDK 저장소 발행·검사)

## 무엇을 하지 않는가
Poly 모드 로더 런타임 코드가 아니며, 게임을 실행할 때 클래스 경로에 들어가지 않습니다.

## 의존성
Spotless Gradle 플러그인, Gson

## 주요 진입점
플러그인 ID `poly.java-conventions`, `poly.minecraft-dev`, `poly.minecraft-versions`, `poly.mod-template`(루트의 `newPolyMod`, `packageModTemplate`), `poly.sdk-repository`(루트의 `publishSdk`, ADR-0008 후속), `dev.polymodloader.mod`(모드 프로젝트용, `poly-gradle-plugin`으로 발행)

이 빌드의 `:publishSdk`는 플러그인 jar(소스 없음)와 `dev.polymodloader.mod` 마커만 SDK 저장소에 올립니다. 루트 `publishSdk`가 함께 실행합니다. `publishToMavenLocal`은 전처럼 소스와 모든 마커를 올립니다.

모드 제작 도구의 논리는 Gradle 없이 테스트할 수 있는 클래스에 있습니다: `ModProjectScaffolder`, `ModResourceGenerator`, `ModResourceChecker`. 작업 클래스(`NewPolyModTask`, `GeneratePolyResourcesTask`, `CheckPolyResourcesTask`)는 입력·출력만 연결합니다.

## 빌드·테스트
```powershell
.\gradlew.bat check
```

included build로 컴파일되며, 실제 동작은 `serverBootSmokeTest`와 `runClient`로 검증합니다. 루트 `check`는 이 모듈의 테스트를 돌리지 않으므로 `.\gradlew.bat -p build-logic check`를 따로 실행합니다. `PolyResourcesBuildTest`는 Gradle TestKit으로 `build/testkit/` 아래에 실제 모드 프로젝트를 만들어 빌드합니다.

## 대표 흐름
Mojang version manifest를 기준으로 클라이언트·라이브러리·에셋을 SHA-1로 검증하며 받고, 서버 번들러는 SHA-256으로 검증하며 추출합니다. 결과는 `build/poly/minecraft/<version>-<side>.json` launch spec에 기록합니다. `MinecraftFiles/<version>/`(또는 `-PpolyMinecraftFiles=<경로>`)에 직접 넣은 버전 JSON·jar·라이브러리가 있으면 같은 해시로 확인한 뒤 캐시로 복사하고 다운로드를 건너뜁니다(`LocalMinecraftFiles`, `MinecraftFiles/README.md`).

## 알려진 제한
Configuration cache는 검증하지 않았습니다. Mojang manifest 형식이 바뀌면 영향을 받습니다(ADR-0002).
