# Phase 5 결과 — Fabric 및 Forge 브리지 기술 확장 (1단계)

- 완료일: 2026-10-19
- 기준: 설계서 §16 Phase 5
- 결정: [ADR-0009](../adr/0009-loader-bridges.md)

## 작업 항목

| 항목 | 상태 | 내용 |
|---|---|---|
| 각 브리지의 지원 범위를 기능별로 확장 | ⚠️ 1단계 | Fabric은 엔트리포인트(`main`/`client`/`server`, 기본 어댑터)를 실행합니다. NeoForge·MinecraftForge는 진단만 합니다 |
| 메타데이터, 초기화, 주요 API, 변환 방식 대응 | ⚠️ 부분 | 메타데이터는 세 로더 모두 해석합니다. 초기화는 Fabric 엔트리포인트만 대응합니다. Fabric API, FML 이벤트 버스, Mixin, AT, class tweaker는 미대응이며 기능별 코드로 보고합니다 |
| 각 모드 유형의 단독 실행 테스트 | ✅ | `FabricBridgeTest` 4개, `FmlModsTomlBridgeTest` 2개(`check`), 실제 게임 검증에 Fabric 테스트 모드 포함 |
| 호환성 결과를 버전별로 기록 | ✅ | `docs/COMPATIBILITY.md`, 시작 보고서 `foreignMods` |

## 완료 기준

| 기준 | 상태 | 근거 |
|---|---|---|
| 명시적으로 선정한 샘플 모드가 정의된 범위에서 실행 | ⚠️ 자체 픽스처 | `fabric_hello`(Fabric 규약대로 작성한 테스트 모드)가 26.2 클라이언트와 게임 테스트 서버에서 실행됩니다. **실제 배포된 서드파티 Fabric 모드는 아직 실행되지 않습니다.** 대부분 Fabric API나 Mixin에 의존하기 때문입니다 |
| 미지원 기능과 제약이 자동 진단으로 나타남 | ✅ | 실제 Fabric API, NeoForge, MinecraftForge jar에서 기능별 `POLY-COMPAT-1xx/2xx` 확인 |
| 각 브리지의 독립 테스트가 CI에서 실행 | ✅ | 브리지 단위 테스트가 `check`에 포함 |

## 실행한 검증 (2026-10-19)
- `.\gradlew.bat check`: 성공. 테스트 98개 통과
- `serverBootSmokeTest`, `worldCreateSaveTest`, `clientWorldReloadTest`: 통과(Fabric 엔트리포인트 로그 포함)
- 실제 jar 3종 진단: 위 ADR 표

## 남은 일
설계서 Phase 5의 완전한 목표(서드파티 Fabric/Forge 모드 실행)를 이루려면 Mixin 서비스, 중첩 jar, Fabric API 재구현 또는 연동, FML 런타임 대응이 필요합니다. 순서는 ADR-0009의 "다음 단계"를 따릅니다.
