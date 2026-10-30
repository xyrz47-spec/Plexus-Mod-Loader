package dev.plexusmodloader.compat.forge;

import static java.lang.constant.ConstantDescs.CD_Object;
import static java.lang.constant.ConstantDescs.CD_String;
import static java.lang.constant.ConstantDescs.CD_boolean;

import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.ClassTransform;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.CodeModel;
import java.lang.classfile.Instruction;
import java.lang.classfile.Label;
import java.lang.classfile.MethodModel;
import java.lang.classfile.MethodTransform;
import java.lang.classfile.Opcode;
import java.lang.classfile.TypeKind;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.classfile.instruction.LoadInstruction;
import java.lang.classfile.instruction.ReturnInstruction;
import java.lang.classfile.instruction.TypeCheckInstruction;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;
import java.lang.module.ModuleReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Lets FML's mod list answer for the mods Poly Mod Loader runs beside the host loader (ADR-0025): {@code
 * ModList.getModFileById} and {@code ModList.isLoaded} ask Poly Mod Loader's {@code ForeignModList} (in {@code
 * forge-host.jar} or {@code neoforge-host.jar}) when FML's own answer is {@code null} or {@code false}. FML's answer
 * comes first, so a Forge or NeoForge mod keeps its ID. Two FMLs have such a mod list ({@link Shape}):
 *
 * <ul>
 *   <li>MinecraftForge's ({@link Shape#FORGE}), checked with {@code javap} against {@code fmlcore} 26.2-65.1.5 and
 *       26.1-62.0.9 (identical {@code ModList}): every method is static; {@code getModFileById(String)} is {@code
 *       return (IModFileInfo) FILE_BY_ID.get(id);} and {@code isLoaded(String)} is {@code return
 *       indexedMods.containsKey(id);}. A method left unchanged is reported as {@code PLEXUS-COMPAT-211}.
 *   <li>NeoForge's FML 11 ({@link Shape#NEOFORGE}, ADR-0026), checked with {@code javap} against {@code loader}
 *       11.0.16 (NeoForge 26.2.0.93): the methods are instance methods of the one {@code ModList}; {@code
 *       getModFileById(String)} is {@code return (IModFileInfo) this.fileById.get(id);} and {@code isLoaded(String)}
 *       is {@code return this.indexedMods.containsKey(id);}. A method left unchanged is reported as {@code
 *       PLEXUS-COMPAT-218}.
 * </ul>
 *
 * <p>A method is changed only when its body is exactly the checked one; otherwise it is left as it is and named in
 * {@link Result#unchanged()}.
 *
 * <p>Not changed, on purpose: {@code getModFiles}, {@code getMods}, {@code size}, {@code getAllScanData} and the {@code
 * forEach…}/{@code apply…} methods, which FML and Forge use to load mods ({@code ModLoader}), their resource packs
 * ({@code ResourcePackLoader}) and FML's scan data ({@code CapabilityManager}); other loaders' mods would be loaded
 * twice or asked for a scan FML never did. Nor {@code
 * getModContainerById}: a {@code ModContainer} is FML's lifecycle object (loading stage, mod bus, configs, extension
 * points) and other loaders' mods are not in FML's lifecycle.
 */
final class ForgeModListPatch {

    /**
     * Where one FML's {@code ModList} keeps the two lookups.
     *
     * @param className the mod list's class
     * @param modFileInfo the type {@code getModFileById} returns
     * @param instance whether the lookups are instance methods reading instance fields (NeoForge) or static ones
     * @param fileById the map field {@code getModFileById} reads
     * @param foreignModList Poly Mod Loader's answers in that FML's host jar
     */
    record Shape(String className, ClassDesc modFileInfo, boolean instance, String fileById, ClassDesc foreignModList) {

        /** MinecraftForge's FML ({@code fmlcore}); the answers are in the BOOT layer's {@code cpw.mods.modlauncher}. */
        static final Shape FORGE = new Shape(
                "net.minecraftforge.fml.ModList",
                ClassDesc.of("net.minecraftforge.forgespi.language.IModFileInfo"),
                false,
                "FILE_BY_ID",
                ClassDesc.of(ForgeBootModules.HOST_PACKAGE + ".ForeignModList"));

        /** NeoForge's FML 11 ({@code loader}); the answers are in {@code neoforge-host.jar}'s module (ADR-0026). */
        static final Shape NEOFORGE = new Shape(
                "net.neoforged.fml.ModList",
                ClassDesc.of("net.neoforged.neoforgespi.language.IModFileInfo"),
                true,
                "fileById",
                ClassDesc.of("dev.plexusmodloader.compat.forge.fml.ForeignModList"));

        String packageName() {
            return className.substring(0, className.lastIndexOf('.'));
        }

        String classFile() {
            return className.replace('.', '/') + ".class";
        }

        ClassDesc modList() {
            return ClassDesc.of(className);
        }

        /** The local slot of the looked-up ID. */
        int idSlot() {
            return instance ? 1 : 0;
        }
    }

    /** MinecraftForge's FML mod list. */
    static final String CLASS_NAME = Shape.FORGE.className();

