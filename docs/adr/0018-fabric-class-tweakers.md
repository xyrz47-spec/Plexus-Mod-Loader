# ADR-0018: Fabric class tweakers as Poly Mod Loader access widenings

- Status: accepted (2026-10-21)
- Related: ADR-0009 (loader bridges), ADR-0013 (access widening; point 5 amended here), ADR-0014 (link check), ADR-0015 (nested jars)

## Context
A Fabric mod names a class tweaker (access widener) with `accessWidener` in `fabric.mod.json`. Until now the Fabric bridge blocked every such mod with `POLY-COMPAT-102`. In the real-mod corpus (`compatibilityCorpus`, 2026-10-21) that blocked 26 directly installed mods and 54 nested modules. Most of them also use Mixin, so lifting `102` alone does not make them run, but the libraries and small mods that only widen access were blocked for a reason Poly Mod Loader can already handle: ADR-0013 gave the transform engine `AccessWidening` with the meaning of Fabric's access widener.

What Fabric Loader 0.19.5 does, checked with `javap` on the jar in the Gradle cache (`net.fabricmc.loader.impl.lib.classtweaker`):

- **Header.** The first line, split on whitespace, must be exactly three words: `accessWidener v1|v2 <namespace>` or `classTweaker v1|v2 <namespace>` (internal versions 1–4). The header may not carry a comment.
- **Lines.** `#` starts a comment; only in `accessWidener v1` is a commented line trimmed. An empty line is skipped. A line that starts with whitespace is an error. Words are split on `\s+` in v1 and on `[ \t]+` from v2 on.
- **Access lines.** `<access> class <c>`, `<access> method <c> <name> <desc>`, `<access> field <c> <name> <desc>`. The access is `accessible`, `extendable` or `mutable` (any case). From `accessWidener v2` on, any of them may be prefixed `transitive-`. Class names with `.` are errors. Classes and methods cannot be made mutable; fields cannot be made extendable.
- **Other directives.** From `classTweaker v1` on there is `[transitive-]inject-interface <class> <interface>`; from `classTweaker v2` on there is `[transitive-]extend-enum <class> <constant>`.
- **Meaning.**
  - A member made accessible also makes its class accessible; a member made extendable also makes its class extendable.
  - An extendable constructor becomes protected, and calls to it stay `invokespecial`.
  - A mutable static field of an interface stays final.
  - An accessible record class also gets an accessible canonical constructor.
  - Transitive and non-transitive lines apply alike at run time. `transitive-` only matters to Loom when it compiles dependent mods.
- **Namespace.** The file is read with the runtime namespace and refused if its header names another. Without mappings, which is the case for 26.x, the runtime namespace is `official`.

