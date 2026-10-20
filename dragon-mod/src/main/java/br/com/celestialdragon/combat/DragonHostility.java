package br.com.celestialdragon.combat;

import br.com.celestialdragon.entity.CelestialDragonEntity;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import java.util.WeakHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;

/** Gives both hostile and normally peaceful mobs a response to a nearby dragon. */
public final class DragonHostility {
    private static final double RANGE = 35.0;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final Set<Mob> INSTALLED = Collections.newSetFromMap(new WeakHashMap<>());
    private static boolean registered;

    private DragonHostility() {}

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Mob mob && !(mob instanceof CelestialDragonEntity) && INSTALLED.add(mob)) {
                mob.getGoalSelector().addGoal(0, new ConfrontDragonGoal(mob));
            }
        });
    }

    private static final class ConfrontDragonGoal extends Goal {
        private final Mob mob;
        private CelestialDragonEntity dragon;
        private int nextSearchTick;
        private int nextPathTick;
        private int nextAttackTick;

        private ConfrontDragonGoal(Mob mob) {
            this.mob = mob;
            this.nextSearchTick = mob.tickCount + mob.getRandom().nextInt(20);
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
        }

        @Override
        public boolean canUse() {
            if (!mob.isAlive() || mob.isNoAi() || mob.tickCount < nextSearchTick) {
                return false;
            }
            nextSearchTick = mob.tickCount + 20;
            double nearest = RANGE_SQUARED;
            dragon = null;
            // This uses the world's section index, never a global entity or mob scan.
            for (CelestialDragonEntity candidate : mob.level().getEntitiesOfClass(
                    CelestialDragonEntity.class, mob.getBoundingBox().inflate(RANGE))) {
                double distance = mob.distanceToSqr(candidate);
                if (distance <= nearest && canConfront(candidate)) {
                    dragon = candidate;
                    nearest = distance;
                }
            }
            return dragon != null;
        }

        private boolean canConfront(CelestialDragonEntity candidate) {
            return candidate.isAlive()
                    && !candidate.isInvulnerable()
                    && !mob.isPassengerOfSameVehicle(candidate)
                    && !CelestialDragonEntity.isProtectedAlly(candidate, mob)
                    && !candidate.isAlliedTo(mob) && !mob.isAlliedTo(candidate)
                    && mob.distanceToSqr(candidate) <= RANGE_SQUARED
                    && mob.hasLineOfSight(candidate);
        }

        @Override
        public boolean canContinueToUse() {
            return mob.isAlive() && !mob.isNoAi() && dragon != null && canConfront(dragon);
        }

        @Override
        public void start() {
            mob.setTarget(dragon);
            mob.setAggressive(true);
            nextPathTick = mob.tickCount;
        }

        @Override
        public void stop() {
            if (mob.getTarget() == dragon) {
                mob.setTarget(null);
            }
            mob.setAggressive(false);
            mob.getNavigation().stop();
            dragon = null;
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            if (dragon == null || !(mob.level() instanceof ServerLevel level) || !canConfront(dragon)) {
                return;
            }
            mob.getLookControl().setLookAt(dragon, 30.0F, 30.0F);
            if (mob.tickCount >= nextPathTick) {
                nextPathTick = mob.tickCount + 10;
                mob.getNavigation().moveTo(dragon, 1.15);
            }
            if (mob.tickCount >= nextAttackTick && mob.isWithinMeleeAttackRange(dragon)) {
                nextAttackTick = mob.tickCount + 20;
                // Cows, sheep, etc. have no ATTACK_DAMAGE attribute; asking for it
                // through doHurtTarget would fail instead of letting them retaliate.
                if (mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                        && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0.0) {
                    mob.doHurtTarget(level, dragon);
                } else {
                    dragon.hurtServer(level, mob.damageSources().mobAttack(mob), 2.0F);
                }
            }
        }
    }
}
