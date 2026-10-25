# ADR-0002: 빌드와 개발 환경 — Gradle 9.8.1, build-logic, 자체 Minecraft 개발 플러그인

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §6, §7.4, §21-1·2·4·11

## 문제

IntelliJ와 CLI 양쪽에서 같은 빌드·테스트·게임 실행이 되어야 합니다(§6.3). 동시에 Minecraft 파일을 저장소에 넣지 않고 재현 가능하게 받아야 합니다.

## 결정

| 영역 | 선택 | 이유 |
|---|---|---|
| Gradle | Wrapper 9.8.1, `distributionSha256Sum` 고정 | 2026-10-19 현재 최신이며 Java 25에서 실행을 확인했습니다. 체크섬으로 배포본 변조를 막습니다. |
| 스크립트 | Kotlin DSL, 버전은 `gradle/libs.versions.toml` | 설계서 §6.1, §6.4 |
| 공통 설정 | `build-logic` included build의 **Java로 작성한** 규약 플러그인 `poly.java-conventions` | 프로젝트 언어와 같습니다. 모듈마다 설정을 반복하지 않습니다. |
| 컴파일 | toolchain Java 25, `-Xlint:all -Werror` | 경고가 쌓이지 않게 합니다. |
| 테스트 | JUnit 6.1.3 (JUnit Platform) | §6.1의 "JUnit 5 기반" 요구의 후속 메이저입니다. API는 같은 Jupiter입니다. |
| 형식 | Spotless 8.10.4 + palantir-java-format 2.102.0, `check`에 연결 | §7.4. 120열 Java 형식을 자동 적용합니다. |
| 게임 개발 환경 | 자체 플러그인 `poly.minecraft-dev` | 아래 참고 |

### `poly.minecraft-dev`를 직접 만든 이유

Fabric Loom이나 NeoGradle은 각 로더의 실행 구조(Knot, FML)와 매핑 처리를 전제합니다. Poly 모드 로더는 자체 부트스트랩과 클래스로더를 쓰므로(ADR-0003) 그 도구를 그대로 쓸 수 없습니다. 26.x는 난독화가 없어서 리매핑도 필요 없으므로, 필요한 기능이 작습니다.

- Mojang version manifest를 따라 클라이언트 jar, 라이브러리(OS 규칙 적용), 에셋을 받고 **모든 파일을 manifest의 SHA-1로 검증**합니다.
- 서버 번들러 jar에서 실제 서버 jar와 라이브러리를 꺼내고 **SHA-256으로 검증**합니다.
- 파일은 `~/.gradle/caches/poly-minecraft`에 캐시해 여러 체크아웃이 공유합니다. 저장소에는 아무것도 넣지 않습니다.
- 작업: `prepareMinecraftClient`, `prepareMinecraftServer`, `runClient`, `runServer`, `serverBootSmokeTest`.

## 위험

- 이 플러그인은 Mojang manifest 형식에 의존합니다. 형식이 바뀌면 `LibraryRules`와 `MinecraftDistributionInstaller`를 고쳐야 합니다.
- 설정 캐시(configuration cache)는 아직 켜지 않았습니다. 실행 작업이 실행 시점에 launch spec을 읽는 구조라 별도 검증이 필요합니다.
- palantir-java-format이 새 Java 문법을 늦게 지원하면 `spotlessCheck`가 막힐 수 있습니다. 그때는 포매터 버전만 따로 올립니다.

## 라이선스

ASM(BSD-3), Gson(Apache-2.0), SLF4J(MIT), JUnit(EPL-2.0), Spotless(Apache-2.0), palantir-java-format(Apache-2.0). Minecraft 파일은 Mojang EULA를 따르며 재배포하지 않습니다.
