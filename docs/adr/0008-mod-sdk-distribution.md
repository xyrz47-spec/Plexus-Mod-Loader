# ADR-0008: 모드 개발 SDK — Maven 발행, `dev.polymodloader.mod` 플러그인, 템플릿

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §6, §16 Phase 4

## 결정

1. **모든 Poly 모드 로더 모듈을 발행합니다.**
   - 좌표: `dev.polymodloader:<모듈명>:<버전>`. 소스 jar와 JavaDoc jar를 함께 발행합니다.
   - 공개 저장소가 정해지기 전까지는 Maven Local(`publishToMavenLocal`)을 씁니다.
2. **`build-logic`을 `dev.polymodloader:poly-gradle-plugin`으로 발행합니다.** 이 플러그인은 저장소 내부용 규약 플러그인과 모드 개발 플러그인 `dev.polymodloader.mod`를 함께 담습니다. Minecraft 다운로드·실행 작업은 `MinecraftRunTasks`로 공유하므로, 저장소 안과 밖의 실행 방식이 같습니다.
3. **`dev.polymodloader.mod`가 제공하는 것:**
   - toolchain(Java 25)
   - `compileOnly` 의존성: `poly-lifecycle-api`(이벤트·시스템 API 포함)와 SLF4J
   - `validatePolyModJson`: Poly 모드 로더의 `ModMetadataReader`로 메타데이터를 검사하며, `check`에 연결됩니다.
   - 실행·검증 작업: `runClient`, `runServer`, `worldCreateSaveTest`, `clientWorldReloadTest`, `serverBootSmokeTest`
   - 개발용 log4j 설정은 플러그인에 포함돼 있습니다.
4. **템플릿:** `templates/poly-mod-template`는 독립 Gradle 프로젝트이며, Wrapper와 IntelliJ 실행 구성을 포함합니다.
5. **JavaDoc을 `check`에 연결합니다**(`-Xdoclint:all,-missing`). 공개 API 문서 오류는 빌드를 실패시킵니다.

## 결과
템플릿을 저장소 밖 디렉터리에 복사해 `build worldCreateSaveTest`를 실행하면 다음이 순서대로 일어납니다(2026-10-19 검증).
- 플러그인과 Poly 모드 로더를 Maven Local에서 받습니다.
- 메타데이터를 검증하고 모드를 컴파일합니다.
- Minecraft 26.2 게임 테스트 서버에서 모드 엔트리포인트와 `ServerEvents.STARTED`를 실행합니다.

## 위험과 남은 일
- Maven Local에 의존하므로, 다른 PC에서는 먼저 Poly 모드 로더를 발행해야 합니다. 공개 저장소를 정하는 것은 Phase 7(공개 베타)의 일입니다.
- 예제 모드도 발행됩니다. 문제는 없지만, 공개 발행 때는 제외해야 합니다.

## 후속: 웹사이트의 공개 Maven 저장소 (2026-10-21)

소스 저장소가 비공개가 되어 Maven Local만으로는 외부 개발자가 Poly 모드를 만들 수 없습니다. 그래서 웹사이트에 정적 파일로 된 Maven 저장소 `https://polyloader.dothome.co.kr/maven/`를 둡니다(2026-10-23부터 `https://www.polymc.net/maven/`). 위 결정 1의 "모든 모듈, 소스 jar 포함"은 Maven Local에만 해당하도록 바꿉니다.

1. **공개 저장소에는 모드 프로젝트에 필요한 것만 올립니다.** 루트 플러그인 `poly.sdk-repository`의 `publishSdk`가 고릅니다.
   - 시작점은 `dev.polymodloader.mod`가 쓰는 모듈입니다: `poly-lifecycle-api`(compileOnly), `poly-bootstrap`(실행), `poly-mod-metadata`(검증), 지원하는 모든 Minecraft 버전의 `poly-version-adapter-v<id>`. 여기서 `apiElements`·`runtimeElements`의 프로젝트 의존성을 따라가 나머지를 찾습니다. 2026-10-21 기준 17개 모듈입니다(`poly-installer`, `examples`, `test-mods`, `tools` 제외). 이 넷 중 하나가 끌려오면 빌드가 실패합니다.
   - 소스 jar는 올리지 않습니다. 각 모듈에는 `maven`(Maven Local용, 소스·JavaDoc 포함)과 `sdk`(공개용) 출판물이 따로 있습니다. 좌표는 같고, `sdk`는 Maven Local로, `maven`은 SDK 저장소로 가지 않습니다.
   - JavaDoc jar는 모드가 컴파일하는 API에만 둡니다: `poly-lifecycle-api`와 그 `api` 의존성(`poly-runtime-api`, `poly-system-api`, `poly-event-api`, `poly-game-api`). JavaDoc은 `--no-fonts`로 만들어 모듈마다 약 3.8 MB의 웹 글꼴을 뺍니다.
   - Gradle 플러그인 `dev.polymodloader:poly-gradle-plugin`(소스 없음)과 마커 `dev.polymodloader.mod:dev.polymodloader.mod.gradle.plugin`만 올립니다. 저장소 내부용 플러그인 ID의 마커는 올리지 않습니다. build-logic은 included build이므로 루트 `publishSdk`가 build-logic의 `:publishSdk`를 함께 실행합니다.
   - 모드 템플릿 `dev.polymodloader:poly-mod-template:<버전>@zip`(`packageModTemplate`)도 올립니다. 압축을 푼 뒤 `initPolyMod`로 이름을 바꿉니다. 이름 바꾸기는 `newPolyMod`와 같은 `ModProjectScaffolder`를 씁니다.