    /** Poly Mod Loader's answers for MinecraftForge, in the BOOT layer's {@code cpw.mods.modlauncher} module. */
    static final ClassDesc FOREIGN_MOD_LIST = Shape.FORGE.foreignModList();

    static final String GET_MOD_FILE_BY_ID = "getModFileById";
    static final String IS_LOADED = "isLoaded";

    private static final ClassDesc MAP = ClassDesc.of("java.util.Map");
    private static final MethodTypeDesc LOADED = MethodTypeDesc.of(CD_boolean, CD_String);
    private static final MethodTypeDesc MAP_GET = MethodTypeDesc.of(CD_Object, CD_Object);
    private static final MethodTypeDesc MAP_CONTAINS_KEY = MethodTypeDesc.of(CD_boolean, CD_Object);
    private static final String INDEXED_MODS = "indexedMods";

    /**
     * The result of {@link #apply}.
     *
     * @param bytes the class file to define ({@code original} when nothing changed)
     * @param changed the methods that now ask {@code ForeignModList}
     * @param unchanged the methods whose body is not the one checked, left as they are
     */
    record Result(byte[] bytes, List<String> changed, List<String> unchanged) {
        Result {
            changed = List.copyOf(changed);
            unchanged = List.copyOf(unchanged);
        }
    }

    private ForgeModListPatch() {}

    /**
     * {@code reference} as it is, or, when it is the module with MinecraftForge's FML {@code ModList} ({@code fmlcore},
     * which FML puts in the untransformed PLUGIN layer as a {@code LIBRARY}), reading {@code ModList} through {@link
     * #apply}.
     *
     * @param report gets each result, for the log
     */
    static ModuleReference patching(ModuleReference reference, ClassDesc foreignModList, Consumer<Result> report) {
        return patching(reference, Shape.FORGE, foreignModList, report);
    }

    /**
     * {@code reference} as it is, or, when it has the package of {@code shape}'s {@code ModList} (for NeoForge, FML's
     * {@code loader}, a module of the untransformed library layer), reading {@code ModList} through {@link #apply}.
     *
     * @param report gets each result, for the log
     */
    static ModuleReference patching(
            ModuleReference reference, Shape shape, ClassDesc foreignModList, Consumer<Result> report) {
        if (!reference.descriptor().packages().contains(shape.packageName())) {
            return reference;
        }
        return PatchedClassReference.of(reference, shape.classFile(), bytes -> {
            Result result = apply(bytes, shape, foreignModList);
            report.accept(result);
            return result.bytes();
        });
    }

    /**
     * MinecraftForge's {@code ModList} class file with its lookups falling back to {@code foreignModList}'s static
     * {@code getModFileById(String)IModFileInfo} and {@code isLoaded(String)Z}.
     *
     * @param original the class file of {@link #CLASS_NAME}; any other class is returned unchanged
     */
    static Result apply(byte[] original, ClassDesc foreignModList) {
        return apply(original, Shape.FORGE, foreignModList);
    }

    /**
     * {@code shape}'s {@code ModList} class file with its lookups falling back to {@code foreignModList}'s static
     * {@code getModFileById(String)IModFileInfo} and {@code isLoaded(String)Z}.
     *
     * @param original the class file of {@code shape}'s {@code ModList}; any other class is returned unchanged
     */
    static Result apply(byte[] original, Shape shape, ClassDesc foreignModList) {
        ClassFile classFile = ClassFile.of();
        ClassModel model = classFile.parse(original);
        if (!model.thisClass().asSymbol().equals(shape.modList())) {
            return new Result(original, List.of(), List.of());
        }
        List<String> changed = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        MethodTypeDesc fileType = MethodTypeDesc.of(shape.modFileInfo(), CD_String);
        check(model, shape, GET_MOD_FILE_BY_ID, fileType, code -> looksUpFile(shape, code), changed, unchanged);
        check(model, shape, IS_LOADED, LOADED, code -> looksUpContainer(shape, code), changed, unchanged);
        if (changed.isEmpty()) {
            return new Result(original, changed, unchanged);
        }
        ClassTransform transform = (builder, element) -> {
            if (element instanceof MethodModel method
                    && changed.contains(method.methodName().stringValue())) {
                boolean byId = method.methodName().equalsString(GET_MOD_FILE_BY_ID);
                MethodTransform body = (methodBuilder, part) -> {
                    if (part instanceof CodeModel) {
                        methodBuilder.withCode(code -> {
                            if (byId) {
                                fileOrForeign(code, shape, foreignModList);
                            } else {
                                loadedOrForeign(code, shape, foreignModList);
                            }
                        });
                    } else {
                        methodBuilder.with(part);
                    }
                };
                builder.transformMethod(method, body);
            } else {
                builder.with(element);
            }
        };
        return new Result(classFile.transformClass(model, transform), changed, unchanged);
    }

