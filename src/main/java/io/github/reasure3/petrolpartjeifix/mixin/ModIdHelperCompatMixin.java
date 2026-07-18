package io.github.reasure3.petrolpartjeifix.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.reasure3.petrolpartjeifix.compat.SharedFeatureMarker;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.library.helpers.ModIdHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.function.Function;

@Mixin(value = ModIdHelper.class, remap = false)
public abstract class ModIdHelperCompatMixin {
    @WrapOperation(
            method = "getModNameForTooltip(Lmezz/jei/api/ingredients/ITypedIngredient;)Ljava/util/Optional;",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/function/Function;apply(Ljava/lang/Object;)Ljava/lang/Object;",
                    ordinal = 0
            ),
            require = 1
    )
    private Object petrolpapetrolpartjeifix$sharedFeatureMarker(
            Function<?, ?> displayModId,
            Object argument,
            Operation<Object> original
    ) {
        Object jeiResult = original.call(displayModId, argument);
        if (!(argument instanceof ITypedIngredient<?> typedIngredient)) {
            return jeiResult;
        }

        String marker = SharedFeatureMarker.forTypedIngredient(typedIngredient);
        return marker == null ? jeiResult : marker;
    }
}
