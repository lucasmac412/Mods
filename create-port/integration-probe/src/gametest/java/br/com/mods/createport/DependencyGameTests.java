package br.com.mods.createport;

import com.electronwill.nightconfig.core.CommentedConfig;
import io.github.tropheusj.milk.Milk;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;

public final class DependencyGameTests {
    @GameTest(structure = "create_dependency_tests:empty")
    public void milkUsesRealRegisteredFluidsAndReloadedTags(GameTestHelper helper) {
        Milk.enableMilkFluid();
        helper.assertTrue(BuiltInRegistries.FLUID.getKey(Milk.STILL_MILK).getNamespace().equals("milk-lib"),
                "The adapter must expose real registered 26.2 fluids");
        helper.assertTrue(Milk.STILL_MILK.isSame(Milk.FLOWING_MILK), "Still and flowing milk belong to one fluid");
        helper.assertTrue(Milk.STILL_MILK.defaultFluidState().is(Milk.MILK_FLUID_TAG), "Milk tag must survive datapack loading");
        helper.succeed();
    }

    @GameTest(structure = "create_dependency_tests:empty")
    public void transferOfMilkCommitsAndRollsBack(GameTestHelper helper) {
        var tank = new SingleVariantStorage<FluidVariant>() {
            @Override protected FluidVariant getBlankVariant() { return FluidVariant.blank(); }
            @Override protected long getCapacity(FluidVariant variant) { return 2 * FluidConstants.BUCKET; }
        };
        var milk = FluidVariant.of(Milk.STILL_MILK);
        try (var tx = Transaction.openOuter()) {
            helper.assertTrue(tank.insert(milk, FluidConstants.BUCKET, tx) == FluidConstants.BUCKET, "Insert one bucket");
            tx.commit();
        }
        try (var tx = Transaction.openOuter()) {
            helper.assertTrue(tank.extract(milk, FluidConstants.BUCKET, tx) == FluidConstants.BUCKET, "Extract tentatively");
        }
        helper.assertTrue(tank.amount == FluidConstants.BUCKET, "Aborted extraction restores the real fluid");
        try (var tx = Transaction.openOuter()) {
            tank.extract(milk, FluidConstants.BUCKET, tx);
            tx.commit();
        }
        helper.assertTrue(tank.amount == 0, "Committed extraction empties the storage");
        helper.succeed();
    }

    @GameTest(structure = "create_dependency_tests:empty")
    public void configLoadsAndCorrectsInvalidKineticSpeed(GameTestHelper helper) {
        helper.assertTrue(ProbeMod.SPEC.isLoaded(), "Server config must load through the actual library");
        helper.assertTrue(ProbeMod.MAX_SPEED.get() == 256, "Default rotation speed");
        var input = CommentedConfig.inMemory();
        input.set("kinetics.maxRotationSpeed", -100);
        helper.assertFalse(ProbeMod.SPEC.isCorrect(input), "Invalid speed is rejected");
        ProbeMod.SPEC.correct(input);
        helper.assertTrue(input.<Integer>get("kinetics.maxRotationSpeed") == 16,
                "Invalid speed is clamped to the allowed lower bound; actual=" + input.get("kinetics.maxRotationSpeed"));
        helper.succeed();
    }
}
