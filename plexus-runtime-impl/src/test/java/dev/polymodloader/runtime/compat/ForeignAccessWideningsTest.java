package dev.polymodloader.runtime.compat;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.polymodloader.api.runtime.GameSide;
import dev.polymodloader.compat.bridge.BridgeAssessment;
import dev.polymodloader.compat.bridge.BridgedAccessWidening;
import dev.polymodloader.compat.bridge.CompatibilityStatus;
import dev.polymodloader.internal.transform.AccessWidening;
import dev.polymodloader.internal.transform.TransformFailurePolicy;
import dev.polymodloader.runtime.discovery.ForeignModResolver;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.attribute.RecordAttribute;
import java.lang.classfile.attribute.RecordComponentInfo;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ForeignAccessWideningsTest {

    private static final String RECORD = "net/minecraft/world/Pos";
    private static final String PLAIN = "net/minecraft/world/Plain";

    /** {@code record Pos(int x, long y)} with its canonical constructor, and a plain class. */
    private static final Map<String, byte[]> GAME = Map.of(
            RECORD,
            ClassFile.of(ClassFile.StackMapsOption.DROP_STACK_MAPS)
                    .build(ClassDesc.of("net.minecraft.world.Pos"), cb -> {
                        cb.withSuperclass(ClassDesc.of("java.lang.Record"));
                        cb.with(RecordAttribute.of(List.of(
                                RecordComponentInfo.of("x", CD_int, List.of()),
                                RecordComponentInfo.of("y", CD_long, List.of()))));
                        cb.withMethodBody(
                                "<init>", MethodTypeDesc.of(CD_void, CD_int, CD_long), 0, CodeBuilder::return_);
                    }),
            PLAIN,
            ClassFile.of().build(ClassDesc.of("net.minecraft.world.Plain"), cb -> cb.withField("f", CD_int, 0)));

    private static ForeignModResolver.ForeignMod mod(BridgedAccessWidening... widenings) {
        BridgeAssessment assessment = BridgeAssessment.of(
                        "Fabric", "tweaked", "1.0.0", List.of(), List.of(), CompatibilityStatus.EXPERIMENTAL)
                .withAccessWidenings(List.of(widenings));
        return new ForeignModResolver.ForeignMod(Path.of("tweaked.jar"), null, assessment);
    }

    private static BridgedAccessWidening classWidening(String owner) {
        return new BridgedAccessWidening(
                "t.aw:2",
                owner,
                BridgedAccessWidening.Kind.CLASS,
                "",
                "",
                Set.of(BridgedAccessWidening.Access.ACCESSIBLE));
    }

    private static List<AccessWidening> from(GameSide side, BridgedAccessWidening... widenings) {
        return ForeignAccessWidenings.from(
                List.of(mod(widenings)), "26.2", side, name -> Optional.ofNullable(GAME.get(name)));
    }

    @Test
    void wideningsApplyToThisGameAndSideAndStopTheLaunchIfTheyCannot() {
        BridgedAccessWidening field = new BridgedAccessWidening(
                "t.aw:3",
                PLAIN,
                BridgedAccessWidening.Kind.FIELD,
                "f",
                "I",
                Set.of(BridgedAccessWidening.Access.ACCESSIBLE, BridgedAccessWidening.Access.MUTABLE));

        assertEquals(
                List.of(new AccessWidening(
                        "tweaked",
                        "26.2",
                        Set.of(GameSide.CLIENT),
                        PLAIN,
                        AccessWidening.Kind.FIELD,
                        "f",
                        "I",
                        Set.of(AccessWidening.Access.ACCESSIBLE, AccessWidening.Access.MUTABLE),
                        TransformFailurePolicy.ABORT_STARTUP)),
                from(GameSide.CLIENT, field));
    }

    /** As Fabric Loader's access widener does: an accessible record can be constructed. */
    @Test
    void anAccessibleRecordAlsoGetsAnAccessibleCanonicalConstructor() {
        List<AccessWidening> widenings = from(GameSide.CLIENT, classWidening(RECORD), classWidening(PLAIN));

        assertEquals(
                List.of("class " + RECORD, "method " + RECORD + ".<init>(IJ)V", "class " + PLAIN),
                widenings.stream().map(AccessWidening::describeTarget).toList());
    }

    /**
     * A target the game does not have is left out, as Fabric Loader applies such a line to nothing (the link check
     * reported it, PLEXUS-COMPAT-137); so is a client class on a dedicated server (PLEXUS-COMPAT-130).
     */
    @Test
    void targetsTheGameLacksAreLeftOut() {
        BridgedAccessWidening client = classWidening("net/minecraft/client/Minecraft");
        BridgedAccessWidening missingField = new BridgedAccessWidening(
                "t.aw:4",
                PLAIN,
                BridgedAccessWidening.Kind.FIELD,
                "gone",
                "I",
                Set.of(BridgedAccessWidening.Access.ACCESSIBLE));

        assertEquals(List.of(), from(GameSide.DEDICATED_SERVER, client));
        assertEquals(List.of(), from(GameSide.CLIENT, client));
        assertEquals(List.of(), from(GameSide.CLIENT, missingField));
    }
}
