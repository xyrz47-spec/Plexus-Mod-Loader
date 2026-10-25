package dev.polymodloader.tools.apireference;

/**
 * The words {@link MarkdownDoclet} writes around the Javadoc text, in one site language. The Javadoc
 * itself is English and is copied as written in every language.
 */
record ReferenceLabels(
        String typeCount,
        String generatedTitle,
        String generatedNote,
        String typesHeader,
        String stability,
        String componentsHeader,
        String constantsHeader,
        String fieldsHeader,
        String supertypes,
        String parametersHeader,
        String returns,
        String throwsLabel,
        String inheritedDoc,
        String functionalInterface,
        String interfaceKind,
        String annotationKind,
        String enumKind,
        String recordKind,
        String abstractClassKind,
        String classKind,
        String since,
        String sinceEarliest,
        String unreleased,
        String changesTitle,
        String changesSidebar,
        String changesIntro,
        String changesNone,
        String changesEarliest,
        String functionsTitle,
        String functionsSidebar,
        String functionsIntro,
        String functionsHeader,
        String constructorReturns,
        String functionCount) {

    static final ReferenceLabels ENGLISH = new ReferenceLabels(
            "%d types",
            "Generated",
            "This page is generated from the source's Javadoc by `./gradlew apiReference`. Do not edit it;"
                    + " fix the source comment and generate it again.",
            "| Type | Kind | Description |",
            "Stability",
            "| Component | Type | Description |",
            "| Constant | Description |",
            "| Constant or field | Type | Description |",
            "Supertypes",
            "| Parameter | Type | Description |",
            "Returns",
            "Throws",
            "(see the supertype's description)",
            "functional interface",
            "interface",
            "annotation",
            "enum",
            "record",
            "abstract class",
            "class",
            "Since",
            "%s or earlier",
            "next release",
            "API additions by release",
            "Additions by release",
            "The public types and members each Poly Mod Loader release added, newest first, generated from the release"
                    + " tags by `./gradlew :tools:api-reference:apiSince` and `./gradlew apiReference`. Each type and"
                    + " member on the package pages shows the same release next to **Since**.",
            "These releases added no public API: %s.",
            "Everything else in the API was already in %s, the first release tag; it may be older.",
            "All functions",
            "All functions",
            "Every public constructor and method of the API in one list, by package: what it returns, the first sentence"
                    + " of its description, and the release that added it. The name links to its full entry: signature,"
                    + " every parameter, the return value, the exceptions, when to call it and on which thread.",
            "| Function | Returns | Description | Since |",
            "new instance",
            "%d functions");

    static final ReferenceLabels KOREAN = new ReferenceLabels(
            "%d개 형식",
            "자동 생성",
            "이 페이지는 소스의 Javadoc에서 `./gradlew apiReference`로 만든 것입니다. 설명은 코드에 적힌 영어 그대로입니다."
                    + " 직접 고치지 말고 소스의 주석을 고친 뒤 다시 만드세요.",
            "| 형식 | 종류 | 설명 |",
            "안정성",
            "| 구성 요소 | 타입 | 설명 |",
            "| 상수 | 설명 |",
            "| 상수·필드 | 타입 | 설명 |",
            "상위 형식",
            "| 인자 | 타입 | 설명 |",
            "반환",
            "예외",
            "(상위 형식의 설명 참고)",
            "함수형 인터페이스",
            "인터페이스",
            "애너테이션",
            "열거형",
            "레코드",
            "추상 클래스",
            "클래스",
            "추가된 버전",
            "%s 또는 그 이전",
            "다음 릴리스",
            "릴리스별 추가 API",
            "릴리스별 추가 API",
            "Poly 모드 로더 릴리스마다 새로 생긴 공개 형식과 멤버입니다. 최신 릴리스가 위에 있습니다. 릴리스 태그에서"
                    + " `./gradlew :tools:api-reference:apiSince`와 `./gradlew apiReference`로 만듭니다. 패키지 페이지의 각"
                    + " 형식과 멤버에도 **추가된 버전**으로 같은 릴리스가 표시됩니다.",
            "다음 릴리스에는 추가된 공개 API가 없습니다: %s.",
            "나머지 API는 모두 첫 릴리스 태그인 %s에 이미 있었습니다. 그보다 오래됐을 수 있습니다.",
            "모든 함수",
            "모든 함수",
            "API의 공개 생성자와 메서드를 패키지별로 한 목록에 모았습니다. 반환 타입, 설명의 첫 문장, 추가된 버전이 있습니다."
                    + " 이름을 누르면 전체 설명(시그니처, 모든 인자, 반환값, 예외, 호출 시점과 스레드)으로 갑니다.",
            "| 함수 | 반환 | 설명 | 추가된 버전 |",
            "새 인스턴스",
            "함수 %d개");

    /** The labels for a site locale: {@code en} or {@code ko}. */
    static ReferenceLabels forLocale(String locale) {
        return switch (locale) {
            case "en" -> ENGLISH;
            case "ko" -> KOREAN;
            default -> throw new IllegalArgumentException("No API reference labels for locale " + locale);
        };
    }
}
