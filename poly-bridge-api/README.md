# poly-bridge-api — Cross-Loader Compatibility SPI

`poly-bridge-api`는 Fabric, Forge, NeoForge, Quilt 등 다양한 서드파티 모드로더를 통합하기 위한 **크로스 로더 브릿지 서비스 제공자 인터페이스(SPI)** 및 진단 데이터 모델을 정의하는 모듈입니다.

---

## 1. 주요 인터페이스 및 계약

### `ModLoaderBridge` (핵심 SPI)
각 로더 브릿지(`poly-bridge-fabric`, `poly-bridge-forge`)가 구현하는 인터페이스입니다.
- **`assess(metadata, side, ModJarContents)`**: 메타데이터(와 메타데이터가 가리키는 jar 안의 파일)를 사전 분석하여 호환성 진단 결과(`BridgeAssessment`)를 반환합니다.
- **`runtimePackages()`**: 해당 로더의 런타임 API 패키지 목록을 보고합니다 (미사용 시 은닉 처리 대상).
- **`gameHost(GameHost.Request, modJars)`**: 게임을 직접 패치하고 시작하는 호스트 로더(Forge/NeoForge)의 `GameHost`를 만듭니다.
- **`hostIncompatibilities(...)`**: 호스트가 패치한 게임에서 그대로 실행할 수 없는 이 브릿지의 모드를 이유와 함께 알려 줍니다(`POLY-COMPAT-224`).

### `InjectionPointReplacement`
Forge나 NeoForge의 바이트코드 패치로 인해 이동하거나 변경된 바닐라 메서드 주입점을 런타임에 대체하기 위한 불변 데이터 모델입니다.
- `targetClass`: 대상 바닐라 클래스 (예: `net/minecraft/locale/Language`)
- `method`: 대상 메서드 명 (예: `loadDefault`)
- `replaced`: 패치가 없앤 원래 주입 대상(호출 또는 필드)
- `replacement`: 그 자리를 대신하는 대상
- `placement`: 대체 방식(`SAME_PLACE`, `AFTER_TRUE_RESULT`, `IN_METHOD`)

### 진단 데이터 모델
- **`CompatibilityStatus`**: `VERIFIED`(테스트 계획 통과), `PARTIAL`(알려진 기능 제한), `EXPERIMENTAL`(일부 실험만 확인한 경로), `BLOCKED`(실행 불가, 로드 안 함), `UNTESTED`(평가 안 함). 앞의 셋은 로드합니다.
- **`CompatibilityFinding`**: 구조화된 오류 코드(`POLY-COMPAT-...`)와 구체적인 충돌 사유를 캡슐화.

---

## 2. 검증 및 빌드

```powershell
.\gradlew.bat :poly-bridge-api:check
```
