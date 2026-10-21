package br.com.mods.createport.render;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.instance.InstanceTypes;
import dev.engine_room.flywheel.lib.instance.TransformedInstance;
import dev.engine_room.flywheel.lib.model.Models;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;

public final class FlywheelProbeClient implements ClientModInitializer {
    public static final AtomicInteger CREATED = new AtomicInteger();
    public static final AtomicInteger FRAMES = new AtomicInteger();
    public static final AtomicInteger DELETED = new AtomicInteger();
    @Override public void onInitializeClient() {
        SimpleBlockEntityVisualizer.builder(BlockEntityTypes.CHEST)
                .factory(ProbeVisual::new).neverSkipVanillaRender().apply();
    }
    private static final class ProbeVisual extends AbstractBlockEntityVisual<ChestBlockEntity> implements SimpleDynamicVisual {
        private final TransformedInstance cube;
        private final TransformedInstance glass;
        ProbeVisual(VisualizationContext ctx, ChestBlockEntity blockEntity, float partialTick) {
            super(ctx, blockEntity, partialTick);
            cube = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.GOLD_BLOCK.defaultBlockState())).createInstance();
            glass = instancerProvider().instancer(InstanceTypes.TRANSFORMED, Models.block(Blocks.GLASS.defaultBlockState())).createInstance();
            updateLight(partialTick);
            move(partialTick);
            CREATED.incrementAndGet();
        }
        private void move(float partialTick) {
            glass.setIdentityTransform().translate(visualPos.getX() + 2, visualPos.getY() + 2, visualPos.getZ())
                    .rotateCentered(-((level.getGameTime() + partialTick) % 160) * (float)Math.PI / 80, 0, 1, 0).setChanged();
            cube.setIdentityTransform().translate(visualPos.getX(), visualPos.getY() + 2, visualPos.getZ())
                    .rotateCentered(((level.getGameTime() + partialTick) % 160) * (float)Math.PI / 80, 0, 1, 0).setChanged();
        }
        @Override public void beginFrame(Context context) { move(context.partialTick()); FRAMES.incrementAndGet(); }
        @Override public void updateLight(float partialTick) { relight(cube, glass); }
        @Override public void collectCrumblingInstances(Consumer<Instance> consumer) { consumer.accept(cube); consumer.accept(glass); }
        @Override protected void _delete() { cube.delete(); glass.delete(); DELETED.incrementAndGet(); }
    }
}
