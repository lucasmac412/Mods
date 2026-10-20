package br.com.celestialdragon.world;

import br.com.celestialdragon.CelestialDragonMod;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

public final class DragonWorld {
    public static final ResourceKey<Biome> CELESTIAL_GROVE = ResourceKey.create(Registries.BIOME, CelestialDragonMod.id("celestial_grove"));
    public static final ResourceKey<Structure> DRAGON_NEST = ResourceKey.create(Registries.STRUCTURE, CelestialDragonMod.id("dragon_nest"));
    public static final ResourceKey<PlacedFeature> CELESTIAL_ORE = ResourceKey.create(Registries.PLACED_FEATURE, CelestialDragonMod.id("ore_celestial"));
    public static final Feature<NoneFeatureConfiguration> GROVE_TERRAIN = new GroveTerrainFeature();
    public static final Feature<NoneFeatureConfiguration> CELESTIAL_TREES = new CelestialTreeFeature();
    public static final Feature<NoneFeatureConfiguration> CELESTIAL_ROCKS = new CelestialRockFeature();
    public static final StructureType<CelestialNestStructure> NEST_TYPE = () -> CelestialNestStructure.CODEC;
    public static final StructurePieceType NEST_PIECE = NestPiece::new;
    public static final StructurePlacementType<CelestialNestPlacement> NEST_PLACEMENT = () -> CelestialNestPlacement.CODEC;
    private DragonWorld() { }

    public static void register() {
        Registry.register(BuiltInRegistries.FEATURE, CelestialDragonMod.id("grove_terrain"), GROVE_TERRAIN);
        Registry.register(BuiltInRegistries.FEATURE, CelestialDragonMod.id("celestial_tree"), CELESTIAL_TREES);
        Registry.register(BuiltInRegistries.FEATURE, CelestialDragonMod.id("celestial_rocks"), CELESTIAL_ROCKS);
        Registry.register(BuiltInRegistries.STRUCTURE_TYPE, CelestialDragonMod.id("dragon_nest"), NEST_TYPE);
        Registry.register(BuiltInRegistries.STRUCTURE_PIECE, CelestialDragonMod.id("dragon_nest"), NEST_PIECE);
        Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, CelestialDragonMod.id("grove_center"), NEST_PLACEMENT);
        // Identical vein size, attempts, triangular height distribution and exposure rules to emerald.
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld().or(BiomeSelectors.tag(BiomeTags.IS_OVERWORLD)),
                GenerationStep.Decoration.UNDERGROUND_ORES, CELESTIAL_ORE);
    }
}
