package io.github.tropheusj.milk;

import net.george.milk.MilkLib;
import net.george.milk.MilkLibTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;

/** Actual 26.2 fluid objects behind the four old Create Milk API call sites. */
public final class Milk {
    public static final FlowingFluid STILL_MILK = MilkLib.STILL_MILK;
    public static final FlowingFluid FLOWING_MILK = MilkLib.FLOWING_MILK;
    public static final TagKey<Fluid> MILK_FLUID_TAG = MilkLibTags.MILK;

    private Milk() { }

    public static void enableMilkFluid() {
        // The new library always registers milk. Never register a second fluid under
        // a fake legacy ID; recipes and transfer must use the same real objects.
        if (STILL_MILK == null || FLOWING_MILK == null)
            throw new IllegalStateException("Milk Lib 26.2 fluids were not initialized");
    }
}
