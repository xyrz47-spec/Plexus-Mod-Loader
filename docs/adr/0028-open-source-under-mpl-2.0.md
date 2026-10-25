# ADR-0028: 소스를 Mozilla Public License 2.0으로 공개한다

- 상태: 채택 (2026-10-25)
- 관련: ADR-0023(이름 변경), ADR-0017(런처 프로필 설치)

## 배경

1.1.0까지 Poly 모드 로더는 **PolyLoader Non-Commercial License 1.0**으로 배포했습니다. 상업적 이용을 막는 라이선스라 OSI 기준의 오픈소스가 아니었고, 2026-10-21부터는 공개 GitHub 저장소(`xyrz47-spec/Poly-Mod-Loader`)에 소스 없이 README와 릴리스만 두었습니다.

그 결과 두 가지 문제가 생겼습니다.

- Windows 설치 파일(`.exe`)에 코드 서명이 없어 SmartScreen이 경고합니다. 무료 서명 프로그램(SignPath Foundation 등)은 OSI 승인 라이선스를 쓰는 공개 프로젝트만 받고, Microsoft Artifact Signing은 한국의 개인 개발자가 쓸 수 없습니다.
- 모드 로더는 설치한 모든 모드의 코드를 실행합니다. 서명도 없고 소스도 볼 수 없는 실행 파일은 사용자에게 믿기 어렵습니다(Reddit에서 서명 요청을 받았습니다).

프로젝트 소유자가 소스를 오픈소스로 공개하고 서명을 받는 쪽을 택했습니다. 저작권자는 소유자 한 명이고(git 기록의 작성자는 `_xyrz`뿐), 기존 라이선스 9.3조가 앞으로의 버전을 다른 조건, 오픈소스 라이선스로 낼 수 있게 해 두었습니다. 함께 배포하는 제3자 코드(Fabric Loader·Quilt Loader·class-tweaker 일부, tomlj, Gson)는 모두 Apache-2.0·MIT·BSD 계열입니다.

## 결정

1. **소스 라이선스는 Mozilla Public License 2.0입니다.** `LICENSE`를 MPL 2.0 원문으로 바꿉니다. 저작권 표시는 README에 둡니다(Copyright 2026 _xyrz and the Poly Mod Loader contributors).
   - 파일 단위 카피레프트라 Poly 모드 로더 파일을 고쳐 배포하면 그 파일의 소스를 공개해야 하고, 새 파일과 모드·모드팩·런처는 자기 라이선스를 씁니다.
   - Apache-2.0 코드와 함께 쓸 수 있습니다. 상업적 이용은 허용됩니다.
   - 후보였던 LGPL-3.0은 Java에서 "링크"의 범위가 모호하고, Apache-2.0은 닫힌 개조판을 허용해 고르지 않았습니다. GPL-3.0은 모드를 파생물로 볼 소지가 있어 제외했습니다.
2. **`templates/`와 `examples/`는 MIT No Attribution(MIT-0)입니다.** 각 폴더에 `LICENSE`를 둡니다. 예전 라이선스 6.2조처럼 모드에 자유롭게 가져다 쓰게 하려는 것이고, MIT-0도 OSI 승인 라이선스입니다.
3. **이름과 로고는 라이선스에서 빼고 `TRADEMARKS.md`로 정합니다.** 예전 라이선스 7조를 옮긴 것입니다. 개조판은 자기 이름과 로고를 써야 합니다. 로고 사본을 저장소 루트 `logo.svg`에 둡니다.
4. **새 라이선스는 1.1.0 다음 버전부터입니다.** 1.1.0까지의 배포 파일은 받은 그대로 예전 라이선스를 따르고, 예전 라이선스 9.3조대로 나중에 주어지는 더 넓은 조건도 함께 씁니다. `THIRD_PARTY_NOTICES.md`(모든 jar의 `META-INF`에 들어감)에 이 사실과 소스 주소를 적어 MPL 2.0 3.2조(실행 파일 배포 시 소스 안내)를 지킵니다.
5. **공개 저장소에는 소스와 관계된 파일만 둡니다.** 개발은 계속 비공개 저장소에서 하고, `.github/scripts/export-public-repo.py`가 추적 중인 파일에서 아래를 뺀 것을 공개 저장소로 복사합니다. 공개 저장소의 `.gitignore`에도 같은 경로를 넣어 실수로 올라가지 않게 합니다.
   - 웹사이트: `site/`, `.github/workflows/docs.yml`, `docs/RELEASE_AND_DEPLOY.md`, 예전 공개용 README(`.github/public-repo/`)
   - Claude·AI 에이전트 설정: `.claude/`, `CLAUDE.md`, `AGENTS.md`, `docs/CLAUDE_SKILLS.md`
   - 비공개 쪽 도구: `.github/scripts/export-public-repo.py`, `.github/scripts/publish-release.py`
   - 설계서·ADR·계획(`docs/plans/`)·스프린트 기록은 남깁니다. 호환성 문서가 검증 근거로 이 기록들을 가리키기 때문입니다.
6. **공개 저장소 이력은 새 커밋 하나로 시작합니다.** 비공개 저장소의 개발 이력(400여 커밋)은 공개하지 않습니다. 공개 전에 내보낼 파일 전체에서 개인 경로·이메일·비밀 값·jar·Minecraft 파일이 없는지 검사합니다(2026-10-25 검사: 해당 없음).
7. **CI는 공개 저장소에서도 돕니다.** `ci.yml`의 웹사이트 빌드 단계는 `site/package.json`이 있을 때만 실행합니다.

## 결과

- 누구나 소스를 읽고, 고치고, 상업적으로도 쓸 수 있습니다. 예전 라이선스 5조(자동 분석·AI 재구현·AI 학습 제한)와 별도 상업 라이선스는 더 이상 없습니다.
- 개조판은 고친 Poly 모드 로더 파일의 소스를 공개해야 하고, "Poly Mod Loader"라는 이름과 로고를 쓸 수 없습니다.
- SignPath Foundation 등 오픈소스용 코드 서명을 신청할 수 있습니다. 신청에는 공개 저장소의 CI(`release.yml`)로 빌드한 릴리스가 필요하며, 서명 단계는 승인 뒤에 따로 더합니다.
- 웹사이트 소스는 비공개로 남습니다. 공개된 개발 문서는 `site/`와 `AGENTS.md` 대신 웹사이트 주소와 `docs/contributing/`을 가리킵니다.
- `AGENTS.md`의 "`LICENSE`를 바꾸지 않는다", "오픈소스라고 부르지 않는다"는 규칙을 이 결정에 맞게 고칩니다.
