package com.hosseinasgari.aiplayer.entity;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

public class AiPlayerEntity extends PathAwareEntity {
    private static final double FOLLOW_DISTANCE = 4.0;
    private static final double TELEPORT_DISTANCE = 48.0;
    private static final double COMBAT_RANGE = 12.0;
    private static final double COMBAT_ATTACK_RANGE = 2.8;
    private static final double ROBOT_SPEED = 1.15;

    private UUID ownerUuid;
    private RobotMode mode = RobotMode.IDLE;
    private String taskDescription = "idle";
    private BlockPos homePos;
    private BlockPos gatherTarget;
    private int buildIndex;
    private int gatheredWood;
    private long nextAttackTime;

    public AiPlayerEntity(EntityType<? extends AiPlayerEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAiPlayerAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.15);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 12.0f));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            return;
        }

        long time = this.getWorld().getTime();
        if (time % 4L == 0L) {
            runCombatBrain();
        }
        if (time % 10L == 0L) {
            runTaskBrain();
        }
    }

    private void runCombatBrain() {
        if (mode != RobotMode.FOLLOW
                && mode != RobotMode.GUARD
                && mode != RobotMode.PROTECT
                && mode != RobotMode.GATHER_WOOD
                && mode != RobotMode.BUILD_HOUSE) {
            return;
        }

        HostileEntity target = findNearestHostile(COMBAT_RANGE);
        if (target == null) {
            return;
        }

        if (this.squaredDistanceTo(target) <= COMBAT_ATTACK_RANGE * COMBAT_ATTACK_RANGE) {
            this.getNavigation().stop();
            this.lookAtEntity(target, 20.0f, 20.0f);

            if (this.getWorld().getTime() >= nextAttackTime) {
                this.tryAttack(target);
                nextAttackTime = this.getWorld().getTime() + 12L;
            }
            return;
        }

        this.getNavigation().startMovingTo(target, 1.25);
    }

    private void runTaskBrain() {
        switch (mode) {
            case IDLE -> this.getNavigation().stop();
            case FOLLOW -> followOwner();
            case WANDER -> wander();
            case GUARD -> guardHome();
            case PROTECT -> protectOwner();
            case GATHER_WOOD -> gatherWood();
            case BUILD_HOUSE -> buildHouse();
        }
    }

    private void followOwner() {
        ServerPlayerEntity owner = getOwner();
        if (owner == null) {
            this.getNavigation().stop();
            return;
        }

        double distance = this.squaredDistanceTo(owner);
        if (distance > TELEPORT_DISTANCE * TELEPORT_DISTANCE && this.getWorld() == owner.getWorld()) {
            this.requestTeleport(owner.getX(), owner.getY(), owner.getZ());
            return;
        }

        if (distance > FOLLOW_DISTANCE * FOLLOW_DISTANCE) {
            this.getNavigation().startMovingTo(owner, ROBOT_SPEED);
        } else {
            this.getNavigation().stop();
        }
    }

    private void protectOwner() {
        ServerPlayerEntity owner = getOwner();
        if (owner == null) {
            this.getNavigation().stop();
            return;
        }

        HostileEntity nearbyThreat = findNearestHostileAround(owner, COMBAT_RANGE);
        if (nearbyThreat != null) {
            if (this.squaredDistanceTo(nearbyThreat) > COMBAT_ATTACK_RANGE * COMBAT_ATTACK_RANGE) {
                this.getNavigation().startMovingTo(nearbyThreat, 1.3);
            }
            return;
        }

        double distance = this.squaredDistanceTo(owner);
        if (distance > 6.0 * 6.0) {
            this.getNavigation().startMovingTo(owner, ROBOT_SPEED);
        } else {
            this.getNavigation().stop();
        }
    }

    private void guardHome() {
        if (homePos == null) {
            homePos = this.getBlockPos();
        }

        double distance = this.squaredDistanceTo(
                homePos.getX() + 0.5,
                homePos.getY(),
                homePos.getZ() + 0.5
        );

        if (distance > 8.0 * 8.0) {
            this.getNavigation().startMovingTo(
                    homePos.getX() + 0.5,
                    homePos.getY(),
                    homePos.getZ() + 0.5,
                    ROBOT_SPEED
            );
        } else {
            this.getNavigation().stop();
        }
    }

    private void wander() {
        if (!this.getNavigation().isFollowingPath()) {
            BlockPos origin = homePos != null ? homePos : this.getBlockPos();
            double x = origin.getX() + (this.random.nextInt(17) - 8);
            double z = origin.getZ() + (this.random.nextInt(17) - 8);
            this.getNavigation().startMovingTo(
                    x + 0.5,
                    origin.getY(),
                    z + 0.5,
                    0.8
            );
        }
    }

    private void gatherWood() {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (gatherTarget == null || !isLog(gatherTarget)) {
            gatherTarget = findNearestLog();
        }

        if (gatherTarget == null) {
            taskDescription = "no logs found";
            this.getNavigation().stop();
            return;
        }

        double distance = this.squaredDistanceTo(
                gatherTarget.getX() + 0.5,
                gatherTarget.getY() + 0.5,
                gatherTarget.getZ() + 0.5
        );

        if (distance > 3.0 * 3.0) {
            this.getNavigation().startMovingTo(
                    gatherTarget.getX() + 0.5,
                    gatherTarget.getY(),
                    gatherTarget.getZ() + 0.5,
                    1.0
            );
            return;
        }

        if (serverWorld.breakBlock(gatherTarget, true, this, 512)) {
            gatheredWood++;
            taskDescription = "gathering wood: " + gatheredWood + "/12";
        }

        gatherTarget = null;

        if (gatheredWood >= 12) {
            taskDescription = "gathered 12 logs";
            mode = RobotMode.IDLE;
            this.getNavigation().stop();
        }
    }

    private void buildHouse() {
        if (homePos == null) {
            homePos = this.getBlockPos().down();
            buildIndex = 0;
        }

        BlockPos target = buildPositionAtIndex(homePos, buildIndex);
        if (target == null) {
            mode = RobotMode.GUARD;
            taskDescription = "house complete";
            this.getNavigation().stop();
            return;
        }

        double distance = this.squaredDistanceTo(
                target.getX() + 0.5,
                target.getY() + 0.5,
                target.getZ() + 0.5
        );

        if (distance > 18.0) {
            this.getNavigation().startMovingTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    1.0
            );
            return;
        }

        BlockState current = this.getWorld().getBlockState(target);
        if (current.isReplaceable()) {
            this.getWorld().setBlockState(
                    target,
                    Blocks.OAK_PLANKS.getDefaultState(),
                    Block.NOTIFY_ALL
            );
        }

        buildIndex++;
        taskDescription = "building house: " + Math.min(buildIndex, 170) + "/170";
    }

    private BlockPos buildPositionAtIndex(BlockPos origin, int index) {
        final int size = 7;
        final int half = size / 2;
        final int floorCount = size * size;
        final int perimeter = size * 4 - 4;
        final int wallCount = perimeter * 3;

        if (index < floorCount) {
            int x = index % size - half;
            int z = index / size - half;
            return origin.add(x, 0, z);
        }

        int wallIndex = index - floorCount;
        if (wallIndex < wallCount) {
            int layer = wallIndex / perimeter + 1;
            int edgeIndex = wallIndex % perimeter;

            int x;
            int z;

            if (edgeIndex < size) {
                x = edgeIndex - half;
                z = -half;
            } else if (edgeIndex < size * 2 - 1) {
                x = half;
                z = edgeIndex - size + 1 - half;
            } else if (edgeIndex < size * 3 - 2) {
                x = half - (edgeIndex - (size * 2 - 1)) - 1;
                z = half;
            } else {
                x = -half;
                z = half - (edgeIndex - (size * 3 - 2)) - 1;
            }

            if (z == -half && x == 0 && (layer == 1 || layer == 2)) {
                return origin.add(0, layer, -half + 1);
            }

            return origin.add(x, layer, z);
        }

        int roofIndex = wallIndex - wallCount;
        if (roofIndex < floorCount) {
            int x = roofIndex % size - half;
            int z = roofIndex / size - half;
            return origin.add(x, 4, z);
        }

        return null;
    }

    private boolean isLog(BlockPos pos) {
        return this.getWorld().getBlockState(pos).isIn(BlockTags.LOGS);
    }

    private BlockPos findNearestLog() {
        BlockPos center = this.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -10; x <= 10; x++) {
            for (int y = -4; y <= 8; y++) {
                for (int z = -10; z <= 10; z++) {
                    BlockPos candidate = center.add(x, y, z);
                    if (!isLog(candidate)) {
                        continue;
                    }

                    double distance = this.squaredDistanceTo(
                            candidate.getX() + 0.5,
                            candidate.getY() + 0.5,
                            candidate.getZ() + 0.5
                    );

                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = candidate.toImmutable();
                    }
                }
            }
        }

        return best;
    }

    private HostileEntity findNearestHostile(double radius) {
        return findNearestHostileAround(this, radius);
    }

    private HostileEntity findNearestHostileAround(net.minecraft.entity.Entity centerEntity, double radius) {
        return this.getWorld().getEntitiesByClass(
                        HostileEntity.class,
                        centerEntity.getBoundingBox().expand(radius),
                        entity -> entity.isAlive() && !entity.isRemoved()
                ).stream()
                .min((a, b) -> Double.compare(
                        centerEntity.squaredDistanceTo(a),
                        centerEntity.squaredDistanceTo(b)
                ))
                .orElse(null);
    }

    private ServerPlayerEntity getOwner() {
        if (ownerUuid == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }
        net.minecraft.entity.player.PlayerEntity player = serverWorld.getPlayerByUuid(ownerUuid);
        return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }

    public void setOwner(ServerPlayerEntity owner) {
        this.ownerUuid = owner.getUuid();
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public RobotMode getMode() {
        return mode;
    }

    public String getTaskDescription() {
        return taskDescription;
    }

    public void applyPlan(RobotCommandPlanner.Plan plan) {
        this.mode = plan.mode();
        this.taskDescription = plan.description();

        if (this.mode == RobotMode.GUARD || this.mode == RobotMode.BUILD_HOUSE) {
            this.homePos = this.getBlockPos();
        }

        if (this.mode == RobotMode.GATHER_WOOD) {
            this.gatherTarget = null;
            this.gatheredWood = 0;
        }

        if (this.mode == RobotMode.BUILD_HOUSE) {
            this.buildIndex = 0;
        }

        if (this.mode == RobotMode.IDLE) {
            this.getNavigation().stop();
        }
    }

    @Override
    public boolean canImmediatelyDespawn(double distanceSquared) {
        return false;
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);

        if (ownerUuid != null) {
            nbt.putUuid("Owner", ownerUuid);
        }

        nbt.putString("Mode", mode.name());
        nbt.putString("Task", taskDescription);

        if (homePos != null) {
            nbt.putInt("HomeX", homePos.getX());
            nbt.putInt("HomeY", homePos.getY());
            nbt.putInt("HomeZ", homePos.getZ());
        }
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);

        if (nbt.containsUuid("Owner")) {
            ownerUuid = nbt.getUuid("Owner");
        }

        try {
            mode = RobotMode.valueOf(nbt.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            mode = RobotMode.IDLE;
        }

        taskDescription = nbt.getString("Task");

        if (nbt.contains("HomeX")) {
            homePos = new BlockPos(
                    nbt.getInt("HomeX"),
                    nbt.getInt("HomeY"),
                    nbt.getInt("HomeZ")
            );
        }
    }
}
