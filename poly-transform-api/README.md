# poly-transform-api — Declarative Bytecode Transform Contract

`poly-transform-api`는 마인크래프트 바이트코드 변환 규칙과 접근성 확장(Access Widening), 그리고 변환 검증 오류 코드를 데이터 모델로 선언하는 내부 인터페이스 모듈입니다.

---

## 1. 설계 원칙 및 책임

1. **ASM 의존성 차단:**
   - 바이트코드를 직접 다루는 ASM 라이브러리 타입(`ClassNode`, `MethodNode` 등)을 일절 노출하지 않습니다.
   - 변환 의도(Method Hook, Static Redirect, Constant Replacement, Access Widening)를 순수 Java 불변 데이터 레코드(`Record`, `Sealed Interface`)로 추상화합니다.
2. **선언적 변환 규칙 (Declarative Rules):**
   - 모드와 버전 어댑터는 "어떤 클래스의 어떤 메서드에 어떤 훅을 거는가"를 불변 객체(`TransformRuleDefinition`)로 선언하며, 실제 바이트코드 조작은 `poly-transform-engine`이 전담합니다.
3. **엄격한 실패 정책 (`TransformFailurePolicy`):**
   - `ABORT`(변환 중단 및 게임 종료), `DISABLE_RULE`(해당 규칙만 비활성화), `IGNORE` 정책을 지원하여 안전한 실패(Fail-safe)를 지원합니다.

---

## 2. 주요 선언 모델

- **`TransformRuleDefinition`**: 규칙 ID, 타겟 클래스, 타겟 메서드, 적용 액션, 실패 정책 및 선행 규칙(`after`) 정의.
- **`MethodTransformAction`** (Sealed Interface):
  - `InjectStaticCallAtHead`: 메서드 시작 지점에 정적 메서드 호출 주입
  - `InvokeHookAtHead`: 컨텍스트/인자를 포함한 시작 훅 호출
  - `InvokeHookBeforeReturn`: `RETURN` 직전 반환 훅 호출
  - `RedirectCall`: 특정 메서드 호출 지점(Call site)을 대체 메서드로 리다이렉트
  - `ReplaceReturnedStringConstant`: 문자열 상수 반환값 치환
- **`AccessWidening`**: `public` 확장 및 `final` 제거를 선언하는 접근 제어자 확장 모델.

---

## 3. 검증 및 빌드

```powershell
.\gradlew.bat :poly-transform-api:check
```
실제 바이트코드 변환 엔진의 통합 테스트는 `poly-transform-engine`에서 수행됩니다.
