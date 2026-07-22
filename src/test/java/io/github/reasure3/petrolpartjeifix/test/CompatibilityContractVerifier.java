package io.github.reasure3.petrolpartjeifix.test;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAtNode;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableWrapOperationNode;
import io.github.reasure3.petrolpartjeifix.compat.PetrolparkMixinAnnotationAdjuster;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public final class CompatibilityContractVerifier {
    private static final String WRAP_OPERATION =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String WRAP_METHOD =
            "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;";
    private static final String OVERWRITE = "Lorg/spongepowered/asm/mixin/Overwrite;";

    private static final String PETROLPARK_MOD_HELPER_MIXIN =
            "com.petrolpark.mixin.compat.jei.client.ModIdHelperMixin";
    private static final String PETROLPARK_CATEGORY_MIXIN =
            "com.petrolpark.mixin.compat.jei.client.RecipeCategoryTabMixin";

    private static final String OLD_MOD_HELPER_HANDLER_DESCRIPTOR =
            "(Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/lang/Object;"
                    + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;";
    private static final String OLD_CATEGORY_HANDLER_DESCRIPTOR =
            "(Lmezz/jei/api/helpers/IModIdHelper;Ljava/lang/String;"
                    + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;";

    private CompatibilityContractVerifier() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected verifier mode");
        }
        switch (args[0]) {
            case "contracts" -> {
                require(args.length == 2, "contracts mode needs the classes directory");
                Path petrolparkJar = findClasspathJarContaining(PETROLPARK_MOD_HELPER_MIXIN);
                Path jeiJar = findClasspathJarContaining("mezz.jei.library.helpers.ModIdHelper");
                verifyContracts(petrolparkJar, jeiJar, Path.of(args[1]));
            }
            case "artifact" -> {
                require(args.length == 2, "artifact mode needs the patch JAR");
                verifyArtifact(Path.of(args[1]));
            }
            default -> throw new IllegalArgumentException("Unknown verifier mode: " + args[0]);
        }
    }

    private static void verifyContracts(Path petrolparkJar, Path jeiJar, Path classesDirectory) throws Exception {
        require(Files.isRegularFile(petrolparkJar), "Missing Petrolpark input: " + petrolparkJar);
        require(Files.isRegularFile(jeiJar), "Missing JEI input: " + jeiJar);

        System.out.println("Petrolpark SHA-256 " + sha256(petrolparkJar));
        System.out.println("JEI SHA-256        " + sha256(jeiJar));

        PetrolparkMixinAnnotationAdjuster adjuster = new PetrolparkMixinAnnotationAdjuster();
        Set<String> removals = new HashSet<>();
        boolean survivingWrapMethodFound = false;
        AdjustableAnnotationNode exactAnnotationForNearMiss = null;

        for (PetrolparkMixinContract contract : petrolparkContracts()) {
            ClassNode mixin = readClassFromJar(petrolparkJar, contract.mixinClassName());
            for (MethodNode method : mixin.methods) {
                if (method.visibleAnnotations == null) {
                    continue;
                }
                for (AnnotationNode rawAnnotation : method.visibleAnnotations) {
                    AdjustableAnnotationNode annotation = AdjustableAnnotationNode.fromNode(rawAnnotation);
                    AdjustableAnnotationNode adjusted = adjuster.adjust(
                            List.of(contract.targetClassName()),
                            contract.mixinClassName(),
                            method,
                            annotation
                    );

                    if (adjusted == null) {
                        removals.add(contract.mixinClassName() + '#' + method.name + method.desc
                                + ' ' + rawAnnotation.desc);
                        if (method.name.equals("petrolpark$getSharedFeatureModIds")) {
                            exactAnnotationForNearMiss = AdjustableAnnotationNode.fromNode(copy(rawAnnotation));
                        }
                    }

                    if (contract.mixinClassName().equals(PETROLPARK_MOD_HELPER_MIXIN)
                            && method.name.equals("petrolpark$formatSharedFeatureModIds")
                            && method.desc.equals("(Ljava/lang/String;"
                                    + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;")
                            && WRAP_METHOD.equals(rawAnnotation.desc)) {
                        require(adjusted != null, "The working Petrolpark getModNameForModId @WrapMethod was removed");
                        survivingWrapMethodFound = true;
                    }
                }
            }
        }

        require(removals.size() == 2, "Expected exactly two adjusted annotations, got " + removals);
        require(removals.contains(PETROLPARK_MOD_HELPER_MIXIN
                        + "#petrolpark$getSharedFeatureModIds" + OLD_MOD_HELPER_HANDLER_DESCRIPTOR + ' ' + WRAP_OPERATION),
                "Missing exact ModIdHelperMixin removal: " + removals);
        require(removals.contains(PETROLPARK_CATEGORY_MIXIN
                        + "#petrolpark$getSharedFeatureModIds" + OLD_CATEGORY_HANDLER_DESCRIPTOR + ' ' + WRAP_OPERATION),
                "Missing exact RecipeCategoryTabMixin removal: " + removals);
        require(survivingWrapMethodFound, "Did not find the working Petrolpark getModNameForModId @WrapMethod");
        PetrolparkMixinAnnotationAdjuster.verifyExpectedRemovalCount();

        require(exactAnnotationForNearMiss instanceof AdjustableWrapOperationNode,
                "No exact annotation was available for fail-closed tests");
        verifyNearMissesArePreserved(adjuster, (AdjustableWrapOperationNode) exactAnnotationForNearMiss);

        verifyJeiTargets(jeiJar);
        verifyReplacementMixins(classesDirectory);
        verifyAdjusterHasNoEarlyTargetLinks(classesDirectory);

        System.out.println("Compatibility contracts verified: 2/2 removals, 2/2 replacement targets, WrapMethod retained");
    }

    private static void verifyNearMissesArePreserved(
            PetrolparkMixinAnnotationAdjuster adjuster,
            AdjustableWrapOperationNode exactAnnotation
    ) {
        MethodNode wrongDescriptor = new MethodNode();
        wrongDescriptor.name = "petrolpark$getSharedFeatureModIds";
        wrongDescriptor.desc = OLD_CATEGORY_HANDLER_DESCRIPTOR + 'X';
        require(adjuster.adjust(
                        List.of("mezz.jei.gui.recipes.RecipeCategoryTab"),
                        PETROLPARK_CATEGORY_MIXIN,
                        wrongDescriptor,
                        exactAnnotation.copy()
                ) != null,
                "Adjuster did not fail closed for a changed handler descriptor");

        MethodNode exactHandler = new MethodNode();
        exactHandler.name = "petrolpark$getSharedFeatureModIds";
        exactHandler.desc = OLD_CATEGORY_HANDLER_DESCRIPTOR;
        AdjustableWrapOperationNode wrongAt = (AdjustableWrapOperationNode) exactAnnotation.copy();
        List<AdjustableAtNode> points = wrongAt.getAt();
        points.getFirst().setTarget("Lexample/Changed;target()V");
        wrongAt.setAt(points);
        require(adjuster.adjust(
                        List.of("mezz.jei.gui.recipes.RecipeCategoryTab"),
                        PETROLPARK_CATEGORY_MIXIN,
                        exactHandler,
                        wrongAt
                ) != null,
                "Adjuster did not fail closed for a changed @At target");
        require(PetrolparkMixinAnnotationAdjuster.removalCount() == 2,
                "Near-miss checks changed the removal count");
    }

    private static void verifyJeiTargets(Path jeiJar) throws IOException {
        ClassNode modIdHelper = readClassFromJar(jeiJar, "mezz.jei.library.helpers.ModIdHelper");
        MethodNode tooltipName = findMethod(
                modIdHelper,
                "getModNameForTooltip",
                "(Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/Optional;"
        );
        require(countCalls(
                        tooltipName,
                        "java/util/function/Function",
                        "apply",
                        "(Ljava/lang/Object;)Ljava/lang/Object;"
                ) == 1,
                "JEI ModIdHelper target is not exactly one Function.apply call");

        ClassNode categoryTab = readClassFromJar(jeiJar, "mezz.jei.gui.recipes.RecipeCategoryTab");
        MethodNode categoryTooltip = findMethod(
                categoryTab,
                "getTooltip",
                "()Lmezz/jei/common/gui/JeiTooltip;"
        );
        require(countCalls(
                        categoryTooltip,
                        "mezz/jei/api/helpers/IModIdHelper",
                        "getFormattedModNameComponentForModId",
                        "(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"
                ) == 1,
                "JEI RecipeCategoryTab target is not exactly one Component helper call");

        MethodNode componentFormatter = findMethod(
                modIdHelper,
                "getFormattedModNameComponentForModId",
                "(Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"
        );
        require(countCalls(
                        componentFormatter,
                        "mezz/jei/library/helpers/ModIdHelper",
                        "getModNameForModId",
                        "(Ljava/lang/String;)Ljava/lang/String;"
                ) == 1,
                "JEI Component formatter no longer reaches the surviving Petrolpark WrapMethod target");
    }

    private static void verifyReplacementMixins(Path classesDirectory) throws IOException {
        ReplacementContract modHelper = new ReplacementContract(
                "io.github.reasure3.petrolpartjeifix.mixin.ModIdHelperCompatMixin",
                "petrolpapetrolpartjeifix$sharedFeatureMarker",
                "(Ljava/util/function/Function;Ljava/lang/Object;"
                        + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/Object;",
                "getModNameForTooltip(Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/Optional;",
                "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        ReplacementContract category = new ReplacementContract(
                "io.github.reasure3.petrolpartjeifix.mixin.RecipeCategoryTabCompatMixin",
                "petrolpapetrolpartjeifix$sharedCategoryMarker",
                "(Lmezz/jei/api/helpers/IModIdHelper;Ljava/lang/String;"
                        + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)"
                        + "Lnet/minecraft/network/chat/Component;",
                "getTooltip()Lmezz/jei/common/gui/JeiTooltip;",
                "Lmezz/jei/api/helpers/IModIdHelper;getFormattedModNameComponentForModId("
                        + "Ljava/lang/String;)Lnet/minecraft/network/chat/Component;"
        );

        verifyReplacement(classesDirectory, modHelper);
        verifyReplacement(classesDirectory, category);
    }

    private static void verifyReplacement(Path classesDirectory, ReplacementContract contract) throws IOException {
        ClassNode mixin = readClassFromDirectory(classesDirectory, contract.className());
        MethodNode handler = findMethod(mixin, contract.handlerName(), contract.handlerDescriptor());
        AnnotationNode wrap = findAnnotation(handler.visibleAnnotations, WRAP_OPERATION);
        require(wrap != null, "Missing @WrapOperation on " + contract.handlerName());
        require(stringList(annotationValue(wrap, "method")).equals(List.of(contract.injectedMethod())),
                "Wrong injected method on " + contract.handlerName());
        require(Integer.valueOf(1).equals(annotationValue(wrap, "require")),
                "Replacement injection must declare require=1: " + contract.handlerName());

        List<AnnotationNode> atNodes = annotationList(annotationValue(wrap, "at"));
        require(atNodes.size() == 1, "Replacement must declare one @At: " + contract.handlerName());
        AnnotationNode at = atNodes.getFirst();
        require("INVOKE".equals(annotationValue(at, "value")), "Replacement @At must be INVOKE");
        require(contract.atTarget().equals(annotationValue(at, "target")),
                "Wrong replacement @At target on " + contract.handlerName());
        require(Integer.valueOf(0).equals(annotationValue(at, "ordinal")),
                "Replacement injection must pin ordinal=0");

        for (MethodNode method : mixin.methods) {
            require(findAnnotation(method.visibleAnnotations, OVERWRITE) == null
                            && findAnnotation(method.invisibleAnnotations, OVERWRITE) == null,
                    "JEI method overwrite is forbidden: " + contract.className());
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call) {
                    require(!call.owner.startsWith("com/petrolpark/mixin/compat/jei/client/"),
                            "Replacement calls a Petrolpark private mixin handler");
                }
            }
        }
    }

    private static void verifyAdjusterHasNoEarlyTargetLinks(Path classesDirectory) throws IOException {
        ClassNode adjuster = readClassFromDirectory(
                classesDirectory,
                "io.github.reasure3.petrolpartjeifix.compat.PetrolparkMixinAnnotationAdjuster"
        );
        Predicate<String> forbiddenOwner = owner -> owner.startsWith("com/petrolpark/")
                || owner.startsWith("mezz/jei/")
                || owner.equals("net/neoforged/fml/ModList")
                || owner.startsWith("net/minecraft/client/");

        for (MethodNode method : adjuster.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                String owner = switch (instruction) {
                    case MethodInsnNode call -> call.owner;
                    case FieldInsnNode field -> field.owner;
                    case TypeInsnNode type -> type.desc;
                    case LdcInsnNode ldc when ldc.cst instanceof Type type -> type.getInternalName();
                    default -> null;
                };
                require(owner == null || !forbiddenOwner.test(owner),
                        "Annotation adjuster links an early target class: " + owner);

                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    require(!forbiddenOwner.test(dynamic.bsm.getOwner()),
                            "Annotation adjuster bootstrap links an early target class");
                    for (Object argument : dynamic.bsmArgs) {
                        if (argument instanceof Handle handle) {
                            require(!forbiddenOwner.test(handle.getOwner()),
                                    "Annotation adjuster lambda links an early target class");
                        }
                    }
                }
            }
        }
    }

    private static void verifyArtifact(Path patchJar) throws Exception {
        require(Files.isRegularFile(patchJar), "Missing patch artifact: " + patchJar);
        try (ZipFile zip = new ZipFile(patchJar.toFile())) {
            Set<String> entries = new HashSet<>();
            zip.stream().map(ZipEntry::getName).forEach(entries::add);

            String servicePath = "META-INF/services/com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster";
            require(entries.contains(servicePath), "Missing MixinSquared service registration");
            String service = readText(zip, servicePath).strip();
            require(service.equals("io.github.reasure3.petrolpartjeifix.compat.PetrolparkMixinAnnotationAdjuster"),
                    "Unexpected adjuster service contents: " + service);

            require(entries.contains("petrolpapetrolpartjeifix.mixins.json"), "Missing client mixin config");
            String mixinConfig = readText(zip, "petrolpapetrolpartjeifix.mixins.json");
            require(mixinConfig.contains("ModIdHelperCompatMixin")
                            && mixinConfig.contains("RecipeCategoryTabCompatMixin")
                            && mixinConfig.contains("\"defaultRequire\": 1"),
                    "Incomplete client mixin config");

            String metadata = readText(zip, "META-INF/neoforge.mods.toml");
            require(metadata.contains("modId = \"petrolpapetrolpartjeifix\""), "Wrong mod id in metadata");
            requireClientDependency(metadata, "neoforge", "[21.1.235,21.1.300)");
            requireClientDependency(metadata, "minecraft", "[1.21.1]");
            requireClientDependency(metadata, "petrolpark", "[1.4.36]");
            requireClientDependency(metadata, "jei", "[19.39.0,19.39.1)");
            requireClientDependency(metadata, "create", "[6.0.10]");

            require(entries.contains("LICENSE") && entries.contains("LICENSE_MixinSquared") && entries.contains("NOTICE"),
                    "License or third-party notice is missing");

            List<String> nestedJars = entries.stream()
                    .filter(name -> name.startsWith("META-INF/jarjar/") && name.endsWith(".jar"))
                    .toList();
            require(nestedJars.size() == 1
                            && nestedJars.getFirst().endsWith("mixinsquared-neoforge-0.3.7-beta.3.jar"),
                    "Expected only MixinSquared NeoForge JarJar, got " + nestedJars);
            String jarJarMetadata = readText(zip, "META-INF/jarjar/metadata.json");
            require(jarJarMetadata.contains("mixinsquared-neoforge")
                            && jarJarMetadata.contains("[0.3.7-beta.3]"),
                    "MixinSquared JarJar metadata is not pinned to 0.3.7-beta.3");

            byte[] nestedMixinSquared = readBytes(zip, nestedJars.getFirst());
            Set<String> nestedEntries = nestedZipEntries(nestedMixinSquared);
            require(nestedEntries.contains("LICENSE_MixinSquared"),
                    "Nested MixinSquared license was not preserved");
            require(nestedEntries.contains("META-INF/jars/MixinSquared-0.3.7-beta.3.jar"),
                    "MixinSquared common implementation is absent from the nested platform JAR");

            List<String> forbiddenEntries = entries.stream()
                    .filter(name -> name.startsWith("com/petrolpark/")
                            || name.startsWith("mezz/jei/")
                            || name.startsWith("com/simibubi/create/")
                            || name.equals("petrolpark.mixins.json"))
                    .toList();
            require(forbiddenEntries.isEmpty(),
                    "Patch artifact contains third-party original classes/resources: " + forbiddenEntries);
        }

        System.out.println("Patch artifact verified: " + patchJar);
        System.out.println("Patch SHA-256       " + sha256(patchJar));
    }

    private static void requireClientDependency(String metadata, String modId, String versionRange) {
        String marker = "modId = \"" + modId + "\"";
        int start = metadata.indexOf(marker);
        require(start >= 0, "Missing dependency metadata for " + modId);
        int nextBlock = metadata.indexOf("[[dependencies.", start + marker.length());
        String block = metadata.substring(start, nextBlock < 0 ? metadata.length() : nextBlock);
        require(block.contains("versionRange = \"" + versionRange + "\""),
                modId + " dependency has an unexpected version range: " + versionRange);
        require(block.contains("side = \"CLIENT\""), modId + " dependency is not client-scoped");
    }

    private static List<PetrolparkMixinContract> petrolparkContracts() {
        return List.of(
                new PetrolparkMixinContract(
                        PETROLPARK_MOD_HELPER_MIXIN,
                        "mezz.jei.library.helpers.ModIdHelper"
                ),
                new PetrolparkMixinContract(
                        PETROLPARK_CATEGORY_MIXIN,
                        "mezz.jei.gui.recipes.RecipeCategoryTab"
                )
        );
    }

    private static ClassNode readClassFromJar(Path jar, String className) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            String entryName = className.replace('.', '/') + ".class";
            ZipEntry entry = zip.getEntry(entryName);
            require(entry != null, "Missing class " + className + " in " + jar);
            try (InputStream input = zip.getInputStream(entry)) {
                return readClass(input);
            }
        }
    }

    private static Path findClasspathJarContaining(String className) throws IOException {
        String classEntry = className.replace('.', '/') + ".class";
        List<Path> matches = new ArrayList<>();
        String[] classpathEntries = System.getProperty("java.class.path")
                .split(Pattern.quote(File.pathSeparator));

        for (String classpathEntry : classpathEntries) {
            Path path = Path.of(classpathEntry);
            if (!Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
                continue;
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                if (zip.getEntry(classEntry) != null) {
                    matches.add(path);
                }
            }
        }

        require(matches.size() == 1, "Expected exactly one classpath JAR containing "
                + className + ", got " + matches);
        return matches.getFirst();
    }

    private static ClassNode readClassFromDirectory(Path classesDirectory, String className) throws IOException {
        Path classFile = classesDirectory.resolve(className.replace('.', '/') + ".class");
        require(Files.isRegularFile(classFile), "Missing compiled class " + classFile);
        try (InputStream input = Files.newInputStream(classFile)) {
            return readClass(input);
        }
    }

    private static ClassNode readClass(InputStream input) throws IOException {
        ClassNode node = new ClassNode();
        new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static MethodNode findMethod(ClassNode owner, String name, String descriptor) {
        return owner.methods.stream()
                .filter(method -> method.name.equals(name) && method.desc.equals(descriptor))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Missing method " + owner.name + '#' + name + descriptor
                ));
    }

    private static long countCalls(MethodNode method, String owner, String name, String descriptor) {
        long count = 0;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof MethodInsnNode call
                    && call.owner.equals(owner)
                    && call.name.equals(name)
                    && call.desc.equals(descriptor)) {
                count++;
            }
        }
        return count;
    }

    private static AnnotationNode findAnnotation(List<AnnotationNode> annotations, String descriptor) {
        if (annotations == null) {
            return null;
        }
        return annotations.stream()
                .filter(annotation -> annotation.desc.equals(descriptor))
                .findFirst()
                .orElse(null);
    }

    private static Object annotationValue(AnnotationNode annotation, String key) {
        if (annotation.values == null) {
            return null;
        }
        for (int index = 0; index < annotation.values.size(); index += 2) {
            if (key.equals(annotation.values.get(index))) {
                return annotation.values.get(index + 1);
            }
        }
        return null;
    }

    private static List<String> stringList(Object value) {
        if (value instanceof String string) {
            return List.of(string);
        }
        if (value instanceof List<?> list) {
            List<String> strings = new ArrayList<>();
            for (Object element : list) {
                require(element instanceof String, "Expected annotation string, got " + element);
                strings.add((String) element);
            }
            return strings;
        }
        throw new IllegalStateException("Expected annotation string list, got " + value);
    }

    private static List<AnnotationNode> annotationList(Object value) {
        if (value instanceof AnnotationNode annotation) {
            return List.of(annotation);
        }
        if (value instanceof List<?> list) {
            List<AnnotationNode> annotations = new ArrayList<>();
            for (Object element : list) {
                require(element instanceof AnnotationNode, "Expected nested annotation, got " + element);
                annotations.add((AnnotationNode) element);
            }
            return annotations;
        }
        throw new IllegalStateException("Expected nested annotation list, got " + value);
    }

    private static AnnotationNode copy(AnnotationNode source) {
        AnnotationNode copy = new AnnotationNode(source.desc);
        source.accept(copy);
        return copy;
    }

    private static String readText(ZipFile zip, String entryName) throws IOException {
        return new String(readBytes(zip, entryName), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(ZipFile zip, String entryName) throws IOException {
        ZipEntry entry = zip.getEntry(entryName);
        require(entry != null, "Missing artifact entry " + entryName);
        try (InputStream input = zip.getInputStream(entry)) {
            return input.readAllBytes();
        }
    }

    private static Set<String> nestedZipEntries(byte[] zipBytes) throws IOException {
        Set<String> entries = new HashSet<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            for (ZipEntry entry; (entry = input.getNextEntry()) != null; ) {
                entries.add(entry.getName());
            }
        }
        return entries;
    }

    private static String sha256(Path path) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[16 * 1024];
            for (int read; (read = input.read(buffer)) >= 0; ) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().withUpperCase().formatHex(digest.digest());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private record PetrolparkMixinContract(String mixinClassName, String targetClassName) {
    }

    private record ReplacementContract(
            String className,
            String handlerName,
            String handlerDescriptor,
            String injectedMethod,
            String atTarget
    ) {
    }
}
