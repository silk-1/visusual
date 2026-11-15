package silkvis.verify;

import silkvis.automation.AutomationEngine;
import silkvis.automation.AutomationMode;
import silkvis.automation.AutomationSettings;
import silkvis.automation.BlockPos;
import silkvis.automation.Budget;
import silkvis.automation.Goal;
import silkvis.automation.GoalRegistry;
import silkvis.automation.GoalState;
import silkvis.automation.Movement;
import silkvis.automation.PathCost;
import silkvis.automation.Snapshot;
import silkvis.automation.Step;
import silkvis.automation.StepKind;
import silkvis.automation.StopReason;
import silkvis.automation.goals.CollectGoal;
import silkvis.automation.goals.FarmGoal;
import silkvis.automation.goals.GotoGoal;
import silkvis.automation.goals.MineGoal;
import silkvis.automation.goals.SafetyGoal;
import silkvis.automation.goals.TreeFarmGoal;

import java.util.ArrayList;
import java.util.List;

/**
 * Checks for the automation core: cost ordering, the pathfinder against hand-built worlds, the
 * goals' step sequences and the engine's safety gates.
 *
 * <p>No Minecraft class is touched, which is the point: the whole engine is a pure function of a
 * {@link Snapshot}, so every rule below is asserted here and does not need a client launch to
 * have any confidence at all.</p>
 */
final class AutomationChecks {
    private AutomationChecks() {
    }

    static void run() {
        costOrdering();
        pathAroundWall();
        pathThroughDoor();
        pathNeverEntersDanger();
        breakOnlyWhenAllowed();
        budgetStopsSearch();
        gotoGoalWalks();
        mineGoalRespectsReach();
        mineGoalReportsMissingTool();
        treeGoalFindsWholeTrunk();
        collectGoalSkipsLavaItems();
        safetyGoalLeavesThePit();
        farmGoalHarvestsOnlyRipe();
        engineRefusesLowMode();
        engineStopsOnLowHealth();
        engineBacksOffWhenPlayerDrives();
        engineStopsOnWorldChange();
        aimFindsTheLookedAtBlock();
        aimRespectsTheLookDirection();
        aimIgnoresAir();
        selectorPrefersDangerOverLoot();
        selectorIgnoresFarLootAndFrames();
        selectorDoesNothingWhenOff();
        registryModeGate();
        catalogMatchesRegistry();
        engineUsesPlannedStepsOnly();
        settingsRoundTrip();
    }

    // ------------------------------------------------------------------ cost model

    private static void costOrdering() {
        SelfCheck.check("automation: breaking is always more expensive than walking", () -> {
            double cheapestWalk = PathCost.WALK;
            SelfCheck.assertTrue(PathCost.BREAK > cheapestWalk * 4,
                    "break must dominate: " + PathCost.BREAK);
            SelfCheck.assertTrue(PathCost.BREAK > PathCost.STEP_UP
                    && PathCost.BREAK > PathCost.FALL_3
                    && PathCost.BREAK > PathCost.SWIM
                    && PathCost.BREAK > PathCost.DOOR, "break is the worst option");
            SelfCheck.assertTrue(PathCost.STEP_UP > PathCost.WALK, "jumping costs more");
            SelfCheck.assertTrue(PathCost.PLACE > PathCost.WALK, "placing costs more");
            SelfCheck.assertEquals(Double.POSITIVE_INFINITY,
                    PathCost.of(-9, false, false, false, false, false), "nine blocks is impossible");
        });
    }

    // ------------------------------------------------------------------ pathfinder

    private static void pathAroundWall() {
        SelfCheck.check("automation: wall without break is walked around, not through", () -> {
            FakeWorld world = new FakeWorld().withFloor(4);
            // A wall of stone at x = 2 spanning z = -2..2 inside a 4-block floor, two blocks
            // tall: high enough that climbing over is not a legal step, short enough to walk
            // around, which is exactly what "go there without digging" must do.
            for (int z = -2; z <= 2; z++) {
                world.withBlock(2, 64, z, "minecraft:stone");
                world.withBlock(2, 65, z, "minecraft:stone");
            }
            BlockPos goal = new BlockPos(4, 64, 0);
            Movement.Path path = Movement.find(world, new BlockPos(0, 64, 0), goal, false, 4096,
                    new Budget(4096));
            SelfCheck.assertTrue(path != null, "a route exists around the wall");
            SelfCheck.assertTrue(path.steps().stream().noneMatch(step -> step.kind() == StepKind.MINE),
                    "no digging when break is off");
            // Every step must be a walkable cell.
            for (Step step : path.steps()) {
                SelfCheck.assertTrue(world.passable(step.x(), step.y(), step.z()),
                        "step is passable: " + step.describe());
            }
            // Longer than the straight line of four blocks: that is what going around means.
            SelfCheck.assertTrue(path.steps().size() > 4,
                    "went around, not through: " + path.steps().size());
        });
    }

