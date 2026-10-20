package br.com.celestialdragon.test;

import br.com.celestialdragon.CelestialDragonMod;
import br.com.celestialdragon.content.CelestialEggBlock;
import br.com.celestialdragon.content.DragonContent;
import br.com.celestialdragon.world.DragonWorld;
import br.com.celestialdragon.world.GroveLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.fabricmc.fabric.api.client.gametest.v1.world.TestWorldSave;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/** Exercises the actual Overworld generator, including cross-chunk structures and saved inhabitants. */
public final class DragonNaturalWorldGameTest implements FabricClientGameTest {
    private static final Path REPORTS = Path.of("../../reports/client-natural").toAbsolutePath().normalize();

    @Override
    public void runTest(ClientGameTestContext context) {
        context.getInput().resizeWindow(1280, 720);
        context.runOnClient(client -> {
            client.options.renderDistance().set(5);
            client.options.simulationDistance().set(5);
            client.options.fov().set(75);
            client.options.pauseOnLostFocus = false;
        });
        Observed observed;
        UUID guardianId;
        TestWorldSave save;
        try (var world = context.worldBuilder().adjustSettings(settings -> {
            // The Fabric client test default is flat; explicitly exercise normal noise terrain.
            settings.setWorldType(settings.getNormalPresetList().stream()
                    .filter(entry -> entry.preset() != null && entry.preset().is(WorldPresets.NORMAL))
                    .findFirst().orElseThrow(() -> new AssertionError("Normal world preset missing")));
            settings.setSeed("1");
            settings.setGenerateStructures(true);
            settings.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
            settings.setAllowCommands(true);
        }).create()) {
            save = world.getWorldSave();
            world.getServer().runCommand("time set noon");
            world.getServer().runCommand("weather clear");
            observed = world.getServer().computeOnServer(ignored -> generateAndInspect(world));
            world.getConnection().waitForChunksDownload();
            guardianId = checkGuardian(context, world, observed.egg(), null);
            world.getConnection().waitForClientboundEntityUpdates(CelestialDragonMod.DRAGON);
            world.getConnection().waitForChunksRender();
            context.getInput().lookAt(observed.egg().above(1));
            context.waitTicks(10);
            context.takeScreenshot(TestScreenshotOptions.of("06-natural-celestial-grove-and-nest")
                    .disableCounterPrefix().withSize(1280, 720).withDestinationDir(REPORTS));
        }

        // Reopen the saved generated world, rather than rerunning the procedural command helper.
        try (var world = save.open()) {
            world.getServer().runOnServer(ignored -> {
                ServerLevel level = world.getConnection().getServerLevel();
                generateNestChunks(level, observed.center());
                checkEgg(level, observed.egg());
                checkFoundation(level, observed.egg().below(2));
            });
            world.getConnection().waitForChunksDownload();
            checkGuardian(context, world, observed.egg(), guardianId);
        }
        writeReport(observed, guardianId);
    }

    private static Observed generateAndInspect(TestSingleplayerContext world) {
        ServerLevel level = world.getConnection().getServerLevel();
        if (!(level.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator))
            throw new AssertionError("Natural generation test must use the normal noise generator");
        BlockPos center = GroveLayout.nearestCenter(0, 0);
        generateNestChunks(level, center);
        if (!level.getBiome(center.atY(90)).is(DragonWorld.CELESTIAL_GROVE))
            throw new AssertionError("Celestial grove missing at its real layout center: " + center);

        BlockPos egg = null;
        for (int y = level.getMinY(); y < level.getMaxY() - 2; y++) {
            BlockPos pos = center.atY(y);
            BlockState state = level.getBlockState(pos);
            if (state.is(DragonContent.EGG) && state.getValue(CelestialEggBlock.PART) == 0) {
                if (egg != null) throw new AssertionError("Multiple natural eggs in one nest");
                egg = pos;
            }
        }
        if (egg == null) throw new AssertionError("Natural nest/egg missing at " + center);
        checkEgg(level, egg);
        int foundation = checkFoundation(level, egg.below(2));

        var structure = level.registryAccess().lookupOrThrow(Registries.STRUCTURE).getOrThrow(DragonWorld.DRAGON_NEST);
        var located = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, HolderSet.direct(structure), egg, 1, false);
        if (located == null || !located.getSecond().is(DragonWorld.DRAGON_NEST)
                || Math.abs(located.getFirst().getX() - center.getX()) > 16
                || Math.abs(located.getFirst().getZ() - center.getZ()) > 16)
            throw new AssertionError("The generated nest must be discoverable through /locate structure");

