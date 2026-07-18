package io.github.reasure3.petrolpartjeifix.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.reasure3.petrolpartjeifix.compat.SharedFeatureMarker;
import mezz.jei.api.helpers.IModIdHelper;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.gui.recipes.RecipeCategoryTab;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = RecipeCategoryTab.class, remap = false)
public abstract class RecipeCategoryTabCompatMixin {
    @Shadow
    @Final
    private IRecipeCategory<?> category;

    @WrapOperation(
            method = "getTooltip()Lmezz/jei/common/gui/JeiTooltip;",
            at = @At(
                    value = "INVOKE",
                    target = "Lmezz/jei/api/helpers/IModIdHelper;"
                            + "getFormattedModNameComponentForModId(Ljava/lang/String;)"
                            + "Lnet/minecraft/network/chat/Component;",
                    ordinal = 0
            ),
            require = 1
    )
    private Component petrolpapetrolpartjeifix$sharedCategoryMarker(
            IModIdHelper modIdHelper,
            String originalModId,
            Operation<Component> original
    ) {
        String marker = SharedFeatureMarker.forRecipeCategory(originalModId, category);
        return original.call(modIdHelper, marker == null ? originalModId : marker);
    }
}
