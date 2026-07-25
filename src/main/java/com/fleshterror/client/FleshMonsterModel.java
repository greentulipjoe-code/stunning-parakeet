package com.fleshterror.client;

import com.fleshterror.entity.FleshMonsterEntity;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * A lumpy, amorphous blob body held up by four three-jointed leg-tentacles that plant on the
 * ground and let it scuttle, plus four long, five-jointed arm-tentacles that stretch out and
 * curl progressively (each joint bends a bit more than the last) to fully wrap around a block
 * when grabbing. Whole thing is scaled again per growth-stage by FleshMonsterRenderer.
 */
public class FleshMonsterModel extends HierarchicalModel<FleshMonsterEntity> {

    private static final int LEG_COUNT = 4;
    private static final int ARM_COUNT = 4;
    private static final int ARM_JOINTS = 5; // base, mid1, mid2, tip, grip
    // Which arm indices count as the "front" pair that visibly reaches out to grab things.
    private static final int FRONT_ARM_A = 0;
    private static final int FRONT_ARM_B = 1;

    private final ModelPart root;
    private final ModelPart body;

    private final ModelPart[] legBase = new ModelPart[LEG_COUNT];
    private final ModelPart[] legMid = new ModelPart[LEG_COUNT];
    private final ModelPart[] legTip = new ModelPart[LEG_COUNT];

    // armJoints[armIndex][jointIndex]
    private final ModelPart[][] armJoints = new ModelPart[ARM_COUNT][ARM_JOINTS];

