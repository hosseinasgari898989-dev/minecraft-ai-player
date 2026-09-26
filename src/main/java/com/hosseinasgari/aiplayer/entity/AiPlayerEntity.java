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
    private static final double COMBAT_RANGE = 14.0;
    private static final double COMBAT_ATTACK_RANGE = 2.9;
    private static final double ROBOT_SPEED = 1.15;

    private enum ResourceTask {
        WOOD,
        STONE,
        COAL
    }

    private UUID ownerUuid;
    private RobotMode mode = RobotMode.IDLE;
    private String taskDescription = "idle";
    private BlockPos homePos;
    private BlockPos gatherTarget;
    private BlockPos exploreTarget;
    private int patrolStep;
    private int buildIndex;
    private int gatheredCount;
    private ResourceTask resourceTask = ResourceTask.WOOD;
    private long nextAttackTime;
    private long lastProgressTick;
    private double lastProgressX;
    private double lastProgressY;
    private double lastProgressZ;
    private int commandsExecuted;
    private int blocksBroken;
    private int blocksPlaced;
    private int attacksMade;

    public AiPlayerEntity(EntityType<? extends AiPlayerEntity> entityType, World world) {
        super(entityType, world);
    }

    public static DefaultAttributeContainer.Builder createAiPlayerAttributes() {
        return PathAwareEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.25)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 3.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 0.25);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new LookAtEntityGoal(this, PlayerEntity.class, 14.0f));
        this.goalSelector.add(2, new LookAroundGoal(this));
    }

    @Override
    public void tick() {
        super.tick();

        if (this.getWorld().isClient()) {
            return;
        }

        long time = this.getWorld().getTime();

        if (homePos == null) {
            homePos = this.getBlockPos();
        }

        if (time % 4L == 0L) {
            runCombatBrain();
        }

        if (time % 10L == 0L) {
            runTaskBrain();
        }

        if (time % 40L == 0L) {
            checkStuck();
        }
    }

    private void runCombatBrain() {
        if (mode == RobotMode.IDLE) {
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
                if (this.tryAttack(target)) {
                    attacksMade++;
                }
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
            case EXPLORE -> explore();
            case GUARD -> guardHome();
            case PROTECT -> protectOwner();
            case PATROL -> patrol();
            case RETURN_HOME -> returnHome();
            case GATHER_WOOD -> gather(ResourceTask.WOOD, 12);
            case GATHER_STONE -> gather(ResourceTask.STONE, 20);
            case GATHER_COAL -> gather(ResourceTask.COAL, 16);
            case BUILD_HOUSE -> buildHouse();
            case BUILD_TOWER -> buildTower();
        }
    }

    private void followOwner() {
        ServerPlayerEntity owner = getOwner();
        if (owner == null) {
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

        HostileEntity threat = findNearestHostileAround(
                this.getWorld(),
                homePos,
                18.0
        );

        if (threat != null) {
            this.getNavigation().startMovingTo(threat, 1.25);
            return;
        }

        returnToPosition(homePos, 8.0);
    }

    private void patrol() {
        if (homePos == null) {
            homePos = this.getBlockPos();
        }

        HostileEntity threat = findNearestHostileAround(
                this.getWorld(),
                homePos,
                20.0
        );

        if (threat != null) {
            this.getNavigation().startMovingTo(threat, 1.25);
            return;
        }

        BlockPos[] points = new BlockPos[] {
                homePos.add(8, 0, 8),
                homePos.add(-8, 0, 8),
                homePos.add(-8, 0, -8),
                homePos.add(8, 0, -8)
        };

        BlockPos target = points[Math.min(patrolStep, points.length - 1)];
        if (this.squaredDistanceTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) < 4.0) {
            patrolStep = (patrolStep + 1) % points.length;
            target = points[patrolStep];
        }

        this.getNavigation().startMovingTo(
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                0.9
        );
    }

    private void returnHome() {
        if (homePos == null) {
            homePos = this.getBlockPos();
        }

        if (returnToPosition(homePos, 3.0)) {
            this.getNavigation().stop();
            taskDescription = "home reached";
            mode = RobotMode.GUARD;
        }
    }

    private void wander() {
        if (homePos == null) {
            homePos = this.getBlockPos();
        }

        if (this.getNavigation().isFollowingPath()) {
            return;
        }

        int x = homePos.getX() + this.random.nextInt(25) - 12;
        int z = homePos.getZ() + this.random.nextInt(25) - 12;

        this.getNavigation().startMovingTo(
                x + 0.5,
                homePos.getY(),
                z + 0.5,
                0.85
        );
    }

    private void explore() {
        if (exploreTarget == null || this.squaredDistanceTo(exploreTarget.getX() + 0.5, exploreTarget.getY(), exploreTarget.getZ() + 0.5) < 9.0) {
            int distance = 24 + this.random.nextInt(25);
            int x = this.getBlockPos().getX() + this.random.nextInt(distance * 2 + 1) - distance;
            int z = this.getBlockPos().getZ() + this.random.nextInt(distance * 2 + 1) - distance;
            exploreTarget = new BlockPos(x, this.getBlockY(), z);
            taskDescription = "exploring: " + x + ", " + z;
        }

        this.getNavigation().startMovingTo(
                exploreTarget.getX() + 0.5,
                exploreTarget.getY(),
                exploreTarget.getZ() + 0.5,
                0.95
        );
    }

    private boolean returnToPosition(BlockPos target, double stopDistance) {
        double distance = this.squaredDistanceTo(
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5
        );

        if (distance > stopDistance * stopDistance) {
            this.getNavigation().startMovingTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    ROBOT_SPEED
            );
            return false;
        }

        return true;
    }

    private void gather(ResourceTask task, int targetCount) {
        if (!(this.getWorld() instanceof ServerWorld serverWorld)) {
            return;
        }

        if (resourceTask != task) {
            resourceTask = task;
            gatheredCount = 0;
            gatherTarget = null;
        }

        if (gatheredCount >= targetCount) {
            taskDescription = resourceTask.name().toLowerCase() + " gathered: " + gatheredCount;
            mode = RobotMode.IDLE;
            this.getNavigation().stop();
            return;
        }

        if (gatherTarget == null || !isGatherable(gatherTarget, task)) {
            gatherTarget = findNearestResource(task);
        }

        if (gatherTarget == null) {
            taskDescription = "no " + resourceTask.name().toLowerCase() + " nearby";
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
            gatheredCount++;
            blocksBroken++;
            taskDescription = resourceTask.name().toLowerCase() + ": " + gatheredCount + "/" + targetCount;
        }

        gatherTarget = null;
    }

    private boolean isGatherable(BlockPos pos, ResourceTask task) {
        BlockState state = this.getWorld().getBlockState(pos);
        return switch (task) {
            case WOOD -> state.isIn(BlockTags.LOGS);
            case STONE -> state.isOf(Blocks.STONE) || state.isOf(Blocks.COBBLESTONE) || state.isOf(Blocks.DEEPSLATE);
            case COAL -> state.isOf(Blocks.COAL_ORE) || state.isOf(Blocks.DEEPSLATE_COAL_ORE);
        };
    }

    private BlockPos findNearestResource(ResourceTask task) {
        BlockPos center = this.getBlockPos();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;

        for (int x = -12; x <= 12; x++) {
            for (int y = -6; y <= 8; y++) {
                for (int z = -12; z <= 12; z++) {
                    BlockPos candidate = center.add(x, y, z);
                    if (!isGatherable(candidate, task)) {
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

    private void buildHouse() {
        if (homePos == null) {
            homePos = this.getBlockPos().down();
        }

        BlockPos target = getHouseBlock(homePos, buildIndex);
        if (target == null) {
            taskDescription = "house complete";
            mode = RobotMode.GUARD;
            this.getNavigation().stop();
            return;
        }

        if (!placeAtTarget(target, Blocks.OAK_PLANKS.getDefaultState(), "building house")) {
            this.getNavigation().startMovingTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    1.0
            );
        } else {
            buildIndex++;
        }
    }

    private BlockPos getHouseBlock(BlockPos origin, int index) {
        final int size = 7;
        final int half = 3;
        final int floorCount = 49;
        final int perimeter = 24;
        final int wallCount = 72;

        if (index < floorCount) {
            return origin.add(index % size - half, 0, index / size - half);
        }

        int wallIndex = index - floorCount;
        if (wallIndex < wallCount) {
            int layer = wallIndex / perimeter + 1;
            int edge = wallIndex % perimeter;
            int x;
            int z;

            if (edge < size) {
                x = edge - half;
                z = -half;
            } else if (edge < size * 2 - 1) {
                x = half;
                z = edge - size + 1 - half;
            } else if (edge < size * 3 - 2) {
                x = half - (edge - (size * 2 - 1)) - 1;
                z = half;
            } else {
                x = -half;
                z = half - (edge - (size * 3 - 2)) - 1;
            }

            return origin.add(x, layer, z);
        }

        int roofIndex = index - floorCount - wallCount;
        if (roofIndex < floorCount) {
            return origin.add(roofIndex % size - half, 4, roofIndex / size - half);
        }

        return null;
    }

    private void buildTower() {
        if (homePos == null) {
            homePos = this.getBlockPos().down();
        }

        BlockPos target = getTowerBlock(homePos, buildIndex);
        if (target == null) {
            taskDescription = "tower complete";
            mode = RobotMode.GUARD;
            this.getNavigation().stop();
            return;
        }

        if (!placeAtTarget(target, Blocks.COBBLESTONE.getDefaultState(), "building tower")) {
            this.getNavigation().startMovingTo(
                    target.getX() + 0.5,
                    target.getY(),
                    target.getZ() + 0.5,
                    1.0
            );
        } else {
            buildIndex++;
        }
    }

    private BlockPos getTowerBlock(BlockPos origin, int index) {
        final int size = 3;
        final int half = 1;
        final int floorCount = 9;
        final int perimeter = 8;
        final int wallCount = perimeter * 6;

        if (index < floorCount) {
            return origin.add(index % size - half, 0, index / size - half);
        }

        int wallIndex = index - floorCount;
        if (wallIndex < wallCount) {
            int layer = wallIndex / perimeter + 1;
            int edge = wallIndex % perimeter;
            int x;
            int z;

            if (edge < size) {
                x = edge - half;
                z = -half;
            } else if (edge < size * 2 - 1) {
                x = half;
                z = edge - size + 1 - half;
            } else if (edge < size * 3 - 2) {
                x = half - (edge - (size * 2 - 1)) - 1;
                z = half;
            } else {
                x = -half;
                z = half - (edge - (size * 3 - 2)) - 1;
            }

            return origin.add(x, layer, z);
        }

        int roofIndex = index - floorCount - wallCount;
        if (roofIndex < floorCount) {
            return origin.add(roofIndex % size - half, 7, roofIndex / size - half);
        }

        return null;
    }

    private boolean placeAtTarget(BlockPos target, BlockState state, String description) {
        double distance = this.squaredDistanceTo(
                target.getX() + 0.5,
                target.getY() + 0.5,
                target.getZ() + 0.5
        );

        if (distance > 18.0) {
            taskDescription = description;
            return false;
        }

        if (this.getWorld().getBlockState(target).isReplaceable()) {
            this.getWorld().setBlockState(target, state, Block.NOTIFY_ALL);
            blocksPlaced++;
        }

        taskDescription = description;
        return true;
    }

    private HostileEntity findNearestHostile(double radius) {
        return this.getWorld().getEntitiesByClass(
                        HostileEntity.class,
                        this.getBoundingBox().expand(radius),
                        entity -> entity.isAlive() && !entity.isRemoved()
                ).stream()
                .min((a, b) -> Double.compare(this.squaredDistanceTo(a), this.squaredDistanceTo(b)))
                .orElse(null);
    }

    private HostileEntity findNearestHostileAround(net.minecraft.entity.Entity centerEntity, double radius) {
        return this.getWorld().getEntitiesByClass(
                        HostileEntity.class,
                        centerEntity.getBoundingBox().expand(radius),
                        entity -> entity.isAlive() && !entity.isRemoved()
                ).stream()
                .min((a, b) -> Double.compare(centerEntity.squaredDistanceTo(a), centerEntity.squaredDistanceTo(b)))
                .orElse(null);
    }

    private HostileEntity findNearestHostileAround(World world, BlockPos center, double radius) {
        return world.getEntitiesByClass(
                        HostileEntity.class,
                        new net.minecraft.util.math.Box(center).expand(radius),
                        entity -> entity.isAlive() && !entity.isRemoved()
                ).stream()
                .min((a, b) -> Double.compare(
                        a.squaredDistanceTo(center.getX(), center.getY(), center.getZ()),
                        b.squaredDistanceTo(center.getX(), center.getY(), center.getZ())
                ))
                .orElse(null);
    }

    private ServerPlayerEntity getOwner() {
        if (ownerUuid == null || !(this.getWorld() instanceof ServerWorld serverWorld)) {
            return null;
        }

        PlayerEntity player = serverWorld.getPlayerByUuid(ownerUuid);
        return player instanceof ServerPlayerEntity serverPlayer ? serverPlayer : null;
    }

    private void checkStuck() {
        if (mode == RobotMode.IDLE) {
            lastProgressTick = this.getWorld().getTime();
            lastProgressX = this.getX();
            lastProgressY = this.getY();
            lastProgressZ = this.getZ();
            return;
        }

        double moved = Math.sqrt(
                Math.pow(this.getX() - lastProgressX, 2)
                        + Math.pow(this.getY() - lastProgressY, 2)
                        + Math.pow(this.getZ() - lastProgressZ, 2)
        );

        if (moved < 0.2 && this.getWorld().getTime() - lastProgressTick > 80L) {
            this.getNavigation().stop();

            ServerPlayerEntity owner = getOwner();
            if (owner != null
                    && (mode == RobotMode.FOLLOW
                    || mode == RobotMode.PROTECT
                    || mode == RobotMode.RETURN_HOME)
                    && this.getWorld() == owner.getWorld()) {
                this.requestTeleport(owner.getX(), owner.getY(), owner.getZ());
            }

            if (mode == RobotMode.EXPLORE) {
                exploreTarget = null;
            }

            if (mode == RobotMode.WANDER) {
                wander();
            }

            lastProgressTick = this.getWorld().getTime();
        } else if (moved >= 0.2) {
            lastProgressTick = this.getWorld().getTime();
        }

        lastProgressX = this.getX();
        lastProgressY = this.getY();
        lastProgressZ = this.getZ();
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

    public BlockPos getHomePos() {
        return homePos;
    }

    public int getCommandsExecuted() {
        return commandsExecuted;
    }

    public int getBlocksBroken() {
        return blocksBroken;
    }

    public int getBlocksPlaced() {
        return blocksPlaced;
    }

    public int getAttacksMade() {
        return attacksMade;
    }

    public void setHome() {
        this.homePos = this.getBlockPos().toImmutable();
        this.taskDescription = "home set";
        this.mode = RobotMode.IDLE;
    }

    public void applyPlan(RobotCommandPlanner.Plan plan) {
        this.mode = plan.mode();
        this.taskDescription = plan.description();
        this.commandsExecuted++;

        if (this.mode == RobotMode.GUARD
                || this.mode == RobotMode.PATROL
                || this.mode == RobotMode.BUILD_HOUSE
                || this.mode == RobotMode.BUILD_TOWER) {
            if (homePos == null) {
                homePos = this.getBlockPos().toImmutable();
            }
        }

        if (this.mode == RobotMode.GATHER_WOOD) {
            resourceTask = ResourceTask.WOOD;
            gatheredCount = 0;
            gatherTarget = null;
        }

        if (this.mode == RobotMode.GATHER_STONE) {
            resourceTask = ResourceTask.STONE;
            gatheredCount = 0;
            gatherTarget = null;
        }

        if (this.mode == RobotMode.GATHER_COAL) {
            resourceTask = ResourceTask.COAL;
            gatheredCount = 0;
            gatherTarget = null;
        }

        if (this.mode == RobotMode.BUILD_HOUSE || this.mode == RobotMode.BUILD_TOWER) {
            buildIndex = 0;
        }

        if (this.mode == RobotMode.EXPLORE) {
            exploreTarget = null;
        }

        if (this.mode == RobotMode.PATROL) {
            patrolStep = 0;
        }

        if (this.mode == RobotMode.RETURN_HOME) {
            taskDescription = "returning home";
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
        nbt.putInt("Commands", commandsExecuted);
        nbt.putInt("BlocksBroken", blocksBroken);
        nbt.putInt("BlocksPlaced", blocksPlaced);
        nbt.putInt("Attacks", attacksMade);

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
        commandsExecuted = nbt.getInt("Commands");
        blocksBroken = nbt.getInt("BlocksBroken");
        blocksPlaced = nbt.getInt("BlocksPlaced");
        attacksMade = nbt.getInt("Attacks");

        if (nbt.contains("HomeX")) {
            homePos = new BlockPos(
                    nbt.getInt("HomeX"),
                    nbt.getInt("HomeY"),
                    nbt.getInt("HomeZ")
            );
        }
    }
}