    private static void check(
            ClassModel model,
            Shape shape,
            String name,
            MethodTypeDesc type,
            Predicate<List<Instruction>> body,
            List<String> changed,
            List<String> unchanged) {
        List<MethodModel> found = model.methods().stream()
                .filter(method -> method.methodName().equalsString(name)
                        && method.methodTypeSymbol().equals(type)
                        && ((method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0) == shape.instance())
                .toList();
        boolean matches = found.size() == 1
                && found.getFirst()
                        .code()
                        .map(ForgeModListPatch::instructions)
                        .filter(body)
                        .isPresent();
        (matches ? changed : unchanged).add(name);
    }

    private static List<Instruction> instructions(CodeModel code) {
        return code.elementStream()
                .filter(Instruction.class::isInstance)
                .map(Instruction.class::cast)
                .toList();
    }

    /** {@code return (IModFileInfo) FILE_BY_ID.get(id);} (or {@code this.fileById}) and nothing else. */
    private static boolean looksUpFile(Shape shape, List<Instruction> code) {
        int at = readsMap(shape, code, shape.fileById());
        return at > 0
                && code.size() == at + 4
                && code.get(at) instanceof LoadInstruction id
                && id.slot() == shape.idSlot()
                && code.get(at + 1) instanceof InvokeInstruction get
                && get.opcode() == Opcode.INVOKEINTERFACE
                && get.owner().asSymbol().equals(MAP)
                && get.name().equalsString("get")
                && get.typeSymbol().equals(MAP_GET)
                && code.get(at + 2) instanceof TypeCheckInstruction cast
                && cast.opcode() == Opcode.CHECKCAST
                && cast.type().asSymbol().equals(shape.modFileInfo())
                && code.get(at + 3) instanceof ReturnInstruction done
                && done.typeKind() == TypeKind.REFERENCE;
    }

    /** {@code return indexedMods.containsKey(id);} (or {@code this.indexedMods}) and nothing else. */
    private static boolean looksUpContainer(Shape shape, List<Instruction> code) {
        int at = readsMap(shape, code, INDEXED_MODS);
        return at > 0
                && code.size() == at + 3
                && code.get(at) instanceof LoadInstruction id
                && id.slot() == shape.idSlot()
                && code.get(at + 1) instanceof InvokeInstruction contains
                && contains.opcode() == Opcode.INVOKEINTERFACE
                && contains.owner().asSymbol().equals(MAP)
                && contains.name().equalsString("containsKey")
                && contains.typeSymbol().equals(MAP_CONTAINS_KEY)
                && code.get(at + 2) instanceof ReturnInstruction done
                && done.typeKind() == TypeKind.INT;
    }

    /**
     * Where the code after reading the map {@code field} starts: 1 after {@code getstatic} (MinecraftForge), 2 after
     * {@code aload_0; getfield} (NeoForge); 0 when the code does not start by reading that map.
     */
    private static int readsMap(Shape shape, List<Instruction> code, String field) {
        if (shape.instance()) {
            return code.size() > 2
                            && code.get(0) instanceof LoadInstruction self
                            && self.slot() == 0
                            && self.typeKind() == TypeKind.REFERENCE
                            && readsField(shape, code.get(1), Opcode.GETFIELD, field)
                    ? 2
                    : 0;
        }
        return !code.isEmpty() && readsField(shape, code.get(0), Opcode.GETSTATIC, field) ? 1 : 0;
    }

    private static boolean readsField(Shape shape, Instruction instruction, Opcode opcode, String field) {
        return instruction instanceof FieldInstruction read
                && read.opcode() == opcode
                && read.owner().asSymbol().equals(shape.modList())
                && read.name().equalsString(field)
                && read.typeSymbol().equals(MAP);
    }

    private static void loadMap(CodeBuilder code, Shape shape, String field) {
        if (shape.instance()) {
            code.aload(0).getfield(shape.modList(), field, MAP);
        } else {
            code.getstatic(shape.modList(), field, MAP);
        }
    }

    /** {@code IModFileInfo file = (IModFileInfo) FILE_BY_ID.get(id); return file != null ? file : Foreign.get(id);} */
    private static void fileOrForeign(CodeBuilder code, Shape shape, ClassDesc foreignModList) {
        Label answered = code.newLabel();
        loadMap(code, shape, shape.fileById());
        code.aload(shape.idSlot())
                .invokeinterface(MAP, "get", MAP_GET)
                .checkcast(shape.modFileInfo())
                .dup()
                .ifnonnull(answered)
                .pop()
                .aload(shape.idSlot())
                .invokestatic(foreignModList, GET_MOD_FILE_BY_ID, MethodTypeDesc.of(shape.modFileInfo(), CD_String))
                .labelBinding(answered)
                .areturn();
    }

    /** {@code return indexedMods.containsKey(id) || Foreign.isLoaded(id);} */
    private static void loadedOrForeign(CodeBuilder code, Shape shape, ClassDesc foreignModList) {
        Label answered = code.newLabel();
        loadMap(code, shape, INDEXED_MODS);
        code.aload(shape.idSlot())
                .invokeinterface(MAP, "containsKey", MAP_CONTAINS_KEY)
                .dup()
                .ifne(answered)
                .pop()
                .aload(shape.idSlot())
                .invokestatic(foreignModList, IS_LOADED, LOADED)
                .labelBinding(answered)
                .ireturn();
    }
}
