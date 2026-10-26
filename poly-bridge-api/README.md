# poly-bridge-api — Cross-Loader Compatibility SPI

`poly-bridge-api`는 Fabric, Forge, NeoForge, Quilt 등 다양한 서드파티 모드로더를 통합하기 위한 **크로스 로더 브릿지 서비스 제공자 인터페이스(SPI)** 및 진단 데이터 모델을 정의하는 모듈입니다.

---

## 1. 주요 인터페이스 및 계약

### `ModLoaderBridge` (핵심 SPI)
각 로더 브릿지(`poly-bridge-fabric`, `poly-bridge-forge`)가 구현하는 인터페이스입니다.
- **`assess(ModJarContents)`**: jar 내부의 메타데이터와 클래스 구조를 사전 분석하여 호환성 진단 결과(`BridgeAssessment`)를 반환합니다.
- **`runtimePackages()`**: 해당 로더의 런타임 API 패키지 목록을 보고합니다 (미사용 시 은닉 처리 대상).
- **`createGameHost()`**: 호스트 로더(Forge/NeoForge) 전용 런타임 가상화 핸들러를 생성합니다.

### `InjectionPointReplacement`
Forge나 NeoForge의 바이트코드 패치로 인해 이동하거나 변경된 바닐라 메서드 주입점을 런타임에 대체하기 위한 불변 데이터 모델입니다.
- `targetClass`: 대상 바닐라 클래스 (예: `net/minecraft/locale/Language`)
- `targetMethod`: 대상 메서드 명 (예: `loadDefault`)
- `targetCall`: 이동 전 원래 호출 시그니처
- `replacementCall`: 이동 후 호스트 로더의 패치 호출 시그니처

### 진단 데이터 모델
- **`CompatibilityStatus`**: `COMPATIBLE`(호환), `ADAPTED`(패치 적응 구동), `INCOMPATIBLE`(비호환 제외)
- **`CompatibilityFinding`**: 구조화된 오류 코드(`POLY-COMPAT-...`)와 구체적인 충돌 사유를 캡슐화.

---

## 2. 검증 및 빌드

```powershell
.\gradlew.bat :poly-bridge-api:check
```