2. **저장소 폴더는 버전을 쌓아 둡니다.** 기본 `<저장소>/.poly/sdk-maven`(git 무시, `clean`이 지우지 않음), 또는 `-PpolySdkRepo=<폴더>`. Gradle의 파일 저장소 발행이 `maven-metadata.xml`에 새 버전을 더하므로 이전 버전 목록이 유지됩니다(두 버전을 연달아 발행해 확인).
3. **`verifySdkRepository`가 발행 뒤 저장소를 검사합니다.** 빠진 모듈, `-sources.jar`, jar 안의 `.java`(템플릿 zip의 `src/` 예제는 제외), 기대와 다른 JavaDoc, 저장소 밖의 Poly 모드 로더 의존성, 파일 없는 Gradle 메타데이터 variant(클래스 폴더 variant가 발행되면 컴파일 클래스 경로가 비게 됨), 다른 Poly 모드 로더 산출물(테스트 모드 등)을 찾으면 실패합니다.
4. **웹사이트 배포:** `site/scripts/build-site.js`가 저장소 폴더(`POLY_SDK_REPO`, `deploy.local.json`의 `sdkRepo`, 기본 `../.poly/sdk-maven`)를 `maven/`으로 복사하고 `maven/index.html`과 MIME 형식을 정하는 `maven/.htaccess`를 씁니다. `deploy-site.js`는 `maven/` 아래 파일을 지우지 않습니다. 저장소 폴더가 없는 빌드(CI 등)에서도 서버의 사본과 목록은 그대로입니다. `maven-metadata.xml`은 그 버전의 파일 다음, 페이지보다 먼저 올립니다.
5. **모드 프로젝트의 저장소 순서:** 공개 저장소(`dev.polymodloader` 그룹만), Maven Local, Maven Central/Gradle Plugin Portal. Gradle은 처음 찾은 저장소의 것을 쓰므로, 이미 공개된 버전을 로컬 빌드로 시험하려면 `-PpolyLocalSdk`로 Maven Local을 앞에 둡니다(`PolyModPlugin`과 템플릿 `settings.gradle.kts`).

### 검증 (2026-10-21)
- `publishSdk`를 임시 폴더로 실행: 17개 모듈 + 플러그인 + 마커 + 템플릿, 410개 파일, 2.8 MB(jar 1.3 MB, JavaDoc 1.4 MB). `-sources.jar`와 jar 안의 `.java`는 없습니다. 버전을 바꿔 다시 발행하면 `maven-metadata.xml`에 두 버전이 남습니다.
- 그 폴더를 `python -m http.server`로 제공하고, 저장소 밖에 푼 템플릿에서 init 스크립트로 `Poly 모드 로더` 저장소 주소를 바꾸고 Maven Local을 지운 채(`-Dmaven.repo.local`도 빈 폴더) `initPolyMod`, `build`, `gameTests`(2개 통과), `worldCreateSaveTest`를 실행했습니다. 빈 Maven Local 폴더는 끝까지 비어 있었습니다.
- `deploy-site.js`를 로컬 FTP 서버(pyftpdlib)에 대해 실행: `maven/`이 없는 빌드를 올려도 `maven/`의 412개 파일과 다른 사람의 파일이 남고, 다시 `maven/`이 있는 빌드를 올리면 바뀐 파일만 올립니다.
- build-logic 테스트: `SdkRepositoryCheckTest`, `ModProjectScaffolderTest`(제자리 이름 바꾸기, 충돌 시 그대로 둠), `ModTemplatePluginTest`.

### 위험과 남은 일
- 닷홈 서버에는 아직 올리지 않았습니다. `.htaccess`의 `AddType`과 `.module`·`.sha512` 같은 확장자를 닷홈이 그대로 내주는지는 첫 배포 때 확인합니다.
- 공개 저장소에 연결할 수 없으면(오프라인, 서버 오류) Gradle은 Maven Local로 넘어가지 않고 실패합니다. 오프라인에서는 `--offline`을 씁니다.
- 공개 저장소의 jar는 소스는 없지만 디컴파일할 수 있습니다. 런타임 jar를 내보내는 이상 피할 수 없습니다.