    public FleshMonsterModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        for (int i = 0; i < LEG_COUNT; i++) {
            ModelPart base = root.getChild("leg_base_" + i);
            ModelPart mid = base.getChild("leg_mid_" + i);
            ModelPart tip = mid.getChild("leg_tip_" + i);
            legBase[i] = base;
            legMid[i] = mid;
            legTip[i] = tip;
        }
        for (int i = 0; i < ARM_COUNT; i++) {
            ModelPart j0 = root.getChild("arm_j0_" + i);
            ModelPart j1 = j0.getChild("arm_j1_" + i);
            ModelPart j2 = j1.getChild("arm_j2_" + i);
            ModelPart j3 = j2.getChild("arm_j3_" + i);
            ModelPart j4 = j3.getChild("arm_j4_" + i);
            armJoints[i][0] = j0;
            armJoints[i][1] = j1;
            armJoints[i][2] = j2;
            armJoints[i][3] = j3;
            armJoints[i][4] = j4;
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition parts = mesh.getRoot();

        // ---- Blobby body: several overlapping lumps instead of one clean cube ----
        CubeListBuilder bodyCubes = CubeListBuilder.create()
                .texOffs(0, 0).addBox(-7.0F, -14.0F, -7.0F, 14.0F, 14.0F, 14.0F)   // core mass
                .texOffs(0, 0).addBox(-4.0F, -18.0F, -3.0F, 9.0F, 9.0F, 8.0F)      // upper lump
                .texOffs(0, 0).addBox(-3.0F, -3.0F, -4.0F, 7.0F, 6.0F, 7.0F)       // underbelly bulge
                .texOffs(0, 0).addBox(4.0F, -11.0F, -5.0F, 6.0F, 9.0F, 6.0F)       // right-side lump
                .texOffs(0, 0).addBox(-9.0F, -10.0F, -2.0F, 6.0F, 8.0F, 6.0F)      // left-side lump
                .texOffs(0, 0).addBox(-4.0F, -13.0F, 4.0F, 8.0F, 8.0F, 5.0F);      // rear lump

        parts.addOrReplaceChild("body", bodyCubes, PartPose.offset(0.0F, 18.0F, 0.0F));

        // ---- Legs: three joints each, rest pose lands exactly on the ground (y=24) ----
        double[] legX = {-6, 6, -6, 6};
        double[] legZ = {-6, -6, 6, 6};
        for (int i = 0; i < LEG_COUNT; i++) {
            float x = (float) legX[i];
            float z = (float) legZ[i];
            float leanX = z < 0 ? -0.18F : 0.18F;
            float leanZ = x < 0 ? -0.18F : 0.18F;
            int texX = i * 16;

            PartDefinition base = parts.addOrReplaceChild("leg_base_" + i,
                    CubeListBuilder.create().texOffs(texX, 32).addBox(-1.6F, 0.0F, -1.6F, 3.2F, 3.0F, 3.2F),
                    PartPose.offsetAndRotation(x, 15.0F, z, leanX, 0.0F, leanZ));

            PartDefinition mid = base.addOrReplaceChild("leg_mid_" + i,
                    CubeListBuilder.create().texOffs(texX, 44).addBox(-1.3F, 0.0F, -1.3F, 2.6F, 3.0F, 2.6F),
                    PartPose.offset(0.0F, 3.0F, 0.0F));

            mid.addOrReplaceChild("leg_tip_" + i,
                    CubeListBuilder.create().texOffs(texX, 56).addBox(-1.0F, 0.0F, -1.0F, 2.0F, 3.0F, 2.0F),
                    PartPose.offset(0.0F, 3.0F, 0.0F));
        }

        // ---- Arms: five-jointed tentacles, long enough to actually reach out and curl ----
        double[] armAngleDeg = {35, -35, 145, -145}; // front pair (0,1) does the visible grabbing
        // Joint lengths taper down the tentacle; total ~34 units (~2.1 blocks) before stage scaling.
        float[] jointLen = {8.0F, 7.5F, 7.0F, 6.5F, 4.0F};
        float[] jointThick = {3.6F, 3.0F, 2.4F, 1.8F, 1.2F};
        int[] jointRowY = {68, 84, 100, 116, 132};

        for (int i = 0; i < ARM_COUNT; i++) {
            double angle = Math.toRadians(armAngleDeg[i]);
            float x = (float) (Math.sin(angle) * 6.0);
            float z = (float) (Math.cos(angle) * 6.0);
            float yaw = (float) angle;
            int texX = i * 16;

            PartDefinition j0 = parts.addOrReplaceChild("arm_j0_" + i,
                    CubeListBuilder.create().texOffs(texX, jointRowY[0])
                            .addBox(-jointThick[0] / 2, 0.0F, -jointThick[0] / 2, jointThick[0], jointLen[0], jointThick[0]),
                    PartPose.offsetAndRotation(x, 4.0F, z, 1.1F, yaw, 0.0F));

            PartDefinition j1 = j0.addOrReplaceChild("arm_j1_" + i,
                    CubeListBuilder.create().texOffs(texX, jointRowY[1])
                            .addBox(-jointThick[1] / 2, 0.0F, -jointThick[1] / 2, jointThick[1], jointLen[1], jointThick[1]),
                    PartPose.offsetAndRotation(0.0F, jointLen[0], 0.0F, 0.28F, 0.0F, 0.0F));

            PartDefinition j2 = j1.addOrReplaceChild("arm_j2_" + i,
                    CubeListBuilder.create().texOffs(texX, jointRowY[2])
                            .addBox(-jointThick[2] / 2, 0.0F, -jointThick[2] / 2, jointThick[2], jointLen[2], jointThick[2]),
                    PartPose.offsetAndRotation(0.0F, jointLen[1], 0.0F, 0.30F, 0.0F, 0.0F));

            PartDefinition j3 = j2.addOrReplaceChild("arm_j3_" + i,
                    CubeListBuilder.create().texOffs(texX, jointRowY[3])
                            .addBox(-jointThick[3] / 2, 0.0F, -jointThick[3] / 2, jointThick[3], jointLen[3], jointThick[3]),
                    PartPose.offsetAndRotation(0.0F, jointLen[2], 0.0F, 0.34F, 0.0F, 0.0F));

            j3.addOrReplaceChild("arm_j4_" + i,
                    CubeListBuilder.create().texOffs(texX, jointRowY[4])
                            .addBox(-jointThick[4] / 2, 0.0F, -jointThick[4] / 2, jointThick[4], jointLen[4], jointThick[4]),
                    PartPose.offsetAndRotation(0.0F, jointLen[3], 0.0F, 0.4F, 0.0F, 0.0F));
        }

        return LayerDefinition.create(mesh, 128, 160);
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(FleshMonsterEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
        this.root().getAllParts().forEach(ModelPart::resetPose);

        // Legs: alternating trot/scuttle gait driven by actual movement (works for the crab-like
        // sideways strafing too, since it's tied to real velocity via limbSwing/limbSwingAmount).
        float[] legPhase = {0.0F, (float) Math.PI, (float) Math.PI, 0.0F};
        for (int i = 0; i < LEG_COUNT; i++) {
            float swing = Mth.cos(limbSwing * 0.6662F + legPhase[i]) * 0.9F * limbSwingAmount;
            legBase[i].xRot += swing;
            legMid[i].xRot += swing * 0.6F;
            legTip[i].xRot += Math.abs(swing) * 0.5F;
        }

        float t = ageInTicks * 0.06F;
        float curl = entity.isReaching() ? entity.getCurlProgress() : 0.0f;

        // Progressive per-joint curl weights: each joint further out bends more sharply than
        // the last, so at curl=1 the whole tentacle has wrapped tightly around the target.
        float[] curlWeight = {0.35F, 0.55F, 0.85F, 1.15F, 1.5F};

        for (int i = 0; i < ARM_COUNT; i++) {
            float phase = i * ((float) Math.PI * 2 / ARM_COUNT);
            boolean isFrontArm = (i == FRONT_ARM_A || i == FRONT_ARM_B);
            ModelPart[] joints = armJoints[i];

            if (isFrontArm && curl > 0.001f) {
                // Reach forward/down first, then curl each successive joint in tighter.
                joints[0].xRot += -0.5F * curl;
                for (int j = 1; j < ARM_JOINTS; j++) {
                    joints[j].xRot += curlWeight[j] * curl;
                }
            } else {
                // Idle ambient sway - slow, organic, reads as "alive" rather than attacking.
                float sway = Mth.sin(t + phase) * 0.2F;
                joints[0].yRot += sway * 0.4F;
                joints[0].xRot += Mth.cos(t * 0.7F + phase) * 0.08F;
                for (int j = 1; j < ARM_JOINTS; j++) {
                    joints[j].xRot += sway * (0.3F + j * 0.12F);
                }
            }
        }

        // Subtle body breathing pulse.
        float breathe = Mth.sin(ageInTicks * 0.05F) * 0.4F;
        body.y = 18.0F + breathe;
    }
}
