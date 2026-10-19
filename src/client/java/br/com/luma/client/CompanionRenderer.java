package br.com.luma.client;

import br.com.luma.entity.CompanionEntity;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;

/** A humanoid NPC with its own original 64 x 64 skin and animated clothing. */
public final class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity,
        HumanoidRenderState, HumanoidModel<HumanoidRenderState>> {
    public static final ModelLayerLocation MODEL_LAYER = new ModelLayerLocation(
            Identifier.fromNamespaceAndPath("luma", "companion"), "main");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            "luma", "textures/entity/luma.png");

    public CompanionRenderer(EntityRendererProvider.Context context) {
        // HumanoidMobRenderer supplies item-in-hand animations for weapons and tools.
        super(context, new HumanoidModel<>(context.bakeLayer(MODEL_LAYER)), 0.45F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = HumanoidModel.createMesh(CubeDeformation.NONE, 0.0F);
        PartDefinition root = mesh.getRoot();
        // Give the left limbs their modern skin UVs, rather than mirroring the right side.
        PartDefinition leftArm = root.addOrReplaceChild("left_arm", CubeListBuilder.create()
                .texOffs(32, 48).addBox(-1, -2, -2, 4, 12, 4), PartPose.offset(5, 2, 0));
        PartDefinition leftLeg = root.addOrReplaceChild("left_leg", CubeListBuilder.create()
                .texOffs(16, 48).addBox(-2, 0, -2, 4, 12, 4), PartPose.offset(1.9F, 12, 0));
        CubeDeformation clothing = new CubeDeformation(0.25F);
        root.getChild("body").addOrReplaceChild("jacket", CubeListBuilder.create()
                .texOffs(16, 32).addBox(-4, 0, -2, 8, 12, 4, clothing), PartPose.ZERO);
        root.getChild("right_arm").addOrReplaceChild("sleeve", CubeListBuilder.create()
                .texOffs(40, 32).addBox(-3, -2, -2, 4, 12, 4, clothing), PartPose.ZERO);
        leftArm.addOrReplaceChild("sleeve", CubeListBuilder.create()
                .texOffs(48, 48).addBox(-1, -2, -2, 4, 12, 4, clothing), PartPose.ZERO);
        root.getChild("right_leg").addOrReplaceChild("trouser", CubeListBuilder.create()
                .texOffs(0, 32).addBox(-2, 0, -2, 4, 12, 4, clothing), PartPose.ZERO);
        leftLeg.addOrReplaceChild("trouser", CubeListBuilder.create()
                .texOffs(0, 48).addBox(-2, 0, -2, 4, 12, 4, clothing), PartPose.ZERO);
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public HumanoidRenderState createRenderState() {
        return new HumanoidRenderState();
    }

    @Override
    public void extractRenderState(CompanionEntity entity, HumanoidRenderState state, float tickProgress) {
        super.extractRenderState(entity, state, tickProgress);
        ItemStack rightHand = entity.getMainArm() == HumanoidArm.RIGHT
                ? entity.getMainHandItem() : entity.getOffhandItem();
        ItemStack leftHand = entity.getMainArm() == HumanoidArm.LEFT
                ? entity.getMainHandItem() : entity.getOffhandItem();
        state.rightArmPose = rightHand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
        state.leftArmPose = leftHand.isEmpty() ? HumanoidModel.ArmPose.EMPTY : HumanoidModel.ArmPose.ITEM;
    }

    @Override
    public Identifier getTextureLocation(HumanoidRenderState state) {
        return TEXTURE;
    }
}
