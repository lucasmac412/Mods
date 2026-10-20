package br.com.celestialdragon.content;

import br.com.celestialdragon.CelestialDragonMod;
import java.util.List;
import java.util.function.Function;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The mineral, terrain and survival crafting content of the celestial biome. */
public final class DragonContent {
    public static final Block CELESTIAL_STONE = block("celestial_stone", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(2.0F, 7.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
    public static final Block CELESTIAL_SOIL = block("celestial_soil", Block::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.7F).sound(SoundType.ROOTED_DIRT));
    public static final Block CELESTIAL_LOG = block("celestial_log", RotatedPillarBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(2.0F).sound(SoundType.CHERRY_WOOD));
    public static final UntintedParticleLeavesBlock CELESTIAL_LEAVES = block("celestial_leaves",
            p -> new UntintedParticleLeavesBlock(0.02F, ParticleTypes.CHERRY_LEAVES, p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(0.2F).sound(SoundType.CHERRY_LEAVES)
                    .randomTicks().noOcclusion().isSuffocating((s, l, p) -> false)
                    .isViewBlocking((s, l, p) -> false).isRedstoneConductor((s, l, p) -> false)
                    .pushReaction(PushReaction.DESTROY));
    public static final Block CELESTIAL_ORE = block("celestial_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(3.0F, 3.0F)
                    .sound(SoundType.STONE).requiresCorrectToolForDrops());
    public static final Block DEEPSLATE_CELESTIAL_ORE = block("deepslate_celestial_ore",
            p -> new DropExperienceBlock(UniformInt.of(3, 7), p),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(4.5F, 3.0F)
                    .sound(SoundType.DEEPSLATE).requiresCorrectToolForDrops());
    public static final CelestialNestBlock NEST_BLOCK = block("celestial_nest", CelestialNestBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BROWN).strength(1.5F).sound(SoundType.WOOD).noOcclusion());
    public static final CelestialEggBlock EGG = block("celestial_egg", CelestialEggBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(2.0F, 9.0F).sound(SoundType.AMETHYST)
                    .noOcclusion().dynamicShape().lightLevel(s -> 4).pushReaction(PushReaction.BLOCK));
    public static final CelestialIncubatorBlock INCUBATOR = block("celestial_incubator", CelestialIncubatorBlock::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F, 6.0F).sound(SoundType.METAL)
                    .lightLevel(s -> s.getValue(CelestialIncubatorBlock.OCCUPIED) ? 10 : 2));
    public static final Item CELESTIAL_GEM = Registry.register(BuiltInRegistries.ITEM, CelestialDragonMod.id("celestial_gem"),
            new Item(new Item.Properties().setId(ResourceKey.create(Registries.ITEM, CelestialDragonMod.id("celestial_gem")))
                    .rarity(Rarity.UNCOMMON)));
    public static final BlockEntityType<CelestialIncubatorBlockEntity> INCUBATOR_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, CelestialDragonMod.id("celestial_incubator"),
            FabricBlockEntityTypeBuilder.create(CelestialIncubatorBlockEntity::new, INCUBATOR).build());

    private DragonContent() {}

    private static <T extends Block> T block(String name, Function<BlockBehaviour.Properties, T> factory,
                                            BlockBehaviour.Properties properties) {
        var identifier = CelestialDragonMod.id(name);
        T block = Registry.register(BuiltInRegistries.BLOCK, identifier,
                factory.apply(properties.setId(ResourceKey.create(Registries.BLOCK, identifier))));
        Registry.register(BuiltInRegistries.ITEM, identifier,
                new BlockItem(block, new Item.Properties().setId(ResourceKey.create(Registries.ITEM, identifier))
                        .useBlockDescriptionPrefix()));
        return block;
    }

    public static void register() {
        // Initializing this class performs the registry writes exactly once.
    }

    public static List<ItemLike> creativeEntries() {
        return List.of(CELESTIAL_STONE, CELESTIAL_SOIL, CELESTIAL_LOG, CELESTIAL_LEAVES,
                CELESTIAL_ORE, DEEPSLATE_CELESTIAL_ORE, CELESTIAL_GEM, NEST_BLOCK, EGG, INCUBATOR);
    }
}
