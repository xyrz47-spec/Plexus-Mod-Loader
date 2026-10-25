# 개발 환경

Poly 모드 로더 저장소 자체를 개발하는 환경입니다. 모드를 만드는 환경은 요구 사항(문서 사이트)과 IntelliJ IDEA(문서 사이트)를 보세요.

## 준비

- JDK 25. Gradle toolchain이 설치된 JDK와 `~/.jdks`를 자동으로 찾습니다.
- Gradle을 실행할 JDK: 17 이상(검증은 JDK 25). 필요하면 `JAVA_HOME`을 지정합니다.
- Git
- IntelliJ IDEA 2025.2 이상(Community 가능, 선택)

빌드 구성:

| 영역 | 내용 |
|---|---|
| Gradle | Wrapper 9.8.1(배포본 체크섬 고정), Kotlin DSL |
| 의존성 버전 | `gradle/libs.versions.toml` 한 곳 |
| 공통 설정 | `build-logic`의 `poly.java-conventions`: toolchain Java 25, `-Xlint:all -Werror`, JUnit, Spotless + palantir-java-format |

:::tip 저장소 위치

저장소는 OneDrive 같은 동기화 폴더 밖(예: 홈 폴더 아래 `dev/polymodloader`)에 두세요. 동기화 폴더에서 Gradle 캐시·Git 파일을 쓰면 잠금 충돌이 날 수 있습니다.

:::

## IntelliJ IDEA

<ol className="steps">
  <li><code>File → Open</code>으로 저장소 루트를 엽니다. Gradle 프로젝트로 인식되면 <code>Trust Project</code>를 누릅니다.</li>
  <li><code>Settings → Build, Execution, Deployment → Build Tools → Gradle</code>에서 Gradle JVM을 JDK 25(또는 17 이상)로, <em>Build and run using</em>을 <strong>Gradle</strong>(기본값)로 둡니다. 게임 실행 작업이 Gradle 작업이기 때문입니다.</li>
  <li>Gradle 동기화가 끝나면 Gradle 창에 <code>poly-*</code> 모듈들, 예제 모듈, <code>build-logic</code>이 보여야 합니다.</li>
  <li>저장소의 <code>.run/</code>에 공유 실행 구성이 있습니다: <code>check</code>, <code>runClient</code>, <code>serverBootSmokeTest</code>, <code>runServer</code>(EULA 동의 필요).</li>
  <li><code>runClient</code> 실행 구성을 디버그로 시작하면 IntelliJ가 Gradle의 게임 실행 작업에 디버거를 붙입니다. <code>PolyRuntime.launch</code>(런타임), 샘플 모드의 <code>onInitializeCommon</code>, <code>ClassTransformExecutor.transform</code>(변환 엔진)에 중단점을 걸 수 있습니다.</li>
</ol>

실행 결과와 로그는 `run/client/logs/latest.log`, `run/server/logs/latest.log`에 남습니다. 변환 전후 클래스 덤프가 필요하면 `runClient`에 `-Dpoly.transformDumpDir=<폴더>` JVM 인수를 추가합니다.

:::note 사람 확인 체크리스트

IntelliJ 화면 안에서의 동작은 아직 사람이 직접 확인하지 않았습니다. 확인했다면 결과를 알려 주세요.

- 루트를 열면 Gradle 프로젝트로 인식됨
- 모든 모듈과 의존성이 올바르게 표시됨
- 테스트를 IntelliJ에서 실행했을 때 CLI와 같은 결과
- `runClient` 실행 구성으로 게임이 뜸
- 런타임·샘플 모드·변환 엔진에서 중단점이 걸림

포매터·검사 설정은 저장소에 버전 관리되며(`build-logic`, `.editorconfig`), 개인 IDE 설정 없이 빌드됩니다(`.idea/`는 무시됨).

:::

## 알려진 주의점

- 게임 실행은 오프라인 계정이라 Realms·온라인 서버 인증 오류 로그가 나옵니다. 정상입니다.
- Minecraft 파일은 첫 실행 때 Mojang에서 받아 해시로 검증하고 `~/.gradle/caches/poly-minecraft`에 캐시합니다.
