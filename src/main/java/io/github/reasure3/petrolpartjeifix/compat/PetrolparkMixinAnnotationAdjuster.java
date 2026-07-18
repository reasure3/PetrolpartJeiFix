package io.github.reasure3.petrolpartjeifix.compat;

import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAnnotationNode;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableAtNode;
import com.bawnorton.mixinsquared.adjuster.tools.AdjustableWrapOperationNode;
import com.bawnorton.mixinsquared.api.MixinAnnotationAdjuster;
import org.objectweb.asm.tree.MethodNode;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Removes only the two Petrolpark 1.4.36 JEI injections whose invocation
 * targets no longer exist in JEI 19.39.0.368.
 *
 * <p>This service is constructed during Mixin bootstrap. Keep this class free
 * of links to Petrolpark, JEI, Minecraft, and NeoForge loader classes.</p>
 */
public final class PetrolparkMixinAnnotationAdjuster implements MixinAnnotationAdjuster {
    private static final String WRAP_OPERATION_DESCRIPTOR =
            "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final int EXPECTED_REMOVALS = 2;
    private static final AtomicInteger REMOVAL_COUNT = new AtomicInteger();

    private static final List<RemovalRule> RULES = List.of(
            new RemovalRule(
                    "com.petrolpark.mixin.compat.jei.client.ModIdHelperMixin",
                    "mezz.jei.library.helpers.ModIdHelper",
                    "petrolpark$getSharedFeatureModIds",
                    "(Lmezz/jei/api/ingredients/IIngredientHelper;Ljava/lang/Object;"
                            + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;",
                    "Lmezz/jei/library/helpers/ModIdHelper;getModNameForTooltip("
                            + "Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/Optional;",
                    "Lmezz/jei/api/ingredients/IIngredientHelper;getDisplayModId("
                            + "Ljava/lang/Object;)Ljava/lang/String;"
            ),
            new RemovalRule(
                    "com.petrolpark.mixin.compat.jei.client.RecipeCategoryTabMixin",
                    "mezz.jei.gui.recipes.RecipeCategoryTab",
                    "petrolpark$getSharedFeatureModIds",
                    "(Lmezz/jei/api/helpers/IModIdHelper;Ljava/lang/String;"
                            + "Lcom/llamalad7/mixinextras/injector/wrapoperation/Operation;)Ljava/lang/String;",
                    "Lmezz/jei/gui/recipes/RecipeCategoryTab;getTooltip()"
                            + "Lmezz/jei/common/gui/JeiTooltip;",
                    "Lmezz/jei/api/helpers/IModIdHelper;getFormattedModNameForModId("
                            + "Ljava/lang/String;)Ljava/lang/String;"
            )
    );

    public PetrolparkMixinAnnotationAdjuster() {
    }

    @Override
    public AdjustableAnnotationNode adjust(
            List<String> targetClassNames,
            String mixinClassName,
            MethodNode handlerNode,
            AdjustableAnnotationNode annotationNode
    ) {
        for (RemovalRule rule : RULES) {
            if (rule.matches(targetClassNames, mixinClassName, handlerNode, annotationNode)) {
                int removals = REMOVAL_COUNT.incrementAndGet();
                if (removals > EXPECTED_REMOVALS) {
                    throw new IllegalStateException(
                            "Removed more Petrolpark annotations than expected: " + removals
                    );
                }

                log("Removed incompatible Petrolpark @WrapOperation " + removals + "/"
                        + EXPECTED_REMOVALS + ": " + rule.describe());
                if (removals == EXPECTED_REMOVALS) {
                    verifyExpectedRemovalCount();
                }
                return null;
            }
        }
        return annotationNode;
    }

    public static int removalCount() {
        return REMOVAL_COUNT.get();
    }

    public static void verifyExpectedRemovalCount() {
        int actual = REMOVAL_COUNT.get();
        if (actual != EXPECTED_REMOVALS) {
            throw new IllegalStateException(
                    "Expected exactly " + EXPECTED_REMOVALS
                            + " Petrolpark annotation removals, got " + actual
            );
        }
        log("Verified exactly " + EXPECTED_REMOVALS
                + " incompatible Petrolpark annotations were removed");
    }

    private static void log(String message) {
        try {
            Class<?> factoryClass = Class.forName("org.slf4j.LoggerFactory");
            Object logger = factoryClass.getMethod("getLogger", String.class)
                    .invoke(null, "petrolpapetrolpartjeifix/annotation-adjuster");
            Class<?> loggerClass = Class.forName("org.slf4j.Logger");
            loggerClass.getMethod("info", String.class).invoke(logger, message);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException unavailable) {
            System.getLogger("petrolpapetrolpartjeifix/annotation-adjuster")
                    .log(Level.INFO, message);
        }
    }

    private record RemovalRule(
            String mixinClassName,
            String targetClassName,
            String handlerName,
            String handlerDescriptor,
            String injectedMethod,
            String atTarget
    ) {
        private boolean matches(
                List<String> targetClassNames,
                String actualMixinClassName,
                MethodNode handlerNode,
                AdjustableAnnotationNode annotationNode
        ) {
            if (!mixinClassName.equals(actualMixinClassName)
                    || !targetClassNames.equals(List.of(targetClassName))
                    || !handlerName.equals(handlerNode.name)
                    || !handlerDescriptor.equals(handlerNode.desc)
                    || !WRAP_OPERATION_DESCRIPTOR.equals(annotationNode.desc)
                    || !(annotationNode instanceof AdjustableWrapOperationNode wrapOperation)
                    || !wrapOperation.getMethod().equals(List.of(injectedMethod))) {
                return false;
            }

            List<AdjustableAtNode> injectionPoints = wrapOperation.getAt();
            return injectionPoints.size() == 1
                    && "INVOKE".equals(injectionPoints.getFirst().getValue())
                    && atTarget.equals(injectionPoints.getFirst().getTarget());
        }

        private String describe() {
            return mixinClassName + '#' + handlerName + handlerDescriptor
                    + " @At(target=\"" + atTarget + "\")";
        }
    }
}
