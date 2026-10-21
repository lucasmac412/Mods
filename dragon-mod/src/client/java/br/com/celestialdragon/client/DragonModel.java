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

/** An imperial Chinese long: a flowing serpent, crowned head and small grasping limbs. */
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
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] whiskers = new ModelPart[2];

    public DragonModel(ModelPart root) {
        super(root);
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
        legs[0] = segments[2].getChild("left_foreleg");
        legs[1] = segments[2].getChild("right_foreleg");
        legs[2] = segments[12].getChild("left_hindleg");
        legs[3] = segments[12].getChild("right_hindleg");
        whiskers[0] = head.getChild("whisker_left");
        whiskers[1] = head.getChild("whisker_right");
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
            float width = i <= 12 ? 32 - i * 0.55F : 25.4F - (i - 12) * 2.05F;
            float height = width * 0.84F;
            PartDefinition segment = previous.addOrReplaceChild("segment_" + i,
                    obsidian().addBox(-width / 2, -height / 2, -8, width, height, 21),
                    PartPose.offset(0, 0, i == 0 ? 0 : 16));
            segment.addOrReplaceChild("belly", gold()
                    .addBox(-width * 0.43F, height / 2 - 2, -8.4F, width * 0.86F, 3, 19), PartPose.ZERO);
            for (int rib = 0; rib < 3; rib++) {
                segment.addOrReplaceChild("ventral_rib_" + rib, obsidian()
                        .addBox(-width * 0.4F, height / 2 + 0.6F, -7 + rib * 6.3F,
                                width * 0.8F, 0.6F, 0.8F), PartPose.ZERO);
            }
            float crest = i < 12 ? 12 - i * 0.2F : 9.5F - (i - 12) * 0.48F;
            segment.addOrReplaceChild("crest", gold().addBox(-1.7F, -crest, -3, 3.4F, crest + 1, 9),
                    PartPose.offsetAndRotation(0, -height / 2, 0, -0.38F, 0, 0));
            for (int side : new int[]{-1, 1}) {
                segment.addOrReplaceChild("scale_" + side, gold().addBox(-1, -3.6F, -5, 2, 7.2F, 13),
                        PartPose.offsetAndRotation(side * (width / 2 - 0.1F), -height * 0.22F, 0,
                                -0.12F, side * -0.12F, side * -0.24F));
            }
            if (i == 2 || i == 12) {
                addLeg(segment, i == 2 ? "left_foreleg" : "left_hindleg", 1, width);
                addLeg(segment, i == 2 ? "right_foreleg" : "right_hindleg", -1, width);
            }
            previous = segment;
        }
        PartDefinition tail = previous.addOrReplaceChild("tail_tuft", CubeListBuilder.create(), PartPose.offset(0, 0, 9));
        for (int j = -3; j <= 3; j++) {
            tail.addOrReplaceChild("plume_" + j, gold().addBox(-1.4F, -2.5F, -1, 2.8F, 5, 32 - Math.abs(j) * 4),
                    PartPose.offsetAndRotation(j * 1.5F, -Math.abs(j) * 1.3F, 0,
                            -0.2F - Math.abs(j) * 0.06F, j * 0.17F, 0));
        }

        // Pivot at the server mouth height: aiming up rotates the whole neck, not only the nose.
        PartDefinition neck = body.addOrReplaceChild("neck", obsidian()
                        .addBox(-15, -14, -20, 30, 27, 22)
                        .addBox(-14, -15, -36, 28, 26, 20),
                PartPose.offset(0, 3.2F, 0));
        neck.addOrReplaceChild("neck_belly", gold()
                .addBox(-12, 10, -35, 24, 3, 21).addBox(-13, 12, -19, 26, 3, 20), PartPose.ZERO);
        for (int j = 0; j < 4; j++) {
            neck.addOrReplaceChild("neck_crest_" + j, gold().addBox(-1.8F, -12, -2, 3.6F, 13, 7),
                    PartPose.offsetAndRotation(0, -14, -4 - j * 8, -0.45F, 0, 0));
        }
        PartDefinition head = neck.addOrReplaceChild("head", obsidian()
                        .addBox(-17, -18, -19, 34, 28, 29)
                        .addBox(-14, -1, -42, 28, 11, 27),
                PartPose.offset(0, -10, -34));
        head.addOrReplaceChild("muzzle_trim", gold().addBox(-14.5F, 7.5F, -42.5F, 29, 3, 28), PartPose.ZERO);
        head.addOrReplaceChild("nose", obsidian().addBox(-14.3F, -3, -43, 28.6F, 10, 7), PartPose.ZERO);
        head.addOrReplaceChild("nostrils", gold()
                .addBox(-10.5F, -3.6F, -43.3F, 5, 2.2F, 5)
                .addBox(5.5F, -3.6F, -43.3F, 5, 2.2F, 5), PartPose.ZERO);
        head.addOrReplaceChild("eyes", cyan()
                .addBox(-17.6F, -8.5F, -22, 2.6F, 6.5F, 10)
                .addBox(15, -8.5F, -22, 2.6F, 6.5F, 10), PartPose.ZERO);
        head.addOrReplaceChild("forehead_mask", gold().addBox(-4.5F, -18.7F, -17, 9, 2.2F, 18), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            head.addOrReplaceChild("brow_" + side, gold().addBox(-2.2F, -2, -9, 5, 4, 18),
                    PartPose.offsetAndRotation(side * 15.8F, -9.6F, -17,
                            -0.1F, side * -0.13F, side * -0.3F));
            PartDefinition horn = head.addOrReplaceChild("antler_" + side, gold()
                            .addBox(-2.8F, -20, -2.8F, 5.6F, 21, 5.6F),
                    PartPose.offsetAndRotation(side * 12, -17, 3, -0.4F, side * 0.2F, side * 0.34F));
            PartDefinition tip = horn.addOrReplaceChild("tip", gold().addBox(-2, -18, -2, 4, 19, 4),
                    PartPose.offsetAndRotation(0, -19, 0, -0.45F, 0, side * 0.26F));
            tip.addOrReplaceChild("crown", gold().addBox(-1.1F, -12, -1.1F, 2.2F, 13, 2.2F),
                    PartPose.offsetAndRotation(0, -17, 0, 0.46F, 0, side * -0.35F));
            horn.addOrReplaceChild("prong", gold().addBox(-1.5F, -16, -1.5F, 3, 17, 3),
                    PartPose.offsetAndRotation(0, -9, -1, 0.8F, 0, side * 0.42F));
            tip.addOrReplaceChild("prong", gold().addBox(-1.1F, -13, -1.1F, 2.2F, 14, 2.2F),
                    PartPose.offsetAndRotation(0, -8, 0, -0.6F, 0, side * 0.55F));
            for (int j = 0; j < 8; j++) {
                head.addOrReplaceChild("mane_" + side + "_" + j,
                        gold().addBox(-2.2F, -3, -1, 4.4F, 6, 24 - Math.abs(j - 3) * 1.6F),
                        PartPose.offsetAndRotation(side * (12.5F + (j % 2) * 2.2F), -16 + j * 3.4F,
                                -3 + j * 0.7F, -0.4F + j * 0.1F, side * (0.45F + j * 0.045F), side * 0.1F));
            }
            PartDefinition whisker = head.addOrReplaceChild(side == 1 ? "whisker_left" : "whisker_right",
                    gold().addBox(side > 0 ? 0 : -12, -0.8F, -0.8F, 12, 1.6F, 1.6F),
                    PartPose.offsetAndRotation(side * 12, 7, -34, 0, side * 0.35F, side * -0.2F));
            for (int j = 0; j < 4; j++) {
                whisker = whisker.addOrReplaceChild("curl_" + j,
                        gold().addBox(side > 0 ? 0 : -10, -0.6F, -0.6F, 10, 1.2F, 1.2F),
                        PartPose.offsetAndRotation(side * (j == 0 ? 12 : 10), 0, 0,
                                0, side * (j < 2 ? -0.35F : 0.28F), side * -0.25F));
            }
        }
        head.addOrReplaceChild("forehead_crest", gold().addBox(-2, -12, -7, 4, 13, 13),
                PartPose.offsetAndRotation(0, -18, -5, -0.25F, 0, 0));
        PartDefinition jaw = head.addOrReplaceChild("jaw", obsidian()
                .addBox(-13, 0, -36, 26, 7, 36), PartPose.offset(0, 10, -6));
        jaw.addOrReplaceChild("jaw_gold", gold().addBox(-13.4F, 5, -36.3F, 26.8F, 3.2F, 36.5F), PartPose.ZERO);
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(320, 0)
                .addBox(-7, -0.7F, -32, 14, 1.5F, 17), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            for (int j = 0; j < 5; j++) {
                head.addOrReplaceChild("fang_" + side + "_" + j,
                        ivory().addBox(-1.3F, 0, -1.2F, 2.6F, j == 0 ? 9 : 5, 2.4F),
                        PartPose.offset(side * 11.8F, 9.5F, -38 + j * 5));
                jaw.addOrReplaceChild("tooth_" + side + "_" + j,
                        ivory().addBox(-1.1F, -5.5F, -1.2F, 2.2F, 5.5F, 2.4F),
                        PartPose.offset(side * 11.5F, 0, -32 + j * 5));
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
                .addBox(-15, -14, -9, 30, 4, 25).addBox(-14, -24, 12, 28, 11, 5), PartPose.ZERO);
        saddle.addOrReplaceChild("trim", gold()
                .addBox(-16, -15, -10, 2, 5, 28).addBox(14, -15, -10, 2, 5, 28)
                .addBox(-16, -15, -10, 32, 2, 2).addBox(-15, -25, 12, 30, 2, 6), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            saddle.addOrReplaceChild("stirrup_" + side, gold()
                    .addBox(side * 17 - 0.7F, -11, -3, 1.4F, 21, 1.4F)
                    .addBox(side * 17 - 2.6F, 8, -5, 5.2F, 1.8F, 5), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 512, 256);
    }

    private static void addLeg(PartDefinition parent, String name, int side, float width) {
        // Grasping limbs stay secondary to the serpent silhouette instead of acting like a lizard's legs.
        PartDefinition leg = parent.addOrReplaceChild(name, obsidian().addBox(-3, -2, -3.5F, 6, 10, 7),
                PartPose.offsetAndRotation(side * (width / 2 - 1), 3, 0, -0.55F, side * 0.08F, side * -0.95F));
        leg.addOrReplaceChild("cuff", gold().addBox(-3.6F, 4.5F, -4, 7.2F, 3.2F, 8), PartPose.ZERO);
        PartDefinition ankle = leg.addOrReplaceChild("ankle", obsidian().addBox(-2.6F, -1, -3, 5.2F, 9, 6),
                PartPose.offsetAndRotation(side * 1.4F, 7, 0, 0.9F, 0, side * 0.35F));
        ankle.addOrReplaceChild("palm", obsidian().addBox(-4.1F, 5, -6, 8.2F, 3.2F, 8), PartPose.ZERO);
        for (int toe = -1; toe <= 1; toe++) {
            ankle.addOrReplaceChild("claw_" + toe, ivory().addBox(-1.1F, -1, -7.5F, 2.2F, 2.4F, 8),
                    PartPose.offsetAndRotation(toe * 3, 7, -4, 0.45F, toe * -0.18F, 0));
        }
        ankle.addOrReplaceChild("thumb", ivory().addBox(-1, -1, -6, 2, 2.5F, 6.5F),
                PartPose.offsetAndRotation(side * 4, 6, -1, 0.25F, side * 0.9F, 0));
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
        for (int i = 0; i < legs.length; i++) {
            legs[i].xRot += Mth.sin(time * 0.12F - i * 0.8F) * 0.09F - attack.brace() * (i < 2 ? 0.35F : 0.1F);
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
