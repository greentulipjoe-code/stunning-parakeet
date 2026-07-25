package com.fleshterror.entity.ai;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * A many-legged blob doesn't need to face the direction it's walking - real crabs (and this
 * thing's tentacle-legs) can scuttle sideways just fine. This control turns the body slowly
 * toward the travel direction while still translating along the full path vector immediately,
 * so it visibly strafes/sidesteps rather than snapping to face every waypoint.
 */
public class FleshMoveControl extends MoveControl {

    private final Mob mob;

    public FleshMoveControl(Mob mob) {
        super(mob);
        this.mob = mob;
    }

    @Override
    public void tick() {
        if (this.operation != Operation.MOVE_TO) {
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        double dx = this.wantedX - mob.getX();
        double dy = this.wantedY - mob.getY();
        double dz = this.wantedZ - mob.getZ();
        double distSqr = dx * dx + dy * dy + dz * dz;

        if (distSqr < 2.5000001E-7) {
            this.operation = Operation.WAIT;
            mob.setZza(0.0F);
            mob.setXxa(0.0F);
            return;
        }

        float desiredYaw = (float) (Mth.atan2(dz, dx) * (180F / Math.PI)) - 90.0F;
        // Slow turn rate (crab-like) instead of vanilla's near-instant snap to face travel dir.
        mob.setYRot(rotateTowards(mob.getYRot(), desiredYaw, 12.0F));
        mob.yBodyRot = mob.getYRot();

        double speedAttr = mob.getAttribute(Attributes.MOVEMENT_SPEED) != null
                ? mob.getAttribute(Attributes.MOVEMENT_SPEED).getValue() : 0.2D;
        float speed = (float) (this.speedModifier * speedAttr);
        mob.setSpeed(speed);

        double horizLen = Math.sqrt(dx * dx + dz * dz);
        if (horizLen < 1.0E-5) {
            mob.setZza(speed);
            mob.setXxa(0.0F);
            return;
        }
        double ndx = dx / horizLen;
        double ndz = dz / horizLen;

        double yawRad = Math.toRadians(mob.getYRot());
        double fx = -Math.sin(yawRad);
        double fz = Math.cos(yawRad);
        double rx = fz;
        double rz = -fx;

        float forwardAmt = (float) (ndx * fx + ndz * fz);
        float strafeAmt = (float) (ndx * rx + ndz * rz);

        mob.setZza(forwardAmt * speed * 3.5F);
        mob.setXxa(strafeAmt * speed * 3.5F);
    }

    private static float rotateTowards(float current, float target, float maxDeltaDeg) {
        float delta = Mth.wrapDegrees(target - current);
        delta = Mth.clamp(delta, -maxDeltaDeg, maxDeltaDeg);
        return current + delta;
    }
}
