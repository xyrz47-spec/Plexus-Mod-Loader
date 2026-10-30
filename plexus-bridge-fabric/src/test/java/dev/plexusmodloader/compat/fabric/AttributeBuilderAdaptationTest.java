package dev.plexusmodloader.compat.fabric;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.plexusmodloader.api.runtime.GameSide;
import dev.plexusmodloader.compat.bridge.GameClassBytes;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Fabric API's {@code AttributeSupplierBuilderAccessor} is an {@code @Accessor} for {@code AttributeSupplier$Builder}'s
 * field {@code builder}, an {@code ImmutableMap.Builder} in vanilla. Forge and NeoForge make that field a {@code Map};
 * only then does {@link FabricClassTransformer} add a {@code getBuilder()} that wraps it. From 1.2.2 it added one on
 * vanilla too, and Mixin then refused the accessor ("cannot overwrite method ... because @Overwrite is required"):
 * a Fabric dedicated server with Fabric API stopped in {@code Bootstrap.validate} (corpusFabricServer, 26.2 and 26.3,
 * 2026-10-27).
 */
class AttributeBuilderAdaptationTest {

    private static final String BUILDER = "net.minecraft.world.entity.ai.attributes.AttributeSupplier$Builder";
    private static final String ACCESSOR = "net.fabricmc.fabric.mixin.object.builder.AttributeSupplierBuilderAccessor";

    @Test
    void vanillaKeepsItsBuilderForFabricsAccessor() {
        byte[] vanilla = builderClass("Lcom/google/common/collect/ImmutableMap$Builder;");

        assertArrayEquals(vanilla, transformer().transform(BUILDER, vanilla));
    }

    @Test
    void aHostsMapFieldGetsAGetBuilder() {
        byte[] host = builderClass("Ljava/util/Map;");

        byte[] transformed = transformer().transform(BUILDER, host);

        assertTrue(ClassFile.of().parse(transformed).methods().stream()
                .anyMatch(method -> method.methodName().equalsString("getBuilder")
                        && method.methodType().equalsString("()Lcom/google/common/collect/ImmutableMap$Builder;")));
    }

    private static FabricClassTransformer transformer() {
        GameClassBytes classes = new GameClassBytes() {
            @Override
            public Optional<byte[]> classBytes(String className) {
                return className.equals(ACCESSOR) ? Optional.of(new byte[] {1}) : Optional.empty();
            }

            @Override
            public boolean isDefined(String className) {
                return className.equals(ACCESSOR);
            }
        };
        return new FabricClassTransformer(
                classes,
                new EnvironmentStripper(GameSide.DEDICATED_SERVER, Set.of()),
                InterfaceInjector.of(List.of()),
                EnumExtender.of(List.of()),
                Optional.empty(),
                Optional.empty());
    }

    /** {@code AttributeSupplier$Builder} with its field {@code builder} of type {@code fieldDescriptor}. */
    private static byte[] builderClass(String fieldDescriptor) {
        return ClassFile.of().build(ClassDesc.of(BUILDER), builder -> {
            builder.withFlags(ClassFile.ACC_PUBLIC);
            builder.withField(
                    "builder", ClassDesc.ofDescriptor(fieldDescriptor), ClassFile.ACC_PRIVATE | ClassFile.ACC_FINAL);
            builder.withMethodBody(
                    ConstantDescs.INIT_NAME, MethodTypeDesc.of(ConstantDescs.CD_void), ClassFile.ACC_PUBLIC, code -> {
                        code.aload(0);
                        code.invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME, ConstantDescs.MTD_void);
                        code.return_();
                    });
        });
    }
}
