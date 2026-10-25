# IntelliJ IDEA에서 Poly 모드 로더 개발하기

설계서 §6.2의 절차를 이 저장소에 맞게 적은 것입니다. 2026-10-19에 CLI(Gradle 9.8.1, JDK 25.0.3, Windows 11)에서 모든 명령을 검증했습니다. **IntelliJ 화면 안에서의 동작(가져오기, 실행 구성, 브레이크포인트)은 아직 사람이 직접 확인하지 않았습니다.** 아래 체크리스트로 확인한 뒤 결과를 이 문서에 적어 주세요.

## 준비

- IntelliJ IDEA 2025.2 이상(Community 가능). 이 PC에는 2025.2.6.2가 설치돼 있습니다.
- JDK 25. Gradle toolchain이 `C:\Program Files\Java\jdk-25.*`와 `~/.jdks`를 자동으로 찾습니다.
- Gradle JVM: JDK 17 이상이면 됩니다. 검증은 JDK 25로 했습니다.

## 절차

1. `File > Open`으로 저장소 루트(`polymodloader`)를 엽니다. Gradle 프로젝트로 인식되면 `Trust Project`를 누릅니다.
2. `Settings > Build, Execution, Deployment > Build Tools > Gradle`에서 다음을 설정합니다.
   - Gradle JVM: JDK 25(또는 17 이상)
   - Build and run using: **Gradle**(기본값). 게임 실행 작업이 Gradle 작업이기 때문입니다.
3. Gradle 동기화가 끝나면 Gradle 창에 모듈 10개(`poly-*` 9개 + `examples:minimal-native-mod`)와 `build-logic`이 보여야 합니다.
4. 저장소에 공유 실행 구성이 `.run/`에 있습니다. 실행 구성 목록에 바로 나타납니다.

   | 실행 구성 | Gradle 작업 |
   |---|---|
   | `Poly Mod Loader check` | `check` |
   | `Poly Mod Loader runClient` | `:poly-bootstrap:runClient` |
   | `Poly Mod Loader serverBootSmokeTest` | `:poly-bootstrap:serverBootSmokeTest` |
   | `Poly 모드 로더 runServer` | `:poly-bootstrap:runServer` (EULA 동의 필요) |

5. **디버깅:** `Poly 모드 로더 runClient`를 디버그(벌레 아이콘)로 시작합니다. IntelliJ가 Gradle `JavaExec` 작업에 디버거를 자동으로 붙입니다. 아래 위치에 브레이크포인트를 걸 수 있어야 합니다.
   - `PolyRuntime.launch` — Poly 모드 로더 런타임
   - `MinimalNativeMod.onInitializeCommon` — 샘플 모드
   - `ClassTransformExecutor.transform` — 변환 엔진
6. 실행 결과와 로그는 `run/client/logs/latest.log`, `run/server/logs/latest.log`에 남습니다. 변환 전후 클래스 덤프가 필요하면 `poly-bootstrap/build.gradle.kts`에서 `runClient`에 `-Dpoly.transformDumpDir=...` JVM 인수를 추가합니다.

## 새 모드 프로젝트 만들기

1. Poly 모드 로더 저장소에서 `.\gradlew.bat newPolyMod -PmodId=<id>`로 새 프로젝트를 만듭니다(저장소 없이: 웹사이트 `/maven/`의 템플릿 zip을 풀고 `initPolyMod -PmodId=<id>`). Poly 모드 로더는 웹사이트의 Maven 저장소에서 받습니다. 고친 Poly 모드 로더로 시험하려면 `publishToMavenLocal`과 `-p build-logic publishToMavenLocal` 뒤 모드 프로젝트 명령에 `-PpolyLocalSdk`를 붙입니다.
2. 만든 폴더를 IntelliJ에서 엽니다.
3. `.run/runClient`로 실행하거나 디버그합니다. `.\gradlew.bat worldCreateSaveTest`는 화면 없이 게임 테스트 서버에서 모드를 검증합니다.

## 같은 작업을 CLI에서 (PowerShell)

```powershell
.\gradlew.bat check
.\gradlew.bat :poly-bootstrap:serverBootSmokeTest
.\gradlew.bat :poly-bootstrap:runClient
.\gradlew.bat spotlessApply      # 형식 자동 수정
```

## 확인 체크리스트 (설계서 §6.3) — 사람 확인 필요

- [ ] 루트를 열면 Gradle 프로젝트로 인식됨
- [ ] 모든 모듈과 의존성이 올바르게 표시됨
- [ ] 테스트를 IntelliJ에서 실행했을 때 CLI와 같은 결과(현재 112개 통과)
- [ ] `Poly 모드 로더 runClient` 실행 구성으로 게임이 뜸
- [ ] 런타임·샘플 모드·변환 엔진에서 브레이크포인트가 걸림
- [x] 포매터·검사 설정이 저장소에 버전 관리됨(`build-logic`, `.editorconfig`)
- [x] 개인 IDE 설정 없이 빌드됨(`.idea/`는 무시되며 빌드에 필요 없음)

## 알려진 주의점

- 저장소는 OneDrive 같은 동기화 폴더 밖(예: `%USERPROFILE%\dev\polymodloader`)에 두세요. 동기화 폴더에서 Gradle 캐시·Git 파일을 쓰면 잠금 충돌이 날 수 있습니다.
- 게임 실행은 오프라인 계정이라 Realms·온라인 서버 인증 오류 로그가 나옵니다. 정상입니다.
