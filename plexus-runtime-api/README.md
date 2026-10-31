# plexus-runtime-api — Public Runtime Contracts & Stability Markers

`plexus-runtime-api`는 모든 Plexus Mod Loader 공개 API와 네이티브 모드가 공통으로 의존하는 최상위 계약 및 안정성 지시자를 정의하는 순수 자바(Pure Java) 기본 모듈입니다.

---

## 1. 주요 역할 및 설계 원칙

1. **제로 의존성 (Zero External Dependencies):**
   - 어떠한 마인크래프트 바닐라 클래스나 서드파티 라이브러리(ASM, Gson 등)에도 의존하지 않습니다.
   - JDK 25 기본 런타임 타입만으로 구성되어 모든 계층에서 안전하게 참조할 수 있습니다.
2. **API 안정성 등급 명시 (`@ApiStability`):**
   - Plexus의 모든 공개 인터페이스와 클래스에 명시되어 모드 개발자에게 API 변경 가능성을 사전 고지합니다.
   - JavaDoc, IDE 문서 툴팁, 런타임 진단 도구에서 자동으로 표시됩니다.

---

## 2. 주요 타입

### `@ApiStability` & `StabilityLevel`
- **`STABLE`**: 시맨틱 버저닝(SemVer) 메이저 판올림 전까지 하위 호환성이 엄격히 보장되는 안정적인 API.
- **`PREVIEW`**: 기능 동작은 검증되었으나 커뮤니티 피드백에 따라 시그니처나 계약이 개선될 수 있는 API.
- **`VERSION_BOUND`**: 특정 마인크래프트 버전에 묶여 있고 버전 어댑터를 통해서만 닿는 API.
- **`INTERNAL`**: Plexus Mod Loader 내부 엔진 전용. 외부 모드 개발자의 직접 호출이 권장되지 않는 API.

### `GameSide`
- `CLIENT`: 게임 클라이언트. 싱글플레이의 통합 서버도 이 프로세스에서 돕니다.
- `DEDICATED_SERVER`: 클라이언트 클래스가 없는 전용 서버

---

## 3. 검증 및 빌드

```powershell
.\gradlew.bat :plexus-runtime-api:check
```
