package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.content.DragonCreativeTab;
import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Real creative inventory output and block-use paths, distinct from the incubated survival egg. */
public final class DragonCreativeGameTests {
    private static final String ARENA = "celestial_dragon_gametest:arena";

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void creativeTabDisplaysEveryRegisteredModItemWithoutDuplicates(GameTestHelper helper) {
        var tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(DragonCreativeTab.KEY);
        helper.assertTrue(tab != null, "The dedicated dragon tab must be registered");
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(helper.getLevel().enabledFeatures(), false,
                helper.getLevel().registryAccess()));
        Set<Item> registered = BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(CelestialDragonMod.ID))
                .collect(Collectors.toSet());
        Set<Item> displayed = tab.getDisplayItems().stream().map(ItemStack::getItem).collect(Collectors.toSet());
        helper.assertTrue(registered.size() >= 12, "Both new spawn eggs must be registered alongside survival content");
        helper.assertTrue(displayed.equals(registered), "Every registered dragon item must appear in the dedicated tab");
        helper.assertTrue(tab.getDisplayItems().size() == displayed.size(), "The tab cannot duplicate block items");
        helper.assertTrue(tab.shouldDisplay() && tab.hasAnyItems(), "A nonempty tab must be visible in creative inventory");
        Set<Item> searchable = tab.getSearchTabDisplayItems().stream().map(ItemStack::getItem).collect(Collectors.toSet());
        helper.assertTrue(searchable.containsAll(registered), "Dragon content must also be searchable");
        helper.succeed();
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void creativeEggsSpawnWildAndPlayerOwnedDragons(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.CREATIVE);
        try {
            ItemStack wildEgg = new ItemStack(DragonContent.WILD_DRAGON_SPAWN_EGG, 2);
            ItemStack tameEgg = new ItemStack(DragonContent.TAMED_DRAGON_SPAWN_EGG, 2);
            helper.assertTrue(SpawnEggItem.spawnsEntity(wildEgg, CelestialDragonMod.DRAGON)
                    && SpawnEggItem.spawnsEntity(tameEgg, CelestialDragonMod.DRAGON),
                    "Both creative eggs must carry vanilla spawn-egg entity components");
            BlockPos wildPos = helper.absolutePos(new BlockPos(12, 2, 12));
            BlockPos tamedPos = helper.absolutePos(new BlockPos(32, 2, 32));
            helper.assertTrue(useOnGround(helper, player, wildEgg, wildPos).consumesAction(), "Wild egg must create a dragon");
            var wild = helper.getLevel().getEntities(CelestialDragonMod.DRAGON, d -> wildPos.equals(d.getNest()));
            helper.assertTrue(wild.size() == 1 && !wild.getFirst().isTamed() && wild.getFirst().getOwnerId() == null,
                    "Wild creative egg must create exactly one unowned wild dragon");
            wild.getFirst().setNoAi(true);
            helper.assertTrue(useOnGround(helper, player, tameEgg, tamedPos).consumesAction(), "Tamed egg must create a dragon");
            var tamed = helper.getLevel().getEntities(CelestialDragonMod.DRAGON, d -> tamedPos.equals(d.getNest()));
            helper.assertTrue(tamed.size() == 1 && tamed.getFirst().isTamed()
                            && player.getUUID().equals(tamed.getFirst().getOwnerId()),
                    "Tamed creative egg must assign the authenticated player as owner before spawning");
            tamed.getFirst().setNoAi(true);
            helper.assertTrue(wildEgg.getCount() == 2 && tameEgg.getCount() == 2,
                    "Creative use must preserve both egg stacks");
            wild.forEach(d -> d.discard());
            tamed.forEach(d -> d.discard());
            helper.succeed();
        } finally {
            removePlayer(helper, player);
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void creativeEggCannotBypassSurvivalIncubation(GameTestHelper helper) {
        // The deprecated server mock hard-codes gameMode() to CREATIVE, even after setGameMode.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            helper.assertFalse(player.isCreative(), "This rejection must exercise a real Survival-mode player");
            helper.assertTrue(player.getAbilities().mayBuild, "Survival must reach item use instead of Adventure restrictions");
            ItemStack egg = new ItemStack(DragonContent.TAMED_DRAGON_SPAWN_EGG, 2);
            BlockPos spawn = helper.absolutePos(new BlockPos(16, 2, 16));
            helper.assertTrue(useOnGround(helper, player, egg, spawn) == InteractionResult.FAIL,
                    "A creative test egg cannot skip incubation in Survival");
            helper.assertTrue(egg.getCount() == 2, "Rejected use cannot consume the egg");
            helper.assertTrue(helper.getLevel().getEntities(CelestialDragonMod.DRAGON, d -> spawn.equals(d.getNest())).isEmpty(),
                    "Rejected Survival use cannot create an owned dragon");
            helper.succeed();
        } finally {
            player.discard();
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void creativeEggWaitsForAnOpenArea(GameTestHelper helper) {
        ServerPlayer player = player(helper, GameType.CREATIVE);
        try {
            BlockPos spawn = helper.absolutePos(new BlockPos(16, 2, 16));
            helper.getLevel().setBlockAndUpdate(spawn.above(), Blocks.STONE.defaultBlockState());
            ItemStack egg = new ItemStack(DragonContent.WILD_DRAGON_SPAWN_EGG, 2);
            helper.assertTrue(useOnGround(helper, player, egg, spawn) == InteractionResult.FAIL,
                    "A blocked spawn must be rejected using the dragon's real collision bounds");
            helper.assertTrue(egg.getCount() == 2, "Blocked spawning cannot consume the egg");
            helper.assertTrue(helper.getLevel().getEntities(CelestialDragonMod.DRAGON, d -> spawn.equals(d.getNest())).isEmpty(),
                    "No dragon may be created inside the obstruction");
            helper.getLevel().setBlockAndUpdate(spawn.above(), Blocks.AIR.defaultBlockState());
            helper.assertTrue(useOnGround(helper, player, egg, spawn).consumesAction(),
                    "Clearing the obstruction must allow the same egg to work");
            var dragons = helper.getLevel().getEntities(CelestialDragonMod.DRAGON, d -> spawn.equals(d.getNest()));
            helper.assertTrue(dragons.size() == 1, "Retrying after clearing space must create exactly one dragon");
            dragons.forEach(d -> d.discard());
            helper.succeed();
        } finally {
            removePlayer(helper, player);
        }
    }

    @GameTest(structure = ARENA, skyAccess = true, maxTicks = 40)
    public void dispensersReturnCreativeEggsWithoutCreatingUnownedDragons(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos wild = helper.absolutePos(new BlockPos(12, 2, 12));
        BlockPos tamed = helper.absolutePos(new BlockPos(32, 2, 32));
        powerEggDispenser(helper, wild, DragonContent.WILD_DRAGON_SPAWN_EGG);
        powerEggDispenser(helper, tamed, DragonContent.TAMED_DRAGON_SPAWN_EGG);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(((DispenserBlockEntity)level.getBlockEntity(wild)).getItem(0).isEmpty()
                            && ((DispenserBlockEntity)level.getBlockEntity(tamed)).getItem(0).isEmpty(),
                    "Both powered dispensers must execute their actual item behavior");
            var drops = level.getEntitiesOfClass(ItemEntity.class, helper.getBounds());
            int wildEggs = drops.stream().filter(drop -> drop.getItem().is(DragonContent.WILD_DRAGON_SPAWN_EGG))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            int tamedEggs = drops.stream().filter(drop -> drop.getItem().is(DragonContent.TAMED_DRAGON_SPAWN_EGG))
                    .mapToInt(drop -> drop.getItem().getCount()).sum();
            helper.assertTrue(wildEggs == 1 && tamedEggs == 1,
                    "A dispenser must eject each test egg as an item rather than consuming it to create a dragon");
            helper.assertTrue(level.getEntitiesOfClass(CelestialDragonEntity.class, helper.getBounds()).isEmpty(),
                    "Automation cannot bypass Creative player ownership to create an unowned test dragon");
            helper.succeed();
        });
    }

    private static void powerEggDispenser(GameTestHelper helper, BlockPos pos, Item egg) {
        var level = helper.getLevel();
        for (int dx = -1; dx <= 1; dx++) for (int dz = 0; dz <= 2; dz++)
            level.setBlockAndUpdate(pos.offset(dx, -2, dz), Blocks.BEDROCK.defaultBlockState());
        level.setBlockAndUpdate(pos, Blocks.DISPENSER.defaultBlockState().setValue(DispenserBlock.FACING, Direction.SOUTH));
        ((DispenserBlockEntity)level.getBlockEntity(pos)).setItem(0, new ItemStack(egg));
        level.setBlockAndUpdate(pos.west(), Blocks.REDSTONE_BLOCK.defaultBlockState());
    }

    private static InteractionResult useOnGround(GameTestHelper helper, Player player, ItemStack stack, BlockPos spawn) {
        helper.getLevel().setBlockAndUpdate(spawn.below(), Blocks.BEDROCK.defaultBlockState());
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        var hit = new BlockHitResult(Vec3.atBottomCenterOf(spawn), Direction.UP, spawn.below(), false);
        return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
    }

    private static ServerPlayer player(GameTestHelper helper, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        player.setNoGravity(true);
        player.snapTo(helper.absoluteVec(new Vec3(55, 4, 55)));
        return player;
    }

    private static void removePlayer(GameTestHelper helper, ServerPlayer player) {
        if (player.isPassenger()) player.stopRiding();
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
