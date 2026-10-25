# ADR-0005: Minecraft와 공유하는 라이브러리 버전 고정 (gson, slf4j, log4j)

- 상태: 채택 (2026-10-19)

## 문제

Poly 모드 로더 로더 코드는 게임 라이브러리와 같은 JVM 클래스 경로에서 실행됩니다(ADR-0003). Poly 모드 로더가 Minecraft와 다른 버전의 gson이나 slf4j를 가져오면, 클래스 경로에 같은 클래스가 두 버전으로 존재하게 됩니다.

## 결정

- Poly 모드 로더가 사용하는 gson·slf4j-api 버전은 **Minecraft 26.2 version manifest와 정확히 같게** 고정합니다(gson 2.14.0, slf4j-api 2.0.17). 버전은 `libs.versions.toml`에 주석과 함께 둡니다.
- Poly 모드 로더는 로깅 **API(slf4j)만** 의존합니다. 구현(log4j-core, log4j-slf4j2-impl)은 Minecraft 라이브러리가 제공합니다. 그래서 Poly 모드 로더와 게임 로그가 하나의 log4j 컨텍스트, 하나의 `logs/latest.log`에 순서대로 기록됩니다.
- 개발 실행용 log4j 설정은 `poly-bootstrap/src/dev/log4j2-dev.xml`입니다(`-Dlog4j.configurationFile`).

## 결과와 위험

- Minecraft 버전을 바꿀 때 이 고정값도 함께 검토해야 합니다.
- 모드가 다른 버전의 같은 라이브러리를 jar에 넣으면, 지금은 `GameClassLoader`가 child-first로 그 클래스를 정의합니다. 라이브러리 격리 정책은 아직 없습니다(Phase 1 이후 과제).
- 단위 테스트에는 slf4j 구현이 없어 로그가 출력되지 않습니다(NOP). 테스트는 로그가 아니라 결과값으로 검증합니다.