Every class tweaker in the corpus (73 files) uses `official`. Their headers are `accessWidener v1` (8), `accessWidener v2` (12), `classTweaker v1` (51) and `classTweaker v2` (2), with tabs and repeated spaces between the words. Six lines make a constructor extendable (Fabric API's `MenuType`, `BlockBehaviour$Properties`, `TagEntry`, and others). 189 lines inject interfaces. None extends an enum.

## Decision
1. **Bridges can read files the metadata points to.** `ModLoaderBridge.assess(metadata, side, ModJarContents)` is new. Its default calls the old two-argument method, and the runtime calls the new one with the entries of the same jar. `BridgeAssessment` gets `accessWidenings` (`BridgedAccessWidening`, `INTERNAL`). This vocabulary type keeps `poly-bridge-api` free of the transform API.
2. **The parser follows Fabric Loader's grammar exactly** (`ClassTweakerFile`), including the version-dependent rules above.
   - It reports every line it refuses, with its line number, in Fabric Loader's wording. Fabric Loader stops at the first error.
   - It is also stricter in one respect: it refuses member names and descriptors that are not valid JVM names. Fabric Loader would keep such a line and never match it, while the transform engine requires valid descriptors.
3. **Translation.** Each access line becomes a widening of the same kind and access.
   - The implied class access is added: accessible for an accessible member, extendable for an extendable member.
   - Widenings of the same target are joined, and each keeps the `file:line` that first asked for it.
   - `transitive-` lines are applied like the others.
4. **What still blocks the mod**, each with its own new code:
   - `POLY-COMPAT-125`: the named file is missing or unreadable, or has lines Fabric Loader would refuse. The finding lists `file:line message` for each.
   - `POLY-COMPAT-126`: the namespace is not `official`. For example, `intermediary` would need mappings that 26.2 does not have, and Fabric Loader refuses such a file too.
   - `POLY-COMPAT-127`: `inject-interface` or `extend-enum`, with the lines. Both change a class's shape (new interfaces, new enum constants) for code that usually also needs Mixin. Poly Mod Loader does not do either yet.
5. **Targets are checked before the mod loads, in the link check** (`ModLinkVerifier`, ADR-0014). The planner widens only members a class declares itself, so the check requires the exact class and member.
   - A class or member this game does not have blocks the mod (`POLY-COMPAT-128`, blocking, with `file:line`). Its dependents are blocked in turn, as with any other link-check failure. A class outside the game jar also blocks with `128`, because Poly Mod Loader transforms only game classes.
   - On a dedicated server, widenings of client classes are reported (`POLY-COMPAT-130`, not blocking) and left out. The server has no such classes, just as such lines never apply there under Fabric Loader.
6. **Application.** For loadable mods, `ForeignAccessWidenings` turns the widenings into engine `AccessWidening`s for the running side and game version. It adds the record canonical constructor. The failure policy is `ABORT_STARTUP`: once the link check has passed, a planner rejection would mean the two disagree, and the mod would otherwise fail later with `IllegalAccessError`. The planner joins them with Poly Mod Loader's and Poly Mods' widenings (ADR-0013). Fabric mods have no permission model, so `internal.member.access` (required of Poly Mods) does not apply to them. The class tweaker is their declaration.
7. **ADR-0013 point 5 is amended.**
   - `extendable` on a constructor is allowed. It makes the constructor at least protected and leaves calls to it as `invokespecial`.
   - `mutable` on an interface field leaves it final.
   - Engine revision 3 (cache key).
8. **`POLY-COMPAT-102` is retired.** It meant "declares a class tweaker, which Poly Mod Loader cannot apply". It is no longer emitted and the number is not reused. `POLY-COMPAT-129` (not blocking) now summarises what a class tweaker widens.

## Evidence (2026-10-21)
- `ClassTweakerFileTest` (12 tests): all four headers, whitespace and comment rules per version, every error with its line, `transitive-` per version, interface injection and enum extension per version, and implied class access.
- `FabricBridgeTest`: findings `125`–`127`, `129` and the produced widenings.
- `ModLinkVerifierTest`: declared and inherited members, a missing class, a class outside the game, client classes on a server.
- `ForeignAccessWideningsTest`: side and version, failure policy, the record constructor, client classes on a server.
- `CallSiteAndAccessTest`: an extendable constructor (protected, calls still `invokespecial`, the class verifies and runs), and an interface field that stays final.
- `fabricClassTweaker` scenario (game test server):
  - `fabric-class-tweaker` makes the private `Mth.SIN` accessible and mutable and the private `SharedConstants.prefixDebugFlagName` accessible, then uses both from its entrypoint.
  - `fabric-class-tweaker-stale` is blocked with `128`, naming both missing targets and their lines.
- Corpus (`compatibilityCorpus`, client and server):
  - All 80 class tweakers of the corpus mods (nested copies included) are read without a format, namespace or target error (`125`/`126`/`128`: 0).
  - 42 are blocked by interface injection (`127`), all of which also use Mixin. Mixin (`101`) blocks 74 of the 80 mods.
  - Mods loadable together: 17 to 18. The one added is `fabric-transitive-access-wideners-v1` inside `fabric-api`.
  - The game test server applies its 433 widenings (0 rejected) and starts normally.
  - Details are in `docs/COMPATIBILITY.md`.

## Consequences
- Fabric mods and libraries whose only blocker was a class tweaker with access lines can load. Most real mods with class tweakers also use Mixin (68 of the 73 in the corpus) and stay blocked by `101`.
- Interface injection, used by most Fabric API modules, stays blocked (`127`) until Poly Mod Loader can add interfaces to game classes.
- Differences from Fabric Loader that remain:
  - Poly Mod Loader updates only a widened class's own `InnerClasses` entry, not the entries in other classes. The JVM's run-time access checks do not read them; javac and reflection on the outer class do.
  - `invokedynamic` handles (`REF_invokeSpecial`) to a method made extendable are not turned into virtual handles.
  - Fabric Loader does both.
