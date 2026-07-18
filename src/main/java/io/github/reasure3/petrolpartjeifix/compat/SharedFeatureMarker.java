package io.github.reasure3.petrolpartjeifix.compat;

import com.petrolpark.compat.ISharedFeature;
import com.petrolpark.compat.Mods;
import com.petrolpark.compat.SharedFeatureFlag;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.world.item.ItemStack;

import java.util.stream.Collectors;

public final class SharedFeatureMarker {
    public static final String PREFIX = "petrolparkshared";

    private SharedFeatureMarker() {
    }

    public static String forTypedIngredient(ITypedIngredient<?> typedIngredient) {
        Object ingredient = typedIngredient.getIngredient();
        if (!(ingredient instanceof ItemStack stack)
                || !(stack.getItem() instanceof ISharedFeature sharedFeature)) {
            return null;
        }

        SharedFeatureFlag featureFlag = sharedFeature.getSharedFeatureFlag();
        if (!featureFlag.enabled()) {
            return null;
        }
        return create(featureFlag);
    }

    public static String forRecipeCategory(String originalModId, IRecipeCategory<?> category) {
        if (!"petrolpark".equals(originalModId)
                || !(category instanceof ISharedFeature sharedFeature)) {
            return null;
        }
        return create(sharedFeature.getSharedFeatureFlag());
    }

    private static String create(SharedFeatureFlag featureFlag) {
        String modIds = featureFlag.streamUsers()
                .map(Mods::getId)
                .collect(Collectors.joining(","));
        return modIds.isEmpty() ? null : PREFIX + ',' + modIds;
    }
}
