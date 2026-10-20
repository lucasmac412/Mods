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

/** A wingless Chinese long: sixteen articulated vertebrae, antlers and flowing whiskers. */
public final class DragonModel extends EntityModel<DragonRenderState> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
            CelestialDragonMod.id("celestial_dragon"), "main");
    private static final int SEGMENTS = 16;
    private final ModelPart body;
    private final ModelPart neck;
    private final ModelPart head;
    private final ModelPart jaw;
    private final ModelPart saddle;
    private final ModelPart[] segments = new ModelPart[SEGMENTS];
    private final ModelPart[] legs = new ModelPart[4];
    private final ModelPart[] whiskers = new ModelPart[2];

    public DragonModel(ModelPart root) {
        super(root);
        body = root.getChild("body");
        neck = body.getChild("neck");
        head = neck.getChild("head");
        jaw = head.getChild("jaw");
        saddle = body.getChild("saddle");
        ModelPart previous = body;
        for (int i = 0; i < SEGMENTS; i++) {
            segments[i] = previous.getChild("segment_" + i);
            previous = segments[i];
        }
        legs[0] = segments[1].getChild("left_foreleg");
        legs[1] = segments[1].getChild("right_foreleg");
        legs[2] = segments[7].getChild("left_hindleg");
        legs[3] = segments[7].getChild("right_hindleg");
        whiskers[0] = head.getChild("whisker_left");
        whiskers[1] = head.getChild("whisker_right");
    }

    private static CubeListBuilder obsidian() { return CubeListBuilder.create().texOffs(0, 0); }
    private static CubeListBuilder gold() { return CubeListBuilder.create().texOffs(128, 0); }
    private static CubeListBuilder cyan() { return CubeListBuilder.create().texOffs(256, 0); }
    private static CubeListBuilder ivory() { return CubeListBuilder.create().texOffs(384, 0); }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition body = mesh.getRoot().addOrReplaceChild("body", CubeListBuilder.create(),
                PartPose.offset(0, -7, 0));
        PartDefinition previous = body;
        for (int i = 0; i < SEGMENTS; i++) {
            float width = i < 8 ? 20 - i * 0.55F : 16 - (i - 7) * 1.25F;
            float height = width * 0.86F;
            PartDefinition segment = previous.addOrReplaceChild("segment_" + i, obsidian()
                    .addBox(-width / 2, -height / 2, -6, width, height, 14),
                    PartPose.offset(0, 0, i == 0 ? 0 : 12));
            segment.addOrReplaceChild("belly", gold()
                    .addBox(-width * 0.43F, height / 2 - 1.5F, -6.3F, width * 0.86F, 2.2F, 12.6F), PartPose.ZERO);
            segment.addOrReplaceChild("crest", gold()
                    .addBox(-1, -height / 2 - 7, -2, 2, 8, 5),
                    PartPose.rotation(-0.30F, 0, 0));
            for (int side : new int[]{-1, 1}) {
                segment.addOrReplaceChild("gilt_" + side, gold()
                        .addBox(side < 0 ? -width / 2 - 0.3F : width / 2 - 1.2F,
                                -height * 0.3F, -1, 1.5F, 3.5F, 6), PartPose.ZERO);
            }
            if (i == 1 || i == 7) {
                addLeg(segment, i == 1 ? "left_foreleg" : "left_hindleg", 1, width);
                addLeg(segment, i == 1 ? "right_foreleg" : "right_hindleg", -1, width);
            }
            previous = segment;
        }
        PartDefinition tail = previous.addOrReplaceChild("tail_tuft", CubeListBuilder.create(), PartPose.offset(0, 0, 8));
        for (int j = -3; j <= 3; j++) {
            tail.addOrReplaceChild("plume_" + j, gold().addBox(-1.2F, -2, -1, 2.4F, 4, 20 - Math.abs(j) * 2),
                    PartPose.offsetAndRotation(j * 1.3F, -Math.abs(j), 0, -0.18F - Math.abs(j) * 0.08F, j * 0.18F, 0));
        }

        PartDefinition neck = body.addOrReplaceChild("neck", obsidian()
                .addBox(-9, -8, -17, 18, 16, 20), PartPose.offset(0, -1, -8));
        neck.addOrReplaceChild("neck_belly", gold().addBox(-8, 6.5F, -18, 16, 2.5F, 19), PartPose.ZERO);
        PartDefinition head = neck.addOrReplaceChild("head", obsidian()
                .addBox(-11, -9, -15, 22, 17, 20)
                .addBox(-8, -1, -29, 16, 8, 17), PartPose.offset(0, -5, -16));
        head.addOrReplaceChild("nose_rim", gold().addBox(-8.7F, 4, -29.5F, 17.4F, 3, 17), PartPose.ZERO);
        head.addOrReplaceChild("nose", obsidian().addBox(-8.5F, -2, -30, 17, 6, 5), PartPose.ZERO);
        head.addOrReplaceChild("nostrils", gold()
                .addBox(-6.3F, -2.5F, -30.3F, 3.5F, 1.5F, 3)
                .addBox(2.8F, -2.5F, -30.3F, 3.5F, 1.5F, 3), PartPose.ZERO);
        head.addOrReplaceChild("eyes", cyan()
                .addBox(-11.3F, -4.5F, -16, 2, 4.5F, 7)
                .addBox(9.3F, -4.5F, -16, 2, 4.5F, 7), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            head.addOrReplaceChild("brow_" + side, gold().addBox(-1, -2, -6, 4, 3, 12),
                    PartPose.offsetAndRotation(side * 9.3F, -5.2F, -12, -0.1F, side * -0.15F, side * -0.2F));
            PartDefinition horn = head.addOrReplaceChild("antler_" + side, gold()
                    .addBox(-2, -13, -2, 4, 14, 4),
                    PartPose.offsetAndRotation(side * 7, -9, 0, -0.34F, side * 0.18F, side * 0.33F));
            PartDefinition tip = horn.addOrReplaceChild("tip", gold().addBox(-1.4F, -13, -1.4F, 2.8F, 14, 2.8F),
                    PartPose.offsetAndRotation(0, -12, 0, -0.4F, 0, side * 0.25F));
            tip.addOrReplaceChild("crown", gold().addBox(-0.8F, -8, -0.8F, 1.6F, 9, 1.6F),
                    PartPose.offsetAndRotation(0, -12, 0, 0.5F, 0, side * -0.4F));
            horn.addOrReplaceChild("prong", gold().addBox(-1, -11, -1, 2, 12, 2),
                    PartPose.offsetAndRotation(0, -6, -1, 0.75F, 0, side * 0.4F));
            tip.addOrReplaceChild("prong", gold().addBox(-0.8F, -9, -0.8F, 1.6F, 10, 1.6F),
                    PartPose.offsetAndRotation(0, -6, 0, -0.55F, 0, side * 0.6F));
            for (int j = 0; j < 7; j++) {
                head.addOrReplaceChild("mane_" + side + "_" + j, gold()
                                .addBox(-1.5F, -2, -1, 3, 4, 16 - Math.abs(j - 3)),
                        PartPose.offsetAndRotation(side * (8 + (j % 2) * 1.8F), -8 + j * 2.5F, -2 + j * 0.7F,
                                -0.30F + j * 0.11F, side * (0.45F + j * 0.055F), side * 0.1F));
            }
            PartDefinition whisker = head.addOrReplaceChild(side == 1 ? "whisker_left" : "whisker_right",
                    gold().addBox(side > 0 ? 0 : -10, -0.6F, -0.6F, 10, 1.2F, 1.2F),
                    PartPose.offsetAndRotation(side * 7, 4, -24, 0, side * 0.45F, side * -0.20F));
            for (int j = 0; j < 4; j++) {
                whisker = whisker.addOrReplaceChild("curl_" + j,
                        gold().addBox(side > 0 ? 0 : -9, -0.45F, -0.45F, 9, 0.9F, 0.9F),
                        PartPose.offsetAndRotation(side * (j == 0 ? 10 : 9), 0, 0,
                                0, side * (j < 2 ? -0.38F : 0.25F), side * -0.28F));
            }
        }
        head.addOrReplaceChild("forehead_crest", gold().addBox(-1.5F, -16, -11, 3, 8, 9),
                PartPose.rotation(-0.22F, 0, 0));
        PartDefinition jaw = head.addOrReplaceChild("jaw", obsidian()
                .addBox(-8, 0, -22, 16, 5, 25), PartPose.offset(0, 7, -5));
        jaw.addOrReplaceChild("jaw_gold", gold().addBox(-8.6F, 3, -22.5F, 17.2F, 3, 25), PartPose.ZERO);
        jaw.addOrReplaceChild("tongue", CubeListBuilder.create().texOffs(320, 0)
                .addBox(-5.5F, -0.7F, -21, 11, 1.5F, 17), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            for (int j = 0; j < 5; j++) {
                head.addOrReplaceChild("fang_" + side + "_" + j, ivory()
                                .addBox(-1, 0, -1, 2, j == 0 ? 6 : 3.5F, 2),
                        PartPose.offset(side * 6.8F, 6.5F, -26 + j * 4.3F));
                jaw.addOrReplaceChild("tooth_" + side + "_" + j, ivory().addBox(-0.8F, -3.2F, -1, 1.6F, 3.2F, 2),
                        PartPose.offset(side * 6.5F, 0, -20 + j * 4.3F));
            }
        }
        jaw.addOrReplaceChild("beard", gold().addBox(-2, 3, -3, 4, 14, 3),
                PartPose.offsetAndRotation(0, 3, -13, -0.45F, 0, 0));

        PartDefinition saddle = body.addOrReplaceChild("saddle", CubeListBuilder.create().texOffs(0, 128)
                .addBox(-11, -12, -7, 22, 4, 20).addBox(-10, -18, 10, 20, 9, 4), PartPose.ZERO);
        saddle.addOrReplaceChild("trim", gold()
                .addBox(-12, -13, -8, 2, 5, 23).addBox(10, -13, -8, 2, 5, 23)
                .addBox(-12, -13, -8, 24, 2, 2).addBox(-11, -19, 10, 22, 2, 5), PartPose.ZERO);
        for (int side : new int[]{-1, 1}) {
            saddle.addOrReplaceChild("stirrup_" + side, gold()
                    .addBox(side * 12 - 0.6F, -9, -2, 1.2F, 17, 1.2F)
                    .addBox(side * 12 - 2, 7, -3, 4, 1.5F, 4), PartPose.ZERO);
        }
        return LayerDefinition.create(mesh, 512, 256);
    }

    private static void addLeg(PartDefinition parent, String name, int side, float width) {
        PartDefinition leg = parent.addOrReplaceChild(name, obsidian().addBox(-3.5F, -2, -4, 7, 12, 8),
                PartPose.offsetAndRotation(side * (width / 2 - 2), 3, 0, -0.38F, 0, side * -0.55F));
        PartDefinition ankle = leg.addOrReplaceChild("ankle", obsidian().addBox(-2.8F, -1, -3, 5.6F, 10, 6),
                PartPose.offsetAndRotation(side * 2, 9, 1, 0.8F, 0, side * 0.3F));
        ankle.addOrReplaceChild("foot", obsidian().addBox(-4, 7, -7, 8, 4, 10), PartPose.ZERO);
        for (int toe = -1; toe <= 1; toe++) {
            ankle.addOrReplaceChild("claw_" + toe, gold().addBox(-0.9F, -1, -6, 1.8F, 2.5F, 7),
                    PartPose.offsetAndRotation(toe * 2.6F, 9, -5, 0.25F, toe * -0.15F, 0));
        }
    }

    @Override
    public void setupAnim(DragonRenderState state) {
        super.setupAnim(state);
        float time = state.ageInTicks;
        body.y += Mth.sin(time * 0.07F) * 0.7F;
        saddle.visible = state.tamed;
        for (int i = 0; i < SEGMENTS; i++) {
            float phase = time * 0.095F - i * 0.55F;
            segments[i].xRot = Mth.sin(phase) * (i == 0 ? 0.025F : 0.11F);
            segments[i].yRot = Mth.sin(phase * 0.8F + 1.2F) * (i == 0 ? 0.02F : 0.085F);
            segments[i].zRot = Mth.sin(phase) * 0.018F;
        }
        neck.xRot = Mth.clamp(state.xRot * Mth.DEG_TO_RAD * 0.35F, -0.35F, 0.35F);
        head.yRot = Mth.clamp(state.yRot * Mth.DEG_TO_RAD * 0.6F, -0.65F, 0.65F);
        head.xRot = Mth.sin(time * 0.065F) * 0.035F;
        jaw.xRot = state.beamTicks > 0 ? 0.72F
                : state.biteTicks > 0 ? Mth.sin((1 - state.biteTicks / 12.0F) * Mth.PI) * 0.88F : 0.08F;
        for (int i = 0; i < legs.length; i++) {
            legs[i].xRot += Mth.sin(time * 0.1F + i * Mth.PI / 2) * 0.13F;
        }
        whiskers[0].zRot += Mth.sin(time * 0.08F) * 0.12F;
        whiskers[1].zRot -= Mth.sin(time * 0.08F + 0.8F) * 0.12F;
    }
}
