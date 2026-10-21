package br.com.celestialdragon.client;

import br.com.celestialdragon.CelestialDragonMod;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/** A celestial long: flowing mane, hooked talons and a spine of large pointed scales. */
public final class DragonModel extends EntityModel<DragonRenderState> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
            CelestialDragonMod.id("celestial_dragon"), "main");
    public static final int SEGMENT_COUNT = 24;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart saddle;
    private final ModelPart energy;
    private final ModelPart beard;
    private final ModelPart tail;
    private final ModelPart[] segments = new ModelPart[SEGMENT_COUNT];
    private final ModelPart[] whiskers = new ModelPart[2];
    private final ModelPart[] mane = new ModelPart[34];
    private final ModelPart[] arms = new ModelPart[4];
    private final ModelPart[] hands = new ModelPart[4];

    public DragonModel(ModelPart root) {
        super(DragonGeometry.finish(root));
        body = root.getChild("body");
        neck = body.getChild("neck");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        saddle = body.getChild("saddle");
        energy = head.getChild("charge_core");
        beard = jaw.getChild("beard");
        ModelPart previous = body;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = previous.getChild("segment_" + i);
            previous = segments[i];
        }
        tail = previous.getChild("tail_tuft");
        whiskers[0] = head.getChild("whisker_left");
        whiskers[1] = head.getChild("whisker_right");
        for (int i = 0; i < mane.length; i++) mane[i] = head.getChild("mane_lock_" + i);
        for (int i = 0; i < arms.length; i++) {
            arms[i] = segments[i < 2 ? 2 : 12].getChild(i % 2 == 0 ? "talon_arm_left" : "talon_arm_right");
            hands[i] = arms[i].getChild("forearm").getChild("hand");
        }
    }

    private static CubeListBuilder obsidian() { return CubeListBuilder.create().texOffs(0, 0); }
    private static CubeListBuilder gold() { return CubeListBuilder.create().texOffs(128, 0); }
    private static CubeListBuilder cyan() { return CubeListBuilder.create().texOffs(256, 0); }
    private static CubeListBuilder ivory() { return CubeListBuilder.create().texOffs(384, 0); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        // Vanilla's model origin is 24 units above the feet: the saddle stays at 3.85 blocks.
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0, -24, 0));
        PartDefinition previous = body;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            // Broad shoulders taper into a narrow waist and a long, fine tail. Overlapping
            // octagonal sections keep the silhouette continuous while the spine bends.
            float width = i <= 4 ? 24 - i * 0.9F
                    : i <= 14 ? 20.4F - (i - 4) * 0.42F : 16.2F - (i - 14) * 1.35F;
            float height = width * 0.87F;
            PartDefinition segment = previous.addOrReplaceChild("segment_" + i,
                    obsidian()
                            .addBox(-width * 0.34F, -height / 2, -8, width * 0.68F, height, 21)
                            .addBox(-width / 2, -height * 0.30F, -8, width, height * 0.60F, 21),
                    PartPose.offset(0, 0, i == 0 ? 0 : 16));
            segment.addOrReplaceChild("belly", gold()
                    .addBox(-width * 0.29F, height / 2 - 1.6F, -8.4F, width * 0.58F, 2.1F, 19), PartPose.ZERO);
            for (int rib = 0; rib < 3; rib++) {
                segment.addOrReplaceChild("ventral_rib_" + rib, obsidian()
                        .addBox(-width * 0.29F, height / 2 + 0.3F, -7 + rib * 6.3F,
                                width * 0.58F, 0.35F, 0.8F), PartPose.ZERO);
            }
            float crest = i < 12 ? 18 - i * 0.35F : 13.8F - (i - 12) * 0.8F;
            segment.addOrReplaceChild("point_spine", gold()
                            .addBox(-2.6F, -crest, -7, 5.2F, crest, 14),
                    PartPose.offset(0, -height / 2 + 0.4F, 0));
            for (int side : new int[]{-1, 1}) {
                // Swept, dark flank volumes suggest muscle under the scales; gold is an
                // accent along the upper edge, rather than a thick band around every joint.
                segment.addOrReplaceChild("flank_" + side, obsidian()
                                .addBox(-width * 0.11F, -height * 0.25F, -7,
                                        width * 0.22F, height * 0.50F, 17),
                        PartPose.offsetAndRotation(side * width * 0.43F, 0, 0,
                                0.08F, side * -0.14F, side * -0.24F));
                float plateWidth = Math.min(9.5F, width * 0.48F);
                float plateLength = 12 + width * 0.28F;
                for (int row = 0; row < 2; row++) {
                    PartDefinition plate = segment.addOrReplaceChild("point_scale_" + side + "_" + row,
                            gold().addBox(-plateWidth / 2, -2.6F, -plateLength / 2,
                                    plateWidth, 3, plateLength),
                            PartPose.offsetAndRotation(side * width * (row == 0 ? 0.31F : 0.47F),
                                    -height * (row == 0 ? 0.42F : 0.10F), row == 0 ? -2 : 4,
                                    -0.18F, side * -0.16F, side * (row == 0 ? 0.58F : 1.18F)));
                    plate.addOrReplaceChild("point_scale_inlay", obsidian()
                            .addBox(-plateWidth * 0.34F, -3.2F, -plateLength / 2 + 0.9F,
                                    plateWidth * 0.68F, 1.2F, plateLength - 3.6F), PartPose.ZERO);
                }
            }
            if (i == 2 || i == 12) {
                addTalonArm(segment, 1, width, i == 12);
                addTalonArm(segment, -1, width, i == 12);
            }
            previous = segment;
        }
        PartDefinition tail = previous.addOrReplaceChild("tail_tuft", CubeListBuilder.create(), PartPose.offset(0, 0, 9));
        for (int j = -2; j <= 2; j++) {
            PartDefinition plume = tail.addOrReplaceChild("plume_" + j,
                    gold().addBox(-1.4F, -2, -1, 2.8F, 4, 17 - Math.abs(j) * 2),
                    PartPose.offsetAndRotation(j * 1.2F, -Math.abs(j) * 0.8F, 0,
                            -0.12F - Math.abs(j) * 0.05F, j * 0.12F, 0));
            plume.addOrReplaceChild("tip", gold().addBox(-0.7F, -1, -1, 1.4F, 2, 13),
                    PartPose.offsetAndRotation(0, 0, 15 - Math.abs(j) * 2, 0.18F, 0, 0));
        }

        // Pivot at the server mouth height: aiming up rotates the whole neck, not only the nose.
        PartDefinition neck = body.addOrReplaceChild("neck", obsidian()
                        .addBox(-10, -10.5F, -20, 20, 21, 22)
                        .addBox(-9, -12, -36, 18, 20, 20),
                PartPose.offset(0, 3.2F, 0));
        neck.addOrReplaceChild("neck_belly", gold()
                .addBox(-6, 7, -35, 12, 2, 21).addBox(-7, 9, -19, 14, 2, 20), PartPose.ZERO);
        for (int j = 0; j < 4; j++) {
            neck.addOrReplaceChild("point_spine_neck_" + j, gold().addBox(-1.7F, -10, -4, 3.4F, 10, 8),
                    PartPose.offset(0, -11, -4 - j * 8));
        }
        PartDefinition head = neck.addOrReplaceChild("head", obsidian()
                        .addBox(-12.5F, -18, -15, 25, 24, 26)
                        .addBox(-10, -21, -11, 20, 8, 17)
                        .addBox(-11, -4, -24, 22, 12, 17)
                        .addBox(-9, -3, -37, 18, 11, 18)
                        .addBox(-7.5F, -4, -43, 15, 12, 9),
                PartPose.offset(0, -10, -34));
        // The raised bridge, tapering nose, cheek blades and overhanging brow distinguish
        // the face from a flat rectangular reptile muzzle, without moving the beam origin.
        head.addOrReplaceChild("nasal_bridge", obsidian()
                .addBox(-5.5F, -11, -23, 11, 9, 15)
                .addBox(-4, -8, -36, 8, 7, 16), PartPose.ZERO);
        head.addOrReplaceChild("nose_ridge", gold().addBox(-1.3F, -8.5F, -35, 2.6F, 1.3F, 14), PartPose.ZERO);
        head.addOrReplaceChild("muzzle_trim", gold()
                .addBox(-8, 7.5F, -42.5F, 16, 2, 24)
                .addBox(-10.5F, 7.5F, -23, 21, 2, 10), PartPose.ZERO);
        head.addOrReplaceChild("nose", obsidian().addBox(-8.2F, -5.5F, -43.4F, 16.4F, 7, 6), PartPose.ZERO);
        head.addOrReplaceChild("nostrils", gold()
                .addBox(-6.8F, -5.8F, -43.7F, 3.8F, 2.2F, 3.6F)
                .addBox(3, -5.8F, -43.7F, 3.8F, 2.2F, 3.6F), PartPose.ZERO);
        head.addOrReplaceChild("nostril_centers", obsidian()
                .addBox(-6, -5.9F, -43.8F, 2.3F, 1.2F, 2.7F)
                .addBox(3.7F, -5.9F, -43.8F, 2.3F, 1.2F, 2.7F), PartPose.ZERO);
        head.addOrReplaceChild("eyes", cyan()
                .addBox(-13.1F, -10.5F, -18, 2, 4, 8)
                .addBox(11.1F, -10.5F, -18, 2, 4, 8), PartPose.ZERO);
        head.addOrReplaceChild("pupils", obsidian()
                .addBox(-13.25F, -10.4F, -15, 0.3F, 3.8F, 1.2F)
                .addBox(12.95F, -10.4F, -15, 0.3F, 3.8F, 1.2F), PartPose.ZERO);
        head.addOrReplaceChild("forehead_mask", gold().addBox(-2, -21.7F, -10, 4, 1.2F, 14), PartPose.ZERO);
        addMane(head);
        for (int side : new int[]{-1, 1}) {
            head.addOrReplaceChild("cheek_" + side, obsidian().addBox(-3.5F, -6, -6, 7, 14, 15),
                    PartPose.offsetAndRotation(side * 10.5F, -3, -5, -0.12F, side * 0.3F, side * -0.18F));
            head.addOrReplaceChild("cheek_blade_" + side, gold().addBox(-0.8F, -1.8F, -4, 1.6F, 3.6F, 15),
                    PartPose.offsetAndRotation(side * 14, 0, -9, -0.1F, side * 0.28F, side * -0.2F));
            head.addOrReplaceChild("brow_" + side, gold().addBox(-2, -1.8F, -8, 4, 3.6F, 15),
                    PartPose.offsetAndRotation(side * 12, -11.5F, -14,
                            -0.16F, side * -0.15F, side * -0.32F));
            PartDefinition horn = head.addOrReplaceChild("antler_" + side, gold()
                            .addBox(-2.6F, -20, -2.6F, 5.2F, 21, 5.2F),
                    PartPose.offsetAndRotation(side * 9.5F, -20, 1, -0.4F, side * 0.2F, side * 0.34F));
            PartDefinition tip = horn.addOrReplaceChild("tip", gold().addBox(-2, -18, -2, 4, 19, 4),
                    PartPose.offsetAndRotation(0, -19, 0, -0.45F, 0, side * 0.26F));
            tip.addOrReplaceChild("crown", gold().addBox(-1.1F, -12, -1.1F, 2.2F, 13, 2.2F),
                    PartPose.offsetAndRotation(0, -17, 0, 0.46F, 0, side * -0.35F));
            horn.addOrReplaceChild("prong", gold().addBox(-1.5F, -16, -1.5F, 3, 17, 3),
                    PartPose.offsetAndRotation(0, -9, -1, 0.8F, 0, side * 0.42F));
            tip.addOrReplaceChild("prong", gold().addBox(-1.1F, -13, -1.1F, 2.2F, 14, 2.2F),
                    PartPose.offsetAndRotation(0, -8, 0, -0.6F, 0, side * 0.55F));
            PartDefinition whisker = head.addOrReplaceChild(side == 1 ? "whisker_left" : "whisker_right",
                    gold().addBox(side > 0 ? 0 : -12, -0.8F, -0.8F, 12, 1.6F, 1.6F),
                    PartPose.offsetAndRotation(side * 7.5F, 6, -36, 0, side * 0.35F, side * -0.2F));
            for (int j = 0; j < 4; j++) {
                whisker = whisker.addOrReplaceChild("curl_" + j,
                        gold().addBox(side > 0 ? 0 : -10, -0.6F, -0.6F, 10, 1.2F, 1.2F),
                        PartPose.offsetAndRotation(side * (j == 0 ? 12 : 10), 0, 0,
                                0, side * (j < 2 ? -0.35F : 0.28F), side * -0.25F));
            }
        }
        head.addOrReplaceChild("forehead_crest", gold().addBox(-1.3F, -7, -4, 2.6F, 8, 10),
                PartPose.offsetAndRotation(0, -21, -3, -0.4F, 0, 0));
        PartDefinition jaw = head.addOrReplaceChild("jaw", obsidian()
                .addBox(-9.5F, 0, -17, 19, 7, 20)
                .addBox(-7.7F, 0, -36, 15.4F, 6, 23), PartPose.offset(0, 10, -6));
        jaw.addOrReplaceChild("jaw_gold", gold().addBox(-8, 4.8F, -36.3F, 16, 2.2F, 24)
                .addBox(-10, 5.8F, -16, 20, 2.2F, 18), PartPose.ZERO);
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(320, 0)
                .addBox(-4.5F, -0.7F, -32, 9, 1.5F, 17), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            for (int j = 0; j < 5; j++) {
                head.addOrReplaceChild("fang_" + side + "_" + j,
                        ivory().addBox(-1.1F, 0, -1.2F, 2.2F, j == 0 ? 9 : 4.5F, 2.4F),
                        PartPose.offset(side * (j < 3 ? 7 : 8.8F), 9.5F, -38 + j * 5));
                jaw.addOrReplaceChild("tooth_" + side + "_" + j,
                        ivory().addBox(-1.1F, -5.5F, -1.2F, 2.2F, 5.5F, 2.4F),
                        PartPose.offset(side * (j < 3 ? 6.6F : 8.3F), 0, -32 + j * 5));
            }
        }
        PartDefinition beard = jaw.addOrReplaceChild("beard", CubeListBuilder.create(),
                PartPose.offsetAndRotation(0, 7, -22, -0.42F, 0, 0));
        for (int side = -1; side <= 1; side++) {
            beard.addOrReplaceChild("strand_" + side, gold()
                            .addBox(-1.6F, 0, -1.4F, 3.2F, side == 0 ? 24 : 18, 2.8F),
                    PartPose.offsetAndRotation(side * 3.2F, 0, 0, side == 0 ? -0.2F : -0.05F, 0, side * -0.18F));
        }
        PartDefinition energy = head.addOrReplaceChild("charge_core", cyan().addBox(-6, -6, -6, 12, 12, 12),
                PartPose.offset(0, 10, -42.8F));
        energy.addOrReplaceChild("ring", gold()
                .addBox(-6, -6, -0.8F, 12, 1, 1.6F).addBox(-6, 5, -0.8F, 12, 1, 1.6F)
                .addBox(-6, -5, -0.8F, 1, 10, 1.6F).addBox(5, -5, -0.8F, 1, 10, 1.6F),
                PartPose.rotation(0, 0, Mth.PI / 4));

        PartDefinition saddle = body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 128)
                .addBox(-11, -14, -9, 22, 4, 25).addBox(-10, -23, 12, 20, 10, 4), PartPose.ZERO);
        saddle.addOrReplaceChild("trim", gold()
                .addBox(-12, -15, -10, 1.5F, 5, 28).addBox(10.5F, -15, -10, 1.5F, 5, 28)
                .addBox(-12, -15, -10, 24, 1.5F, 2).addBox(-11, -24, 12, 22, 1.5F, 5), PartPose.ZERO);
        return LayerDefinition.create(mesh, 512, 256);
    }

    private static void addTalonArm(PartDefinition segment, int side, float width, boolean rear) {
        // Small floating arms with long grasping fingers, rather than feet for walking.
        PartDefinition arm = segment.addOrReplaceChild(side > 0 ? "talon_arm_left" : "talon_arm_right",
                obsidian().addBox(-3, -2, -3.5F, 6, 12, 7),
                PartPose.offsetAndRotation(side * (width / 2 - 1), 1, rear ? 3 : 0,
                        rear ? 0.25F : -0.25F, side * 0.12F, side * -0.90F));
        arm.addOrReplaceChild("point_scale_arm", gold().addBox(-2.3F, -3, -4, 4.6F, 2, 12),
                PartPose.offsetAndRotation(0, 3, 0, -Mth.HALF_PI, 0, 0));
        PartDefinition forearm = arm.addOrReplaceChild("forearm", obsidian()
                        .addBox(-2.3F, -1, -2.5F, 4.6F, 10, 5),
                PartPose.offsetAndRotation(0, 9, 0, 0.95F, 0, side * 0.32F));
        forearm.addOrReplaceChild("bracelet", gold().addBox(-2.6F, 5.5F, -2.8F, 5.2F, 1.5F, 5.6F), PartPose.ZERO);
        PartDefinition hand = forearm.addOrReplaceChild("hand", obsidian()
                        .addBox(-4.2F, -1, -4, 8.4F, 3.8F, 7),
                PartPose.offsetAndRotation(0, 8, -0.5F, -0.45F, 0, side * -0.15F));
        for (int finger = -1; finger <= 1; finger++) {
            PartDefinition digit = hand.addOrReplaceChild("finger_" + finger,
                    obsidian().addBox(-1.2F, -1, -7, 2.4F, 2.4F, 8),
                    PartPose.offsetAndRotation(finger * 3.1F, 0.8F, -2.4F,
                            -0.08F, -finger * 0.30F, 0));
            PartDefinition hook = digit.addOrReplaceChild("hook", ivory()
                            .addBox(-1.05F, -1, -5, 2.1F, 2.2F, 6),
                    PartPose.offsetAndRotation(0, 0, -6.5F, 0.50F, 0, 0));
            hook.addOrReplaceChild("point_talon", ivory().addBox(-0.85F, -0.9F, -6, 1.7F, 2.4F, 7),
                    PartPose.offsetAndRotation(0, 0, -4.4F, 0.65F, 0, 0));
        }
        PartDefinition thumb = hand.addOrReplaceChild("thumb", obsidian()
                        .addBox(-1.3F, -1.2F, -6, 2.6F, 2.6F, 7),
                PartPose.offsetAndRotation(side * 3.6F, 0.5F, 0.5F, -0.1F, side * -1.15F, 0));
        thumb.addOrReplaceChild("point_talon_thumb", ivory()
                        .addBox(-1, -1, -7, 2, 2.6F, 8),
                PartPose.offsetAndRotation(0, 0, -5, 0.80F, 0, 0));
    }

    private static void addMane(PartDefinition head) {
        // A dense two-layer collar of thick, tapered locks, not a few thin spikes. The dark
        // undercoat fills the roots; golden locks flare radially and sweep back over the neck.
        head.addOrReplaceChild("mane_undercoat", obsidian()
                .addBox(-16, -24, 4, 32, 35, 16)
                .addBox(-20, -17, 5, 40, 22, 14), PartPose.ZERO);
        int index = 0;
        for (int layer = 0; layer < 2; layer++) {
            int count = layer == 0 ? 18 : 16;
            for (int i = 0; i < count; i++) {
                float angle = (i + layer * 0.5F) * Mth.TWO_PI / count;
                float radialX = Mth.cos(angle);
                float radialY = Mth.sin(angle);
                float radiusX = layer == 0 ? 16.8F : 20.5F;
                float radiusY = layer == 0 ? 18.8F : 22.5F;
                float length = 13 + (i % 3) * 1.4F;
                float width = layer == 0 ? 8 : 8.6F;
                float flare = layer == 0 ? 0.52F : 0.42F;
                PartDefinition lock = head.addOrReplaceChild("mane_lock_" + index++,
                        gold().addBox(-width / 2, -3.8F, -2, width, 7.6F, length),
                        PartPose.offsetAndRotation(radialX * radiusX, -6 + radialY * radiusY,
                                layer == 0 ? 4 : 14, -radialY * flare, radialX * flare, angle * 0.12F));
                PartDefinition middle = lock.addOrReplaceChild("flow", gold()
                                .addBox(-width * 0.35F, -2.8F, -1, width * 0.7F, 5.6F, 10),
                        PartPose.offsetAndRotation(0, 0, length - 2,
                                radialY * 0.20F, -radialX * 0.18F, 0));
                middle.addOrReplaceChild("tip", gold().addBox(-1.6F, -1.6F, -1, 3.2F, 3.2F, 9),
                        PartPose.offsetAndRotation(0, 0, 8, -radialY * 0.12F, radialX * 0.10F, 0));
            }
        }
    }

    @Override
    public void setupAnim(DragonRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        float movement = Mth.clamp(state.movementAmount, 0, 1);
        var attack = DragonAnimation.pose(state.biteTicks, state.beamTicks, time);
        saddle.visible = state.tamed;
        if (!state.ridden) body.y += Mth.sin(time * 0.07F) * 0.5F;
        float amplitude = (0.55F + movement * 0.50F) * (1 - attack.waveDamping());
        float previousYaw = 0;
        float previousPitch = 0;
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            float envelope = DragonAnimation.smooth(Math.min(1, i / 5.0F));
            float phase = time * 0.12F - i * 0.52F;
            float yaw = Mth.sin(phase) * amplitude * envelope;
            float pitch = Mth.sin(phase * 0.82F + 1.1F) * 0.035F * envelope;
            // Differences of global tangents form an S instead of accumulating rotations into a coil.
            segments[i].yRot = yaw - previousYaw;
            segments[i].xRot = pitch - previousPitch;
            segments[i].zRot = Mth.sin(phase) * 0.014F * envelope;
            // Reserve clear space around the rider's seat; the wild version has the full crest.
            segments[i].getChild("point_spine").visible = !state.tamed || i > 1;
            previousYaw = yaw;
            previousPitch = pitch;
        }
        // Keep the muzzle aligned with the server's look vector even for a vertical target.
        neck.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD, -Mth.HALF_PI, Mth.HALF_PI) + attack.neckPitch();
        neck.z += attack.pullBack();
        head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD * 0.5F, -0.45F, 0.45F)
                * (state.beamTicks > 0 ? 0.08F : 1);
        head.xRot = attack.headPitch() + Mth.sin(time * 0.065F) * 0.012F * (1 - attack.brace());
        jaw.xRot = attack.jawOpening();
        for (int i = 0; i < arms.length; i++) {
            float side = i % 2 == 0 ? 1 : -1;
            arms[i].xRot += Mth.sin(time * 0.075F - i * 0.9F) * 0.12F - attack.brace() * 0.20F;
            arms[i].zRot += side * (Mth.sin(time * 0.065F - i * 0.7F) * 0.07F - attack.brace() * 0.13F);
            hands[i].xRot += Mth.sin(time * 0.09F - i) * 0.08F - attack.brace() * 0.12F;
        }
        for (int i = 0; i < mane.length; i++) {
            // Very small, phase-offset movement reads as flowing hair rather than rigid antlers.
            mane[i].xRot += Mth.sin(time * 0.075F - i * 0.65F) * 0.035F;
            mane[i].yRot += Mth.sin(time * 0.065F - i * 0.48F) * 0.025F;
        }
        whiskers[0].zRot += Mth.sin(time * 0.09F) * 0.13F;
        whiskers[1].zRot -= Mth.sin(time * 0.09F + 0.6F) * 0.13F;
        whiskers[0].yRot += Mth.sin(time * 0.07F) * 0.08F;
        whiskers[1].yRot -= Mth.sin(time * 0.07F + 0.8F) * 0.08F;
        beard.xRot += Mth.sin(time * 0.1F) * 0.1F - attack.brace() * 0.1F;
        tail.yRot += Mth.sin(time * 0.12F - 12) * 0.11F;
        energy.visible = attack.energyScale() > 0.001F;
        energy.xScale = energy.yScale = energy.zScale = attack.energyScale();
        energy.xRot = time * 0.07F;
        energy.yRot = time * 0.1F;
    }
}
