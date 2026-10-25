# ADR-0001: 첫 대상 버전 — Minecraft 26.2, Java 25

- 상태: 채택 (2026-10-19)
- 관련: 설계서 §16 Phase 0, §21-3

## 문제

설계서는 Minecraft 버전을 임의로 확정하지 말라고 합니다(§3.3, §6.1). 하지만 부트스트랩, 변환 대상 검증, 통합 테스트를 하려면 정확히 하나의 버전이 필요합니다.

## 고려한 선택지

1. **26.2** — 프로젝트 소유자가 직접 지정한 버전입니다. 로컬 런처에 이미 설치돼 있었습니다.
2. 26.3 — 2026-10-19 기준 최신 안정판입니다(Fabric meta 기준).
3. 26.3-pre / 26.4 스냅샷 — 변동성이 커서 제외했습니다.

## 결정

**26.2를 첫 대상으로 합니다.** 프로젝트 소유자가 2026-10-19에 직접 지정했습니다.

검증한 사실(모두 Mojang 배포물에서 직접 확인):

| 항목 | 값 | 출처 |
|---|---|---|
| 요구 Java | `javaVersion.majorVersion = 25` | 26.2 version JSON |
| 클래스 파일 버전 | major 69 (Java 25) | `javap -v net.minecraft.client.ClientBrandRetriever` |
| 클라이언트 jar | SHA-1 `2dc72797acbc1b63fc16a11c4ac393605f453754` | version JSON `downloads.client` |
| 서버 jar(번들러) | SHA-1 `823e2250d24b3ddac457a60c92a6a941943fcd6a` | version JSON `downloads.server` |
| 난독화 | **없음**. `net/minecraft/client/Minecraft.class` 등 원래 이름으로 배포 | jar 목록, Mojang 공지(26.1부터 난독화 제거) |
| 클라이언트 진입점 | `net.minecraft.client.main.Main` | version JSON `mainClass` |
| 서버 진입점 | `net.minecraft.server.Main` | 서버 번들러 `META-INF/main-class` |
| 공유 라이브러리 | gson 2.14.0, slf4j-api 2.0.17, log4j 2.26.0, LWJGL 3.4.1 | version JSON `libraries` |

## 결과

- 매핑 계층이 필요 없습니다. 변환 대상은 Mojang 원래 이름과 JVM descriptor로 직접 지정합니다. 설계서 §5.5의 "mapping namespace"는 26.x에서는 `official` 하나입니다.
- 26.2는 이미 최신이 아닙니다(26.3 출시). 다음 버전 지원은 새 어댑터 모듈(`poly-version-adapter-v26_3`)을 추가하는 방식으로 하고, 26.2 어댑터의 규칙을 자동으로 재사용하지 않습니다.
- Java 25 toolchain이 모든 모듈에 적용됩니다.

## 되돌리거나 바꿀 조건

- 생태계(Fabric/NeoForge)의 지원 중심이 26.3 이상으로 옮겨가 브리지 실험이 26.2에서 의미가 없어질 때.
- 26.2에서만 발생하는 차단 문제가 발견될 때.

## 후속 (2026-10-21): 지원 버전 확장
기준 버전은 그대로 26.2입니다(`default`). [ADR-0011](0011-multiple-minecraft-versions.md)의 구조로 26.1, 26.1.1, 26.1.2도 지원합니다. 셋 다 난독화되지 않았고 Java 25(`javaVersion.majorVersion = 25`)입니다. 클라이언트 jar SHA-1은 `191771837687b766537a8c4607cb6fad79c533a1`(26.1), `377031a9e733ba8ab4d355959a8f6fb8eb707556`(26.1.1), `4e618f09a0c649dde3fdf829df443ce0b8831e65`(26.1.2)입니다. 지원 목록은 `gradle/minecraft-versions.properties`가 기준입니다.