    private static void pathThroughDoor() {
        SelfCheck.check("automation: a closed door is a legal step", () -> {
            FakeWorld world = new FakeWorld().withFloor(3);
            for (int z = -3; z <= 3; z++) {
                if (z != 0) {
                    world.withBlock(1, 64, z, "minecraft:stone");
                }
            }
            world.withDoor(1, 64, 0);
            Movement.Path path = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(2, 64, 0), false, 2048, new Budget(4096));
            SelfCheck.assertTrue(path != null, "route through the door exists");
            SelfCheck.assertTrue(path.steps().stream()
                    .anyMatch(step -> step.kind() == StepKind.GOTO
                            && step.x() == 1 && step.z() == 0),
                    "the door cell is part of the route: " + path.steps());
        });
    }

    private static void pathNeverEntersDanger() {
        SelfCheck.check("automation: lava is never entered even if it is the shortest way", () -> {
            FakeWorld world = new FakeWorld().withFloor(4);
            world.withDangerous(2, 64, 0);
            world.withDangerous(1, 64, 0);
            Movement.Path path = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(4, 64, 0), true, 4096, new Budget(8192));
            SelfCheck.assertTrue(path != null, "a detour exists");
            for (Step step : path.steps()) {
                SelfCheck.assertTrue(!world.dangerous(step.x(), step.y(), step.z()),
                        "plan avoids danger: " + step.describe());
            }
        });
    }

    private static void breakOnlyWhenAllowed() {
        SelfCheck.check("automation: allowed break becomes a MINE step, not a walk", () -> {
            FakeWorld world = new FakeWorld().withFloor(3);
            // Enclose the start so the only way out is through a block. Two blocks tall: a
            // single layer is a step up, which is movement, not digging.
            for (int y = 64; y <= 65; y++) {
                world.withBlock(1, y, 0, "minecraft:dirt")
                        .withBlock(-1, y, 0, "minecraft:dirt")
                        .withBlock(0, y, 1, "minecraft:dirt")
                        .withBlock(0, y, -1, "minecraft:dirt");
                world.withBlock(0, y, 0, "minecraft:air");
            }
            Movement.Path noBreak = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(3, 64, 0), false, 1024, new Budget(2048));
            SelfCheck.assertTrue(noBreak == null, "sealed in without break");
            Movement.Path withBreak = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(3, 64, 0), true, 4096, new Budget(8192));
            SelfCheck.assertTrue(withBreak != null, "breaking opens a route");
            SelfCheck.assertTrue(withBreak.steps().stream()
                    .anyMatch(step -> step.kind() == StepKind.MINE), "MINE is in the plan");
        });
    }

    private static void budgetStopsSearch() {
        SelfCheck.check("automation: a spent budget ends the search instead of freezing", () -> {
            FakeWorld world = new FakeWorld().withFloor(16);
            // A budget of 4 operations cannot search a 32-block field.
            Movement.Path path = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(15, 64, 15), false, 100_000, new Budget(4));
            SelfCheck.assertTrue(path == null, "search stopped for lack of budget");
            // The same search with room to breathe finishes and stays inside the node cap.
            Movement.Path full = Movement.find(world, new BlockPos(0, 64, 0),
                    new BlockPos(15, 64, 15), false, 100_000, new Budget(100_000));
            SelfCheck.assertTrue(full != null, "full budget finds the route");
            SelfCheck.assertTrue(full.steps().size() >= 15,
                    "walks about the diagonal, not a detour: " + full.steps().size());
        });
    }

    // ------------------------------------------------------------------ goals

    private static void gotoGoalWalks() {
        SelfCheck.check("automation: goto goal walks and finishes on arrival", () -> {
            FakeWorld world = new FakeWorld().withFloor(6);
            GotoGoal goal = new GotoGoal(new BlockPos(3, 64, 0), false, 1.5);
            goal.reset(world);
            List<Step> steps = goal.plan(world, new Budget(4096));
            SelfCheck.assertTrue(!steps.isEmpty(), "produced a plan");
            SelfCheck.assertTrue(steps.stream().allMatch(step -> step.kind() == StepKind.GOTO),
                    "only walking steps");
            // Walk the player through the plan and check the goal finishes.
            for (Step step : steps) {
                world.at(step.x(), step.y(), step.z());
                goal.plan(world, new Budget(4096));
            }
            world.at(3, 64, 0);
            goal.plan(world, new Budget(4096));
            SelfCheck.assertTrue(goal.done(), "arrived and finished");
            SelfCheck.assertEquals(StopReason.DONE, goal.stopReason(), "stop reason is DONE");
        });
    }

    private static void mineGoalRespectsReach() {
        SelfCheck.check("automation: mine goal walks first, then mines in reach", () -> {
            FakeWorld world = new FakeWorld().withFloor(12);
            BlockPos ore = new BlockPos(8, 64, 0);
            world.withBlock(ore.x(), ore.y(), ore.z(), "minecraft:iron_ore").withBreakTicks(8, 64, 0, 5);
            MineGoal goal = new MineGoal(List.of(ore), true);
            goal.reset(world);
            List<Step> first = goal.plan(world, new Budget(4096));
            SelfCheck.assertTrue(!first.isEmpty(), "has work");
            SelfCheck.assertEquals(StepKind.GOTO, first.get(0).kind(), "walks first (too far)");
            world.at(7, 64, 0);
            List<Step> second = goal.plan(world, new Budget(4096));
            SelfCheck.assertEquals(StepKind.MINE, second.get(0).kind(), "mines once in reach");
            // The block is gone: the goal must notice and finish instead of mining air forever.
            world.withBlock(ore.x(), ore.y(), ore.z(), "minecraft:air");
            goal.plan(world, new Budget(4096));
            SelfCheck.assertTrue(goal.done(), "finished when the ore disappeared");
        });
    }

    private static void mineGoalReportsMissingTool() {
        SelfCheck.check("automation: unbreakable block stops with NO_TOOL", () -> {
            FakeWorld world = new FakeWorld().withFloor(2);
            world.withBlock(1, 64, 0, "minecraft:bedrock").withBreakTicks(1, 64, 0, Integer.MAX_VALUE);
            MineGoal goal = new MineGoal(List.of(new BlockPos(1, 64, 0)), true);
            goal.reset(world);
            goal.plan(world, new Budget(4096));
            SelfCheck.assertTrue(goal.done(), "stopped");
            SelfCheck.assertEquals(StopReason.NO_TOOL, goal.stopReason(), "reason is NO_TOOL");
        });
    }

    private static void treeGoalFindsWholeTrunk() {
        SelfCheck.check("automation: tree goal floods the trunk and ignores leaves", () -> {
            FakeWorld world = new FakeWorld().withFloor(4);
            for (int y = 65; y <= 69; y++) {
                world.withBlock(1, y, 0, "minecraft:oak_log");
            }
            world.withBlock(1, 70, 0, "minecraft:oak_leaves")
                    .withBlock(2, 69, 0, "minecraft:oak_leaves");
            TreeFarmGoal goal = new TreeFarmGoal().at(new BlockPos(1, 65, 0));
            goal.reset(world);
            // Stand next to the trunk so the first log is already in reach.
            world.at(1, 64, 0);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(4096), steps);
            SelfCheck.assertEquals(1, steps.size(), "one dig per tick, not the whole tree");
            Step first = steps.get(0);
            SelfCheck.assertEquals(StepKind.MINE, first.kind(), "digs a log");
            SelfCheck.assertEquals(69, first.y(), "top log first: " + first.describe());
            // The trunk is the only log that has been mined so far out of the five found.
            SelfCheck.assertEquals("1/5", goal.progress(), "counted exactly the five logs");
            SelfCheck.assertEquals("minecraft:oak_leaves", world.blockId(1, 70, 0),
                    "leaves are left alone");
            world.withBlock(1, 69, 0, "minecraft:air");
            List<Step> second = new ArrayList<>();
            goal.planSteps(world, new Budget(4096), second);
            SelfCheck.assertEquals(StepKind.MINE, second.get(0).kind(), "still logs");
            SelfCheck.assertEquals(68, second.get(0).y(), "next one down");
        });
    }

    private static void collectGoalSkipsLavaItems() {
        SelfCheck.check("automation: pickup refuses an item lying in lava", () -> {
            FakeWorld world = new FakeWorld().withFloor(6);
            world.withDangerous(3, 64, 0);
            world.withItem(7, 3.5, 64.0, 0.5);
            CollectGoal goal = new CollectGoal(16.0, true);
            goal.reset(world);
            goal.plan(world, new Budget(2048));
            SelfCheck.assertTrue(goal.done(), "stopped instead of walking into lava");
            SelfCheck.assertEquals(StopReason.DANGER, goal.stopReason(), "reason is DANGER");
        });
    }

    private static void safetyGoalLeavesThePit() {
        SelfCheck.check("automation: emergency exit walks out of the danger zone", () -> {
            FakeWorld world = new FakeWorld().withFloor(10);
            world.at(0, 64, 0).withDangerous(0, 64, 0);
            SafetyGoal goal = new SafetyGoal(new BlockPos(0, 64, 0), 8.0);
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(8192), steps);
            SelfCheck.assertTrue(!steps.isEmpty(), "asked to move");
            Step step = steps.get(0);
            long dx = (long) step.x() - 0;
            long dz = (long) step.z() - 0;
            SelfCheck.assertTrue(dx * dx + dz * dz >= 64, "target is outside the danger radius: "
                    + step.describe());
        });
    }

    private static void farmGoalHarvestsOnlyRipe() {
        SelfCheck.check("automation: farm harvests ripe crops only and replants", () -> {
            FakeWorld world = new FakeWorld().withFloor(4);
            BlockPos ripe = new BlockPos(1, 64, 0);
            BlockPos green = new BlockPos(1, 64, 1);
            world.withBlock(ripe.x(), ripe.y(), ripe.z(), "minecraft:wheat").withRipe(1, 64, 0);
            world.withBlock(green.x(), green.y(), green.z(), "minecraft:wheat");
            FarmGoal goal = new FarmGoal(List.of(ripe, green), 8.0);
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(2048), steps);
            SelfCheck.assertEquals(2, steps.size(), "harvest then replant");
            SelfCheck.assertEquals(StepKind.MINE, steps.get(0).kind(), "harvest the ripe one");
            SelfCheck.assertEquals(1, steps.get(0).x(), "the ripe crop, not the green one");
            SelfCheck.assertEquals(StepKind.USE_ITEM, steps.get(1).kind(), "replant right away");
        });
    }

    // ------------------------------------------------------------------ engine

    private static void engineRefusesLowMode() {
        SelfCheck.check("automation: a mining goal is refused in MOVE mode", () -> {
            AutomationEngine engine = new AutomationEngine(new GoalRegistry(),
                    new silkvis.verify.FakeClock());
            engine.applySettings(new AutomationSettings(AutomationMode.MOVE, 48, 1024, 6f, 6,
                    0, 3, false, false, false, true));
            boolean started = engine.start("A01", new BlockPos(1, 64, 0));
            SelfCheck.assertTrue(!started, "refused");
            SelfCheck.assertEquals(StopReason.MODE_TOO_LOW, engine.status().reason(),
                    "reason names the mode");
        });
    }

    private static void engineStopsOnLowHealth() {
        SelfCheck.check("automation: engine stops the goal on low health", () -> {
            AutomationEngine engine = new AutomationEngine(new GoalRegistry(),
                    new silkvis.verify.FakeClock());
            engine.applySettings(new AutomationSettings(AutomationMode.MOVE, 48, 1024, 6f, 6,
                    0, 3, false, false, false, true));
            FakeWorld world = new FakeWorld().withFloor(4).withHealth(4f);
            SelfCheck.assertTrue(engine.start("A19", new BlockPos(2, 64, 0)), "started");
            engine.tick(world, new RecordingExecutor(), false);
            SelfCheck.assertEquals(StopReason.LOW_HEALTH, engine.status().reason(), "stopped");
            SelfCheck.assertTrue(!engine.active(), "no goal running");
        });
    }

    private static void engineBacksOffWhenPlayerDrives() {
        SelfCheck.check("automation: pressing movement keys hands control back", () -> {
            AutomationEngine engine = new AutomationEngine(new GoalRegistry(),
                    new silkvis.verify.FakeClock());
            engine.applySettings(new AutomationSettings(AutomationMode.MOVE, 48, 1024, 6f, 6,
                    0, 3, false, false, false, true));
            FakeWorld world = new FakeWorld().withFloor(4);
            engine.start("A19", new BlockPos(3, 64, 0));
            engine.tick(world, new RecordingExecutor(), true);
            SelfCheck.assertEquals(StopReason.BY_PLAYER, engine.status().reason(), "yielded");
        });
    }

    private static void engineStopsOnWorldChange() {
        SelfCheck.check("automation: dimension change clears the goal", () -> {
            AutomationEngine engine = new AutomationEngine(new GoalRegistry(),
                    new silkvis.verify.FakeClock());
            engine.applySettings(new AutomationSettings(AutomationMode.MOVE, 48, 1024, 6f, 6,
                    0, 3, false, false, false, true));
            FakeWorld overworld = new FakeWorld().withFloor(4);
            engine.start("A19", new BlockPos(3, 64, 0));
            engine.tick(overworld, new RecordingExecutor(), false);
            SelfCheck.assertTrue(engine.active(), "still running in the overworld");
            FakeWorld nether = new FakeWorld().withFloor(4).inDimension("minecraft:the_nether");
            engine.tick(nether, new RecordingExecutor(), false);
            SelfCheck.assertEquals(StopReason.WORLD_CHANGED, engine.status().reason(),
                    "stopped for the world change");
            SelfCheck.assertTrue(!engine.active(), "no goal after the change");
        });
    }

    // ------------------------------------------------------------------ selector

    private static void selectorPrefersDangerOverLoot() {
        SelfCheck.check("automation: danger outranks loot, loot outranks walking", () -> {
            FakeWorld calm = new FakeWorld().withFloor(6);
            SelfCheck.assertEquals(StopReason.NONE,
                    silkvis.automation.Selector.urgent(calm, AutomationMode.MOVE).reason(),
                    "nothing urgent on an empty floor");
            SelfCheck.assertTrue(!silkvis.automation.Selector.urgent(
                    calm, AutomationMode.MOVE).found(), "no goal picked");

            FakeWorld loot = new FakeWorld().withFloor(6).withItem(7, 2.5, 64.0, 0.5);
            SelfCheck.assertEquals("A21",
                    silkvis.automation.Selector.urgent(loot, AutomationMode.MOVE).goalId(),
                    "loot at the feet is worth a walk");

            // A hostile mob within the threat radius beats the item lying next to it.
            FakeWorld mob = new FakeWorld().withFloor(6).withItem(7, 2.5, 64.0, 0.5)
                    .withEntity(9, "minecraft:zombie", 3.5, 64.0, 0.5, true);
            SelfCheck.assertEquals("A22",
                    silkvis.automation.Selector.urgent(mob, AutomationMode.MOVE).goalId(),
                    "the mob wins");

            // Standing in lava beats both, even with a mob and an item nearby.
            FakeWorld lava = new FakeWorld().withFloor(6).withItem(7, 1.5, 64.0, 0.5)
                    .withEntity(9, "minecraft:zombie", 2.5, 64.0, 0.5, true);
            lava.withDangerous(0, 64, 0);
            SelfCheck.assertEquals("A22",
                    silkvis.automation.Selector.urgent(lava, AutomationMode.MOVE).goalId(),
                    "lava is first");
        });
    }

    private static void selectorIgnoresFarLootAndFrames() {
        SelfCheck.check("automation: only real, near loot counts", () -> {
            FakeWorld far = new FakeWorld().withFloor(20).withItem(7, 2.5, 64.0, 18.5);
            SelfCheck.assertTrue(!silkvis.automation.Selector.urgent(
                    far, AutomationMode.MOVE).found(), "loot 18 blocks away is not urgent");
            // minecraft:item_frame is an entity whose id ends in "item" but is not loot; a prefix
            // or suffix test would have picked it up.
            FakeWorld frame = new FakeWorld().withFloor(6)
                    .withEntity(11, "minecraft:item_frame", 1.5, 64.0, 0.5, false);
            SelfCheck.assertTrue(!silkvis.automation.Selector.urgent(
                    frame, AutomationMode.MOVE).found(), "an item frame is not a dropped item");
            FakeWorld near = new FakeWorld().withFloor(6).withItem(7, 0.5, 64.0, 1.5);
            SelfCheck.assertTrue(silkvis.automation.Selector.urgent(
                    near, AutomationMode.MOVE).found(), "an item at the feet is");
        });
    }

    private static void selectorDoesNothingWhenOff() {
        SelfCheck.check("automation: the selector is silent in the OFF mode", () -> {
            FakeWorld world = new FakeWorld().withFloor(6).withItem(7, 1.5, 64.0, 0.5);
            silkvis.automation.Selector.Pick pick = silkvis.automation.Selector.urgent(
                    world, AutomationMode.OFF);
            SelfCheck.assertTrue(!pick.found(), "nothing started");
            SelfCheck.assertEquals(StopReason.MODE_TOO_LOW, pick.reason(), "and it says why");
            SelfCheck.assertEquals(StopReason.WORLD_CHANGED,
                    silkvis.automation.Selector.urgent(null, AutomationMode.MOVE).reason(),
                    "no world is its own reason");
        });
    }

    // ------------------------------------------------------------------ aiming

    private static void aimFindsTheLookedAtBlock() {
        SelfCheck.check("automation: the aim ray finds the block under the crosshair", () -> {
            FakeWorld world = new FakeWorld().withFloor(6);
            // The eye of a player standing at y=64 is at 65.62, so a level ray travels through
            // the y=65 layer: the block has to be there to be looked at.
            world.withBlock(4, 65, 0, "minecraft:stone");
            // Yaw -90 looks east (+X) in Minecraft; pitch 0 is level.
            java.util.Optional<BlockPos> hit = silkvis.automation.Aim.firstSolid(
                    0.5, 64.0 + world.eyeHeight(), 0.5, -90f, 0f, 8.0,
                    pos -> world.solid(pos.x(), pos.y(), pos.z()));
            SelfCheck.assertTrue(hit.isPresent(), "hit something");
            SelfCheck.assertEquals(new BlockPos(4, 65, 0), hit.get(), "the block straight ahead");
        });
    }

    private static void aimRespectsTheLookDirection() {
        SelfCheck.check("automation: the aim ray follows yaw, not the nearest block", () -> {
            FakeWorld world = new FakeWorld().withFloor(6);
            world.withBlock(4, 65, 0, "minecraft:stone");
            world.withBlock(0, 65, 4, "minecraft:stone");
            // Yaw 0 looks south (+Z): the block at +Z must win over the one at +X, which is the
            // same distance away and would be found by a "nearest block" rule.
            java.util.Optional<BlockPos> hit = silkvis.automation.Aim.firstSolid(
                    0.5, 64.0 + world.eyeHeight(), 0.5, 0f, 0f, 8.0,
                    pos -> world.solid(pos.x(), pos.y(), pos.z()));
            SelfCheck.assertTrue(hit.isPresent(), "hit something");
            SelfCheck.assertEquals(new BlockPos(0, 65, 4), hit.get(), "looked south, found south");
            // Straight down is a legal aim too (pitch -90) and must find the floor.
            java.util.Optional<BlockPos> down = silkvis.automation.Aim.firstSolid(
                    0.5, 64.0 + world.eyeHeight(), 0.5, 0f, 90f, 4.0,
                    pos -> world.solid(pos.x(), pos.y(), pos.z()));
            SelfCheck.assertTrue(down.isPresent(), "hit the floor");
            SelfCheck.assertEquals(63, down.get().y(), "the floor is the block below the feet");
        });
    }

    private static void aimIgnoresAir() {
        SelfCheck.check("automation: an aim into open air finds nothing", () -> {
            FakeWorld world = new FakeWorld().withFloor(2);
            java.util.Optional<BlockPos> hit = silkvis.automation.Aim.firstSolid(
                    0.5, 70.0, 0.5, -90f, 0f, 6.0,
                    pos -> world.solid(pos.x(), pos.y(), pos.z()));
            SelfCheck.assertTrue(hit.isEmpty(), "nothing to look at");
        });
    }

    private static void catalogMatchesRegistry() {
        SelfCheck.check("automation: catalogue and registry agree on what is implemented", () -> {
            GoalRegistry registry = new GoalRegistry();
            for (silkvis.automation.AutomationCatalog.Entry entry
                    : silkvis.automation.AutomationCatalog.entries()) {
                boolean ready = entry.status() == silkvis.automation.AutomationCatalog.Status.READY;
                SelfCheck.assertEquals(ready, registry.ids().contains(entry.id()),
                        entry.id() + " status matches the registry");
                SelfCheck.assertEquals(ready, silkvis.automation.AutomationCatalog.canStart(entry.id()),
                        entry.id() + " startability follows the status");
            }
            // The default build must not offer automation that does nothing: the whole point of
            // the status field is that a PLANNED entry has no start button.
            SelfCheck.assertTrue(!silkvis.automation.AutomationCatalog.entries().isEmpty(),
                    "catalogue is not empty");
        });
    }

    private static void engineUsesPlannedStepsOnly() {
        SelfCheck.check("automation: engine honours the HINT mode without pressing anything", () -> {
            AutomationEngine engine = new AutomationEngine(new GoalRegistry(),
                    new silkvis.verify.FakeClock());
            engine.applySettings(new AutomationSettings(AutomationMode.HINT, 48, 1024, 6f, 6,
                    0, 3, false, false, false, true));
            FakeWorld world = new FakeWorld().withFloor(6);
            SelfCheck.assertTrue(engine.start("A19", new BlockPos(3, 64, 0)), "started");
            RecordingExecutor executor = new RecordingExecutor();
            engine.tick(world, executor, false);
            SelfCheck.assertTrue(executor.steps.isEmpty(), "nothing executed in HINT mode");
            SelfCheck.assertTrue(!engine.status().step().isEmpty(), "but the plan is visible");
            SelfCheck.assertTrue(engine.active(), "and the goal is still running");
        });
    }

    private static void registryModeGate() {
        SelfCheck.check("automation: registry creates goals only above their mode", () -> {
            GoalRegistry registry = new GoalRegistry();
            SelfCheck.assertTrue(registry.create("A01", new BlockPos(0, 64, 0),
                    AutomationMode.MOVE).isEmpty(), "mining needs FULL");
            SelfCheck.assertTrue(registry.create("A01", new BlockPos(0, 64, 0),
                    AutomationMode.FULL).isPresent(), "mining allowed in FULL");
            SelfCheck.assertTrue(registry.create("A19", new BlockPos(0, 64, 0),
                    AutomationMode.MOVE).isPresent(), "walking allowed in MOVE");
            SelfCheck.assertTrue(registry.create("A99", new BlockPos(0, 64, 0),
                    AutomationMode.FULL).isEmpty(), "unknown id refused");
            SelfCheck.assertTrue(registry.ids().contains("A22"), "emergency exit is offered");
            Goal goal = registry.create("A21", GoalRegistry.NO_TARGET,
                    AutomationMode.MOVE).orElseThrow();
            SelfCheck.assertEquals("A21", goal.id(), "id round-trips");
        });
    }

    /** Records every step the engine emits and reports success immediately. */
    private static final class RecordingExecutor implements AutomationEngine.Executor {
        private final List<Step> steps = new ArrayList<>();
        private int aborts;
        private boolean running;

        @Override
        public Result execute(Step step, AutomationMode mode) {
            this.steps.add(step);
            return Result.DONE;
        }

        @Override
        public void abort() {
            this.aborts++;
        }
    }

    private static void settingsRoundTrip() {
        SelfCheck.check("automation: settings encode/decode keeps values and refuses bad ones",
                () -> {
                    AutomationSettings custom = new AutomationSettings(AutomationMode.FULL, 64,
                            2048, 8f, 10, 5.5, 2.0, true, true, false, false);
                    AutomationSettings decoded = AutomationSettings.decode(custom.encode());
                    SelfCheck.assertEquals(custom, decoded, "round trip");
                    AutomationSettings defaults = AutomationSettings.defaults();
                    SelfCheck.assertEquals(AutomationMode.OFF, defaults.mode(), "off by default");
                    SelfCheck.assertTrue(!defaults.enabled(), "nothing runs by default");
                    // A broken mode string is a config error, not a silent default.
                    SelfCheck.expectThrows(silkvis.config.ConfigException.class, () -> {
                        silkvis.config.json.JsonObject o = silkvis.config.json.JsonValue.newObject();
                        o.put("mode", "TURBO");
                        AutomationSettings.decode(o);
                    }, "unknown mode rejected");
                    // Out-of-range values are refused, not clamped.
                    SelfCheck.expectThrows(silkvis.config.ConfigException.class, () -> {
                        new AutomationSettings(AutomationMode.FULL, 4, 2048, 8f, 10, 0, 2,
                                false, false, false, true).validate();
                    }, "reach radius below the minimum rejected");
                });
    }
}
