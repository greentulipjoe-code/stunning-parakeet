package com.fleshterror.entity.ai;

import com.fleshterror.entity.FleshMonsterEntity;
import com.fleshterror.init.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Full feeding behavior: walk over to a nearby block, reach/curl a tentacle at it (only once
 * actually in reach - it will NOT eat things it hasn't walked up to), then either bite it in
 * half (small stage, turns full blocks into their slab form) or rip it fully loose and carry
 * a visible flying chunk of it back to the body before it vanishes.
 *
 * Eats basically anything except natural stone (see fleshterror:inedible tag) - including
 * cobblestone, which is NOT on that blacklist.
 */
public class TentacleGrabGoal extends Goal {

    private final FleshMonsterEntity monster;

    private int cooldown;
    private BlockPos targetBlock;
    private int approachTicks;
    private int grabTicks; // only advances once actually within reach
    private List<BlockPos> cluster;
    private final List<CarriedChunk> carried = new ArrayList<>();
    private int lastRepathTick;

    private static final int WINDUP_TICKS = 8;   // tentacle curls/reaches before biting
    private static final int CARRY_TICKS = 10;   // grabbed chunk visibly flies to the mouth
    private static final int MAX_APPROACH_TICKS = 140; // give up if it can't reach the target

    /** Maps a full block to the vanilla slab it should turn into when bitten in half. */
    private static final Map<Block, Block> SLAB_EQUIVALENT = new HashMap<>();

    static {
        put(Blocks.OAK_PLANKS, Blocks.OAK_SLAB); put(Blocks.SPRUCE_PLANKS, Blocks.SPRUCE_SLAB);
        put(Blocks.BIRCH_PLANKS, Blocks.BIRCH_SLAB); put(Blocks.JUNGLE_PLANKS, Blocks.JUNGLE_SLAB);
        put(Blocks.ACACIA_PLANKS, Blocks.ACACIA_SLAB); put(Blocks.DARK_OAK_PLANKS, Blocks.DARK_OAK_SLAB);
        put(Blocks.MANGROVE_PLANKS, Blocks.MANGROVE_SLAB); put(Blocks.CHERRY_PLANKS, Blocks.CHERRY_SLAB);
        put(Blocks.BAMBOO_PLANKS, Blocks.BAMBOO_SLAB); put(Blocks.CRIMSON_PLANKS, Blocks.CRIMSON_SLAB);
        put(Blocks.WARPED_PLANKS, Blocks.WARPED_SLAB);
        put(Blocks.STONE_BRICKS, Blocks.STONE_BRICK_SLAB); put(Blocks.MOSSY_STONE_BRICKS, Blocks.MOSSY_STONE_BRICK_SLAB);
        put(Blocks.CRACKED_STONE_BRICKS, Blocks.STONE_BRICK_SLAB);
        put(Blocks.COBBLESTONE, Blocks.COBBLESTONE_SLAB); put(Blocks.MOSSY_COBBLESTONE, Blocks.MOSSY_COBBLESTONE_SLAB);
        put(Blocks.SMOOTH_STONE, Blocks.SMOOTH_STONE_SLAB);
        put(Blocks.SANDSTONE, Blocks.SANDSTONE_SLAB); put(Blocks.SMOOTH_SANDSTONE, Blocks.SMOOTH_SANDSTONE_SLAB);
        put(Blocks.CUT_SANDSTONE, Blocks.CUT_SANDSTONE_SLAB);
        put(Blocks.RED_SANDSTONE, Blocks.RED_SANDSTONE_SLAB); put(Blocks.SMOOTH_RED_SANDSTONE, Blocks.SMOOTH_RED_SANDSTONE_SLAB);
        put(Blocks.CUT_RED_SANDSTONE, Blocks.CUT_RED_SANDSTONE_SLAB);
        put(Blocks.BRICKS, Blocks.BRICK_SLAB);
        put(Blocks.NETHER_BRICKS, Blocks.NETHER_BRICK_SLAB); put(Blocks.RED_NETHER_BRICKS, Blocks.RED_NETHER_BRICK_SLAB);
        put(Blocks.QUARTZ_BLOCK, Blocks.QUARTZ_SLAB); put(Blocks.SMOOTH_QUARTZ, Blocks.SMOOTH_QUARTZ_SLAB);
        put(Blocks.PURPUR_BLOCK, Blocks.PURPUR_SLAB);
        put(Blocks.PRISMARINE, Blocks.PRISMARINE_SLAB); put(Blocks.PRISMARINE_BRICKS, Blocks.PRISMARINE_BRICK_SLAB);
        put(Blocks.DARK_PRISMARINE, Blocks.DARK_PRISMARINE_SLAB);
        put(Blocks.END_STONE_BRICKS, Blocks.END_STONE_BRICK_SLAB);
        put(Blocks.BLACKSTONE, Blocks.BLACKSTONE_SLAB); put(Blocks.POLISHED_BLACKSTONE, Blocks.POLISHED_BLACKSTONE_SLAB);
        put(Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.POLISHED_BLACKSTONE_BRICK_SLAB);
        put(Blocks.COBBLED_DEEPSLATE, Blocks.COBBLED_DEEPSLATE_SLAB); put(Blocks.POLISHED_DEEPSLATE, Blocks.POLISHED_DEEPSLATE_SLAB);
        put(Blocks.DEEPSLATE_BRICKS, Blocks.DEEPSLATE_BRICK_SLAB); put(Blocks.DEEPSLATE_TILES, Blocks.DEEPSLATE_TILE_SLAB);
        put(Blocks.MUD_BRICKS, Blocks.MUD_BRICK_SLAB);
        put(Blocks.CUT_COPPER, Blocks.CUT_COPPER_SLAB); put(Blocks.EXPOSED_CUT_COPPER, Blocks.EXPOSED_CUT_COPPER_SLAB);
        put(Blocks.WEATHERED_CUT_COPPER, Blocks.WEATHERED_CUT_COPPER_SLAB); put(Blocks.OXIDIZED_CUT_COPPER, Blocks.OXIDIZED_CUT_COPPER_SLAB);
        put(Blocks.POLISHED_GRANITE, Blocks.POLISHED_GRANITE_SLAB); put(Blocks.POLISHED_DIORITE, Blocks.POLISHED_DIORITE_SLAB);
        put(Blocks.POLISHED_ANDESITE, Blocks.POLISHED_ANDESITE_SLAB);
    }

    private static void put(Block full, Block slab) {
        SLAB_EQUIVALENT.put(full, slab);
    }

    public TentacleGrabGoal(FleshMonsterEntity monster) {
        this.monster = monster;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (monster.getTarget() != null) return false; // prioritize fighting players over snacking on walls
        if (cooldown-- > 0) return false;
        return findNearbyEdibleBlock() != null;
    }

    @Override
    public void start() {
        this.targetBlock = findNearbyEdibleBlock();
        this.approachTicks = 0;
        this.grabTicks = 0;
        this.cluster = null;
        this.carried.clear();
        this.lastRepathTick = -999;
        int baseCooldown = 45 - monster.getGrowthStage() * 6;
        this.cooldown = Math.max(12, baseCooldown);
    }

    @Override
    public boolean canContinueToUse() {
        if (targetBlock == null || monster.getTarget() != null) return false;
        if (approachTicks > MAX_APPROACH_TICKS) return false;
        return grabTicks < WINDUP_TICKS + CARRY_TICKS;
    }

    private double reachRange() {
        // How far the tentacles can actually stretch, in blocks - grows with the monster,
        // and keeps growing even past max stage as it racks up bonus growth.
        double bonusReach = Math.min(6.0, monster.getBonusGrowth() * 0.03);
        return 2.0 + monster.getGrowthStage() * 1.6 + bonusReach;
    }

    @Override
    public void tick() {
        if (targetBlock == null) return;
        Level level = monster.level();

        Vec3 targetCenter = Vec3.atCenterOf(targetBlock);
        double dist = monster.position().distanceTo(targetCenter);

        if (dist > reachRange()) {
            // --- Still approaching: walk over, but don't spam-recalculate the path ---
            monster.setReaching(false);
            monster.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);
            if (monster.tickCount - lastRepathTick > 10 || monster.getNavigation().isDone()) {
                monster.getNavigation().moveTo(targetCenter.x, targetCenter.y, targetCenter.z, 1.0D);
                lastRepathTick = monster.tickCount;
            }
            approachTicks++;
            return;
        }

        // --- In reach: plant, stop wandering, and do the actual grab ---
        if (!monster.getNavigation().isDone()) monster.getNavigation().stop();
        monster.getLookControl().setLookAt(targetCenter.x, targetCenter.y, targetCenter.z);
        monster.setReaching(true);

        grabTicks++;

        if (!(level instanceof ServerLevel serverLevel)) return;

        if (grabTicks <= WINDUP_TICKS) {
            // Curling wind-up: particle trail arcing from the monster toward the block, and
            // the arm's curl-in amount ramps from 0 (relaxed) to 1 (fully wrapped around it).
            monster.setCurlProgress(grabTicks / (float) WINDUP_TICKS);
            Vec3 mouth = mouthPosition();
            double t = grabTicks / (double) WINDUP_TICKS;
            double px = lerp(t, mouth.x, targetCenter.x) + (serverLevel.random.nextDouble() - 0.5) * 0.3;
            double py = lerp(t, mouth.y, targetCenter.y) + (serverLevel.random.nextDouble() - 0.5) * 0.3;
            double pz = lerp(t, mouth.z, targetCenter.z) + (serverLevel.random.nextDouble() - 0.5) * 0.3;
            serverLevel.sendParticles(ParticleTypes.CRIMSON_SPORE, px, py, pz, 2, 0.03, 0.03, 0.03, 0.01);

            if (grabTicks == WINDUP_TICKS) {
                doBite(serverLevel);
            }
            return;
        }

        // Carrying phase: animate any grabbed chunks flying from the block to the mouth.
        // Arms stay fully curled (holding the chunk) until the very end, then relax.
        double progress = (grabTicks - WINDUP_TICKS) / (double) CARRY_TICKS;
        monster.setCurlProgress(1.0f - (float) (progress * progress * 0.6)); // slight ease-out relax while carrying
        Vec3 mouth = mouthPosition();
        for (CarriedChunk chunk : carried) {
            if (chunk.entity.isAlive()) {
                double x = lerp(progress, chunk.origin.x, mouth.x);
                double y = lerp(progress, chunk.origin.y, mouth.y) + Math.sin(progress * Math.PI) * 0.5;
                double z = lerp(progress, chunk.origin.z, mouth.z);
                chunk.entity.setPos(x, y, z);
                chunk.entity.setDeltaMovement(Vec3.ZERO);
            }
        }

        if (grabTicks >= WINDUP_TICKS + CARRY_TICKS) {
            finishSwallow(serverLevel);
            monster.setCurlProgress(0.0f);
        }
    }

    private void doBite(ServerLevel level) {
        int stage = monster.getGrowthStage();
        cluster = collectCluster(targetBlock, clusterSizeForStage(stage));

        int totalGrowth = 0;
        for (BlockPos pos : cluster) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) continue;

            boolean alreadySlab = isSlab(state);

            if (stage == 0 && !alreadySlab) {
                Block slabEquivalent = SLAB_EQUIVALENT.get(state.getBlock());
                if (slabEquivalent != null) {
                    // Bite it in half in place: no carry animation, just an instant chomp.
                    BlockState slabState = slabEquivalent.defaultBlockState();
                    if (slabState.hasProperty(SlabBlock.TYPE)) {
                        slabState = slabState.setValue(SlabBlock.TYPE, SlabType.BOTTOM);
                    }
                    level.setBlock(pos, slabState, 3);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                            pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 12, 0.25, 0.25, 0.25, 0.02);
                    level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.HOSTILE, 0.8f, 1.3f);
                    totalGrowth += 1;
                    continue;
                }
                // no slab form exists for this block - fall through and eat it whole instead
            }

            // Full removal - rip it loose and spawn a carried visual chunk.
            level.removeBlock(pos, false);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 16, 0.3, 0.3, 0.3, 0.03);

            ItemStack stack = new ItemStack(state.getBlock());
            if (!stack.isEmpty()) {
                ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
                item.setNoGravity(true);
                item.setPickUpDelay(32767);
                item.setInvulnerable(true);
                item.setDeltaMovement(Vec3.ZERO);
                level.addFreshEntity(item);
                carried.add(new CarriedChunk(item, Vec3.atCenterOf(pos)));
            }
            totalGrowth += alreadySlab ? 1 : 2;
        }

        if (totalGrowth > 0) {
            level.playSound(null, targetBlock, SoundEvents.GENERIC_EAT, SoundSource.HOSTILE, 1.0f, 0.5f);
        }
        monster.addGrowth(totalGrowth);
    }

    private void finishSwallow(ServerLevel level) {
        for (CarriedChunk chunk : carried) {
            if (chunk.entity.isAlive()) {
                level.sendParticles(ParticleTypes.POOF, chunk.entity.getX(), chunk.entity.getY(), chunk.entity.getZ(),
                        6, 0.15, 0.15, 0.15, 0.02);
                chunk.entity.discard();
            }
        }
        carried.clear();
    }

    private Vec3 mouthPosition() {
        Vec3 look = monster.getLookAngle();
        double heightFrac = 0.55 + monster.getGrowthStage() * 0.02;
        return monster.position()
                .add(0, monster.getBoundingBox().getYsize() * heightFrac, 0)
                .add(look.scale(0.6 * monster.getStageScale()));
    }

    @Override
    public void stop() {
        Level level = monster.level();
        if (level instanceof ServerLevel serverLevel) {
            finishSwallow(serverLevel);
        }
        this.targetBlock = null;
        this.cluster = null;
        monster.setReaching(false);
        monster.setCurlProgress(0.0f);
        monster.getNavigation().stop();
    }

    private int clusterSizeForStage(int stage) {
        int base = switch (stage) {
            case 0, 1 -> 1;
            case 2 -> 2;
            case 3 -> 4;
            default -> 7;
        };
        // Even after max stage, a long-lived colossus keeps ripping bigger chunks out at once.
        int bonusBites = monster.getBonusGrowth() / 10;
        return Math.min(30, base + bonusBites);
    }

    private static boolean isSlab(BlockState state) {
        return state.is(BlockTags.SLABS) && (!state.hasProperty(SlabBlock.TYPE) || state.getValue(SlabBlock.TYPE) != SlabType.DOUBLE);
    }

    /** Is this block something the monster is even capable of eating at all? */
    private boolean isEdible(BlockState state, BlockPos pos) {
        if (state.isAir()) return false;
        if (state.is(ModTags.INEDIBLE)) return false;
        if (state.getDestroySpeed(monster.level(), pos) < 0) return false; // unbreakable (bedrock, barrier, etc.)
        if (!state.getFluidState().isEmpty()) return false;
        return true;
    }

    /** Breadth-first collect up to `size` connected edible blocks starting at the seed, for cluster-eating. */
    private List<BlockPos> collectCluster(BlockPos seed, int size) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(seed);
        visited.add(seed);
        Level level = monster.level();

        while (!queue.isEmpty() && result.size() < size) {
            BlockPos pos = queue.poll();
            BlockState state = level.getBlockState(pos);
            if (isEdible(state, pos)) {
                result.add(pos);
                for (BlockPos neighbor : new BlockPos[]{
                        pos.above(), pos.below(), pos.north(), pos.south(), pos.east(), pos.west()}) {
                    if (!visited.contains(neighbor) && neighbor.distSqr(seed) <= 9) {
                        visited.add(neighbor);
                        queue.add(neighbor);
                    }
                }
            }
        }
        return result;
    }

    private BlockPos findNearbyEdibleBlock() {
        // Detection radius: how far it can "notice" food and decide to walk toward it.
        int radius = 6 + monster.getGrowthStage() * 3;
        BlockPos origin = monster.blockPosition();
        Level level = monster.level();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -3; dy <= radius / 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (((dx + dy + dz) & 3) != 0) continue; // sparse scan for performance
                    BlockPos pos = origin.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (isEdible(state, pos)) {
                        double dist = origin.distSqr(pos);
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = pos.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private static double lerp(double t, double a, double b) {
        return a + (b - a) * t;
    }

    private record CarriedChunk(ItemEntity entity, Vec3 origin) {}
}
