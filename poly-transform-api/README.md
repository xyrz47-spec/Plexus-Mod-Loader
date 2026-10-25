# poly-transform-api

## 이 모듈이 하는 일
클래스 변환 규칙을 데이터로 선언하는 타입과 변환 오류 코드를 정의합니다(INTERNAL).

## 무엇을 소유하는가
`dev.polymodloader.internal.transform` 패키지

## 무엇을 하지 않는가
바이트코드를 직접 읽거나 쓰지 않습니다(그것은 `poly-transform-engine`). ASM 타입을 노출하지 않습니다.

## 의존성
`poly-runtime-api`(api)

## 주요 진입점
`TransformRuleDefinition`, `MethodTarget`, `MethodTransformAction`(sealed), `ReplaceReturnedStringConstant`, `TransformFailurePolicy`, `TransformProblemCode`

## 빌드·테스트
```powershell
.\gradlew.bat :poly-transform-api:check
```

동작 검증은 `poly-transform-engine` 테스트가 담당합니다.

## 대표 흐름
버전 어댑터와 모드(`poly.mod.json`)가 규칙과 접근 확대를 선언하고, 엔진이 계획을 세워 적용합니다. 문제는 `TransformProblem`·`AccessWideningProblem`으로 보고합니다.

## 알려진 제한
- 동작은 닫힌 집합입니다: 상수 교체 두 가지, 시작 훅 두 가지(`InjectStaticCallAtHead`, `InvokeHookAtHead`), `InvokeHookBeforeReturn`, `RedirectCall`.
- 같은 메서드의 규칙은 `TransformRuleDefinition.after`로 하나의 순서를 정해야 함께 적용됩니다(ADR-0013).
- 접근 확대(`AccessWidening`)는 클래스·메서드·필드의 접근 제어자만 바꿉니다.
- Mixin의 나머지 의미(인자·지역 변수 수정 등)는 없습니다.