        Counts counts = new Counts();
        // Sixteen peripheral chunks avoid counting the nest's logs as generated trees.
        for (int dx = -4; dx <= 4; dx += 2) for (int dz = -4; dz <= 4; dz += 2) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) == 4)
                inspectChunk(level, level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz), counts);
        }
        if (counts.soil < 256 || counts.stone < 256 || counts.logs < 7 || counts.leaves < 30 || counts.outcrops == 0)
            throw new AssertionError("Natural celestial terrain/flora/outcrops missing: " + counts);
        if (counts.detachedRocks != 0)
            throw new AssertionError("Naturally generated celestial rocks must touch the ground: " + counts.detachedRocks);
        if (counts.ore == 0) throw new AssertionError("No naturally generated celestial ore in sixteen sampled chunks");

        var player = world.getConnection().getServerPlayer();
        player.setGameMode(GameType.CREATIVE);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.teleportTo(level, center.getX() + 44.5, egg.getY() + 35, center.getZ() + 48.5,
                Set.of(), 135, 25, false);
        return new Observed(center, egg, foundation, counts);
    }

    private static void generateNestChunks(ServerLevel level, BlockPos center) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++)
            level.getChunk((center.getX() >> 4) + dx, (center.getZ() >> 4) + dz);
    }

    private static void checkEgg(ServerLevel level, BlockPos egg) {
        for (int part = 0; part < 3; part++) {
            BlockState state = level.getBlockState(egg.above(part));
            if (!state.is(DragonContent.EGG) || state.getValue(CelestialEggBlock.PART) != part)
                throw new AssertionError("Natural egg section " + part + " missing at " + egg);
        }
    }

    private static int checkFoundation(ServerLevel level, BlockPos base) {
        int stone = 0;
        for (int dx = -20; dx <= 20; dx++) for (int dz = -20; dz <= 20; dz++)
            if (level.getBlockState(base.offset(dx, 0, dz)).is(DragonContent.CELESTIAL_STONE)) stone++;
        if (stone < 1_000
                || !level.getBlockState(base.offset(-20, 0, 0)).is(DragonContent.CELESTIAL_STONE)
                || !level.getBlockState(base.offset(20, 0, 0)).is(DragonContent.CELESTIAL_STONE))
            throw new AssertionError("Natural 41-block nest foundation was clipped: " + stone + " blocks at " + base);
        return stone;
    }

    private static void inspectChunk(ServerLevel level, LevelChunk chunk, Counts counts) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        Set<BlockPos> rocks = new HashSet<>();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                int highestSoil = level.getMinY() - 1;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
                for (int y = top; y >= level.getMinY(); y--) {
                    BlockState state = chunk.getBlockState(pos.set(x, y, z));
                    if (state.is(DragonContent.CELESTIAL_SOIL)) { counts.soil++; highestSoil = Math.max(highestSoil, y); }
                    if (state.is(DragonContent.CELESTIAL_STONE)) counts.stone++;
                    if (state.is(DragonContent.CELESTIAL_LOG)) counts.logs++;
                    if (state.is(DragonContent.CELESTIAL_LEAVES)) counts.leaves++;
                    if (state.is(DragonContent.CELESTIAL_ORE) || state.is(DragonContent.DEEPSLATE_CELESTIAL_ORE)) counts.ore++;
                }
                // Above-ground stone necessarily belongs to the separate rock feature in these chunks.
                if (highestSoil >= level.getMinY()) {
                    for (int y = highestSoil + 1; y <= top; y++)
                        if (chunk.getBlockState(pos.set(x, y, z)).is(DragonContent.CELESTIAL_STONE)) {
                            counts.outcrops++;
                            rocks.add(pos.immutable());
                        }
                }
            }
        }
        // Check entire boulders, allowing their rounded edges to overhang by a block.
        Set<BlockPos> remaining = new HashSet<>(rocks);
        while (!remaining.isEmpty()) {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            BlockPos first = remaining.iterator().next();
            remaining.remove(first);
            queue.add(first);
            boolean grounded = false;
            while (!queue.isEmpty()) {
                BlockPos current = queue.removeFirst();
                BlockPos below = current.below();
                if (!rocks.contains(below) && hasContinuousGroundSupport(chunk, below)) grounded = true;
                for (Direction direction : Direction.values()) {
                    BlockPos neighbor = current.relative(direction);
                    if (remaining.remove(neighbor)) queue.addLast(neighbor);
                }
            }
            if (!grounded) counts.detachedRocks++;
        }
    }

    private static boolean hasContinuousGroundSupport(LevelChunk chunk, BlockPos below) {
        BlockPos.MutableBlockPos probe = below.mutable();
        for (int y = below.getY(); y >= chunk.getMinY(); y--) {
            BlockState state = chunk.getBlockState(probe.setY(y));
            if (!state.isSolid() || state.is(BlockTags.LEAVES) || !state.getFluidState().isEmpty()) return false;
            if (state.is(DragonContent.CELESTIAL_SOIL)) return true;
        }
        return true;
    }

    private static UUID checkGuardian(ClientGameTestContext context, TestSingleplayerContext world,
                                      BlockPos egg, UUID expectedId) {
        var server = world.getServer();
        server.waitFor(ignored -> !world.getConnection().getServerLevel().getEntities(
                CelestialDragonMod.DRAGON, dragon -> egg.equals(dragon.getNest())).isEmpty());
        context.waitTicks(20);
        return server.computeOnServer(ignored -> {
            var guardians = world.getConnection().getServerLevel().getEntities(
                    CelestialDragonMod.DRAGON, dragon -> egg.equals(dragon.getNest()));
            if (guardians.size() != 1) throw new AssertionError("Expected exactly one natural guardian, got " + guardians.size());
            var guardian = guardians.getFirst();
            if (guardian.isTamed() || guardian.getOwnerId() != null)
                throw new AssertionError("A naturally spawned nest guardian must be WILD");
            if (expectedId != null && !expectedId.equals(guardian.getUUID()))
                throw new AssertionError("Reopening the world replaced its original guardian");
            return guardian.getUUID();
        });
    }

    private static void writeReport(Observed observed, UUID guardianId) {
        Counts counts = observed.counts();
        String report = """
                {
                  "seed": 1,
                  "generator": "normal_noise",
                  "center": [%d, %d, %d],
                  "eggHeightBlocks": 3,
                  "nestWidthBlocks": 41,
                  "nestFoundationBlocks": %d,
                  "sampledPeripheralChunks": 16,
                  "soilBlocks": %d,
                  "stoneBlocks": %d,
                  "treeLogBlocks": %d,
                  "leafBlocks": %d,
                  "aboveGroundRockBlocks": %d,
                  "detachedRockComponents": %d,
                  "oreBlocks": %d,
                  "guardianCountBeforeAndAfterReload": 1,
                  "guardianIsWild": true,
                  "guardianUuid": "%s",
                  "locateStructurePassed": true,
                  "saveAndReloadPassed": true
                }
                """.formatted(observed.egg().getX(), observed.egg().getY(), observed.egg().getZ(), observed.foundation(),
                counts.soil, counts.stone, counts.logs, counts.leaves, counts.outcrops, counts.detachedRocks, counts.ore, guardianId);
        try {
            Files.createDirectories(REPORTS);
            Files.writeString(REPORTS.resolve("natural-generation.json"), report);
        } catch (IOException exception) {
            throw new AssertionError("Could not save natural-generation evidence", exception);
        }
    }

    private record Observed(BlockPos center, BlockPos egg, int foundation, Counts counts) { }

    private static final class Counts {
        int soil, stone, logs, leaves, outcrops, detachedRocks, ore;
        @Override public String toString() {
            return "soil=" + soil + ", stone=" + stone + ", logs=" + logs + ", leaves=" + leaves
                    + ", outcrops=" + outcrops + ", ore=" + ore;
        }
    }
}
