# tools/tick-probe

## 이 모듈이 하는 일
메서드 하나의 실행 시간을 호출마다 재는 Java 에이전트입니다. `tickBenchmark`(서버 틱)와 `frameBenchmark`(클라이언트 프레임) 작업이 바닐라와 Poly 모드 로더 실행 양쪽에 똑같이 붙입니다(설계서 §14.4).

```
java -javaagent:tick-probe.jar=out=ticks.txt,target=net/minecraft/gametest/framework/GameTestServer.tickServer(Ljava/util/function/BooleanSupplier;)V ...
```

인수 `jfr=true`를 주면 호출마다 Java Flight Recorder 이벤트 `poly.TickProbe.Call`(`TimedCallEvent`)도 남깁니다. 이벤트는 호출이 끝날 때 기록되고 `callNanos`(걸린 시간)와 `index`(호출 순번)를 가지므로, 기록에서 느린 틱과 보통 틱을 나눠 비교할 수 있습니다. 틱 벤치마크 작업은 `-PtickBenchmarkTickEvents=true`일 때 모든 변형에 똑같이 줍니다. 기본값은 꺼짐입니다.

인수 `control=stdin`을 주면 표준 입력에서 명령을 한 줄씩 받습니다. `mark`는 지금까지 기록한 호출을 결과에서 뺍니다. `dump`는 결과 파일을 즉시 씁니다(이후 종료 시에는 다시 쓰지 않음). 클라이언트는 정상 종료 없이 끝내야 해서, `frameBenchmark`가 월드에 들어간 뒤 `mark`, 측정 시간이 지나면 `dump`를 보냅니다.

## 무엇을 소유하는가
- `TickProbeAgent`: 인수를 읽고, 대상 클래스가 정의될 때 감싸며, JVM이 끝날 때 기록을 파일로 씁니다.
- `TickWrapper`: JDK 표준 Class-File API(`java.lang.classfile`)로 대상 메서드를 감쌉니다. 원래 본문은 private 메서드 `<이름>$tickProbe`로 옮기고, 같은 이름·접근 제어자의 새 메서드가 시계 두 번 사이에 그것을 부릅니다. 원래 코드는 그대로 복사하고 새 메서드에는 분기가 없어서, 스택 맵 프레임을 계산하거나 다른 클래스를 로드할 일이 없습니다.
- `TimedCallEvent`: `jfr=true`일 때 호출 하나를 나타내는 JFR 이벤트입니다. `TickRecorder`와 함께 부트 클래스 경로 jar에 들어갑니다.
- `TickRecorder`: 호출마다 걸린 나노초를 모읍니다. 파일은 옆에 쓴 뒤 옮겨서, 반쯤 쓴 파일이 보이지 않습니다. 에이전트가 이 클래스만 담은 임시 jar를 부트 클래스 경로에 붙입니다. 그래서 Poly 모드 로더 게임 클래스로더를 포함한 모든 로더가 같은 클래스를 봅니다.

## 무엇을 하지 않는가
Poly 모드 로더의 일부가 아니며 발행하지 않습니다. 대상 메서드의 동작을 바꾸지 않습니다. 결과 분석(백분위, JFR)은 `build-logic`의 `TickBenchmarkTask`가 합니다.

## 의존성
JDK 25 표준 라이브러리만 씁니다(`java.instrument`, `java.lang.classfile`). 테스트는 JUnit입니다.

## 주요 진입점
`TickProbeAgent.premain`(jar 매니페스트 `Premain-Class`)

## 빌드·테스트
```powershell
.\gradlew.bat :tools:tick-probe:test
.\gradlew.bat :poly-bootstrap:tickBenchmark    # 실제 측정 (약 7분, 창 없음; -PtickBenchmarkHeap=2g로 힙 고정)
.\gradlew.bat :poly-bootstrap:frameBenchmark   # 클라이언트 프레임 측정 (게임 창이 12번 열림)
```
`TickWrapperTest`는 javac로 컴파일한 클래스를 감싸 로드·실행합니다. 원래 본문이 실행되는지, 호출마다 기록되는지, 접근 제어자가 유지되는지, long 인자와 반환값이 그대로 전달되는지, `mark` 뒤의 호출만 파일에 쓰이는지, `jfr=true`일 때 이벤트가 기록된 시간을 그대로 담는지 확인합니다.

## 대표 흐름
`-javaagent`로 시작 → 기록기 jar를 부트 경로에 추가 → 대상 클래스 정의 시 감싸기(표준 오류에 `[tick-probe] timing …`) → 매 호출 기록 → 종료 훅이 `out` 파일에 한 줄에 하나씩 기록

## 알려진 제한
- 대상이 로드되지 않거나 메서드가 없으면 빈 파일이 남습니다. `TickBenchmarkTask`는 `[tick-probe] timing` 줄과 최소 틱 수로 이를 실패로 처리합니다.
- 정적 메서드는 감싸지 않습니다.
- 측정 자체의 비용(호출마다 `System.nanoTime` 두 번과 배열 기록)은 양쪽에 똑같이 들어갑니다.
- `jfr=true`이면 호출마다 JFR 이벤트를 하나 더 기록합니다. 프로파일용이며, 이렇게 잰 틱 시간은 측정 결과로 쓰지 않습니다.
