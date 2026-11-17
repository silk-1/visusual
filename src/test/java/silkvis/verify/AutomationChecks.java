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
import silkvis.automation.ToolSelect;
import silkvis.automation.baritone.BaritoneMinePlan;
import silkvis.automation.baritone.BaritoneOre;
import silkvis.automation.baritone.BaritoneStatus;
import silkvis.automation.baritone.BaritoneTask;
import silkvis.automation.goals.CollectGoal;
import silkvis.automation.goals.EatGoal;
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
        eatGoalWaitsWhenFed();
        eatGoalEatsWhenHungry();
        eatGoalRefusesDebuffFood();
        eatGoalStopsWithNothingToEat();
        toolSelectPicksTheRightFamily();
        toolSelectPrefersTheBetterTier();
        miningHoldsTheToolInsteadOfGivingUp();
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
        baritoneTaskListIsStable();
        baritoneStatusNeverLies();
        baritoneLangKeysExist();
        baritoneCommandsMatchTheChatCommands();
        baritoneMinePageBuildsTheChatCommand();
    }

    // ------------------------------------------------------------------ baritone (integration)

    /**
     * The task list is the tab's menu and its ids are config and log keys, so two things must
     * hold: ids never repeat and never get renamed, and every task declares up front what the
     * caller has to supply. A task that reports {@code Need.NONE} but needs a point would fail
     * only once the player pressed it.
     */
    private static void baritoneTaskListIsStable() {
        SelfCheck.check("automation: baritone task list is stable and self-describing", () -> {
            java.util.Set<String> slugs = new java.util.HashSet<>();
            for (BaritoneTask task : BaritoneTask.values()) {
                SelfCheck.assertTrue(slugs.add(task.slug()),
                        "slug must be unique: " + task.slug());
                SelfCheck.assertTrue(task.titleKey().startsWith("silkvis.baritone."),
                        "title key namespaced: " + task.titleKey());
                SelfCheck.assertEquals(task, BaritoneTask.bySlug(task.slug()),
                        "slug round-trips: " + task.slug());
                SelfCheck.assertTrue(task.need() != null, "need is declared: " + task.slug());
            }
            SelfCheck.assertTrue(BaritoneTask.bySlug("nonsense") == null, "unknown slug is null");
            SelfCheck.assertTrue(BaritoneTask.bySlug(null) == null, "null slug is null");
            // Tasks that cannot work without an argument must say so, or the screen offers a
            // button that fails silently.
            SelfCheck.assertEquals(BaritoneTask.Need.POINT, BaritoneTask.GOTO.need(),
                    "goto needs a point");
            SelfCheck.assertEquals(BaritoneTask.Need.BLOCK_AND_COUNT, BaritoneTask.MINE.need(),
                    "mine needs a block and a count");
            SelfCheck.assertEquals(BaritoneTask.Need.TEXT, BaritoneTask.FOLLOW.need(),
                    "follow needs a name");
            SelfCheck.assertEquals(BaritoneTask.Need.NONE, BaritoneTask.STOP.need(),
                    "stop needs nothing");
        });
    }

    /**
     * The HUD and the tab show {@link BaritoneStatus#describe()} verbatim, so it must always
     * say something true: no nulls, no empty strings, and an unusable bridge must never look
     * like an idle one.
     */
    private static void baritoneStatusNeverLies() {
        SelfCheck.check("automation: baritone status always says something true", () -> {
            BaritoneStatus missing = BaritoneStatus.missing();
            SelfCheck.assertTrue(!missing.usable(), "missing is not usable");
            SelfCheck.assertEquals(BaritoneStatus.Availability.MISSING, missing.availability(),
                    "missing names itself");
            SelfCheck.assertTrue(!missing.describe().isEmpty(), "missing describes itself");
            SelfCheck.assertTrue(missing.describe().contains("not installed"),
                    "missing says why: " + missing.describe());

            BaritoneStatus broken = BaritoneStatus.broken("NoSuchMethodException: isPathing");
            SelfCheck.assertTrue(!broken.usable(), "broken is not usable");
            // The short line is shown in a one-line widget that clips, so the reason must NOT
            // live there -- it lives in detail()/hint() and is shown in a wrapping block.
            SelfCheck.assertTrue(broken.describe().length() <= 40,
                    "describe stays short enough to fit: " + broken.describe());
            SelfCheck.assertTrue(broken.hint().contains("isPathing"),
                    "hint keeps the reason: " + broken.hint());

            BaritoneStatus idle = BaritoneStatus.ready(false, "");
            SelfCheck.assertTrue(idle.usable(), "ready is usable");
            SelfCheck.assertTrue(idle.describe().contains("idle"), "idle says idle");

            BaritoneStatus working = BaritoneStatus.ready(true, "Mine");
            SelfCheck.assertTrue(working.describe().contains("Mine"),
                    "working names the process: " + working.describe());
            // An unusable bridge must be distinguishable from an idle one in the status line,
            // otherwise the player reads "idle" and presses buttons that cannot work.
            SelfCheck.assertTrue(!missing.describe().equals(idle.describe()),
                    "missing and idle are not the same line");
        });
    }

    /**
     * Every key the Baritone tab asks for must exist in both languages. The tab is built from
     * {@link BaritoneTask#titleKey()} at run time, so a missing key shows the raw key to the
     * player instead of a name -- and nothing else would catch it, because the screen is not
     * exercised by the checks.
     */
    private static void baritoneLangKeysExist() {
        SelfCheck.check("automation: baritone tab has RU and EN strings", () -> {
            java.nio.file.Path en = java.nio.file.Path.of(
                    "src/main/resources/assets/silkvis/lang/en_us.json");
            java.nio.file.Path ru = java.nio.file.Path.of(
                    "src/main/resources/assets/silkvis/lang/ru_ru.json");
            // The checks run from the project root under Gradle; when the files are not there
            // the check says so instead of passing silently on nothing.
            SelfCheck.assertTrue(java.nio.file.Files.isRegularFile(en), "en_us.json exists");
            SelfCheck.assertTrue(java.nio.file.Files.isRegularFile(ru), "ru_ru.json exists");

            String enText = java.nio.file.Files.readString(en);
            String ruText = java.nio.file.Files.readString(ru);

            java.util.List<String> keys = new java.util.ArrayList<>();
            for (BaritoneTask task : BaritoneTask.values()) {
                keys.add(task.titleKey());
            }
            keys.add("silkvis.tab.baritone");
            keys.add("silkvis.baritone.state");
            keys.add("silkvis.baritone.tasks");
            keys.add("silkvis.baritone.missing_help");
            keys.add("silkvis.baritone.broken_help");
            keys.add("silkvis.baritone.reason");
            keys.add("silkvis.baritone.target_note");
            keys.add("silkvis.baritone.cancel");
            // The task menu, the two sub-pages and the status line of the ore page.
            keys.add("silkvis.baritone.menu.mine");
            keys.add("silkvis.baritone.menu.goto");
            keys.add("silkvis.baritone.menu.follow");
            keys.add("silkvis.baritone.menu.explore");
            keys.add("silkvis.baritone.menu.stop");
            keys.add("silkvis.baritone.menu.note");
            keys.add("silkvis.baritone.probe");
            keys.add("silkvis.baritone.last");
            keys.add("silkvis.baritone.last.none");
            keys.add("silkvis.baritone.mine.back");
            keys.add("silkvis.baritone.mine.help");
            keys.add("silkvis.baritone.mine.limit");
            keys.add("silkvis.baritone.mine.no_limit");
            keys.add("silkvis.baritone.mine.start");
            keys.add("silkvis.baritone.mine.none");
            keys.add("silkvis.baritone.mine.command");
            keys.add("silkvis.baritone.follow.back");
            keys.add("silkvis.baritone.follow.help");
            keys.add("silkvis.baritone.follow.hint");
            keys.add("silkvis.baritone.follow.start");
            keys.add("silkvis.baritone.follow.empty");
            // Every ore button names itself from its own key, so a missing row would show the
            // player a raw key instead of a name -- and nothing else would catch it.
            for (BaritoneOre ore : BaritoneOre.values()) {
                keys.add(ore.langKey());
            }

            for (String key : keys) {
                SelfCheck.assertTrue(enText.contains("\"" + key + "\""),
                        "EN has " + key);
                SelfCheck.assertTrue(ruText.contains("\"" + key + "\""),
                        "RU has " + key);
            }
        });
    }

    /**
     * Every task must produce a command Baritone actually knows.
     *
     * <p>This is the check that the first version lacked, and its absence cost a build and a
     * test session: the typed API was used with {@code follow(String)}, which does not exist
     * (the parameter is a {@code Predicate<Entity>}), so the bridge refused to start at all.
     * The command names below are the labels read from Baritone's own default commands --
     * goto, mine, thisway, explore, follow, cancel -- and a task that invents a name fails
     * here instead of at the player's first click.</p>
     */
    private static void baritoneCommandsMatchTheChatCommands() {
        SelfCheck.check("automation: every baritone task maps to a real chat command", () -> {
            java.util.Set<String> known = java.util.Set.of(
                    "goto", "mine", "thisway", "explore", "follow", "cancel");

            for (BaritoneTask task : BaritoneTask.values()) {
                // Fill in every argument so the command is built for real, not skipped.
                String command = task.command(10, 70, -20, "Steve", "minecraft:iron_ore", 5);
                SelfCheck.assertTrue(command != null && !command.isEmpty(),
                        "task produces a command: " + task.slug());
                String label = command.split(" ")[0];
                SelfCheck.assertTrue(known.contains(label),
                        task.slug() + " uses a known command, got '" + label + "'");
            }

            // Missing arguments must yield null, not a half-built command: the screen relies
            // on that to keep the button disabled instead of sending "mine 0 null".
            SelfCheck.assertTrue(BaritoneTask.MINE.command(0, 0, 0, null, "", 0) == null,
                    "mine without a block yields nothing");
            SelfCheck.assertTrue(BaritoneTask.FOLLOW.command(0, 0, 0, "", null, 0) == null,
                    "follow without a name yields nothing");

            // A target task must carry all three coordinates, and a point-free task must not
            // smuggle them in where Baritone would read them as arguments.
            SelfCheck.assertEquals("goto 10 70 -20",
                    BaritoneTask.GOTO.command(10, 70, -20, null, null, 0), "goto carries the point");
            SelfCheck.assertEquals("thisway",
                    BaritoneTask.COLLECT.command(10, 70, -20, null, null, 5), "thisway is bare");
            SelfCheck.assertEquals("cancel",
                    BaritoneTask.STOP.command(0, 0, 0, null, null, 0), "stop cancels");

            // Only tasks that need nothing from the player may have an enabled button.
            SelfCheck.assertTrue(BaritoneTask.STOP.readyToRun(), "stop is always ready");
            SelfCheck.assertTrue(BaritoneTask.EXPLORE.readyToRun(), "explore needs nothing");
            SelfCheck.assertTrue(!BaritoneTask.MINE.readyToRun(), "mine needs a block");
            SelfCheck.assertTrue(!BaritoneTask.FOLLOW.readyToRun(), "follow needs a name");
        });
    }

    /**
     * The ore page must build the line its own help promises: {@code mine diamond_ore
     * coal_ore} with no limit and {@code mine 64 ...} with one.
     *
     * <p>This is the check the earlier tab lacked in effect: the bridge was wired, but nothing
     * put the selected ores into an argument list, so "Mine" could only be a disabled button. The
     * expected strings below are the two usage lines from Baritone's own MineCommand help.</p>
     */
    private static void baritoneMinePageBuildsTheChatCommand() {
        SelfCheck.check("automation: the ore page builds the mine command", () -> {
            BaritoneMinePlan plan = new BaritoneMinePlan();
            SelfCheck.assertTrue(plan.command() == null, "an empty pick sends nothing");
            SelfCheck.assertTrue(!plan.selected(BaritoneOre.DIAMOND),
                    "nothing is picked before the player picks it");

            plan.toggle(BaritoneOre.DIAMOND);
            SelfCheck.assertTrue(plan.selected(BaritoneOre.DIAMOND), "toggle picks an ore");
            SelfCheck.assertEquals("mine diamond_ore deepslate_diamond_ore", plan.command(),
                    "no limit means no count argument");

            plan.toggle(BaritoneOre.COAL);
            SelfCheck.assertTrue(
                    plan.command().startsWith("mine diamond_ore deepslate_diamond_ore coal_ore"),
                    "picks are joined in menu order: " + plan.command());

            plan.setLimit(100);
            SelfCheck.assertEquals(96, plan.limit(), "the limit snaps to the button step");
            SelfCheck.assertTrue(plan.command().startsWith("mine 96 "),
                    "a limit goes before the blocks: " + plan.command());

            plan.bumpLimit(-100);
            SelfCheck.assertEquals(0, plan.limit(), "minus stops at zero");
            plan.bumpLimit(100);
            SelfCheck.assertEquals(BaritoneMinePlan.MAX_LIMIT, plan.limit(),
                    "plus stops at the ceiling");

            plan.toggle(BaritoneOre.DIAMOND);
            plan.toggle(BaritoneOre.COAL);
            SelfCheck.assertTrue(plan.command() == null,
                    "unpicking everything disables Start again");
        });
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

    // ------------------------------------------------------------------ eat (A09)

    /**
     * The goal is a standing order, not a job: fed means "do nothing this tick", never
     * "finished". A goal that returned done here would end the automation the first time the
     * player was full.
     */
    private static void eatGoalWaitsWhenFed() {
        SelfCheck.check("automation: auto eat does nothing while fed", () -> {
            FakeWorld world = new FakeWorld().withFloor(4).withFood(20)
                    .withHotbar(0, "minecraft:bread");
            EatGoal goal = new EatGoal();
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(256), steps);
            SelfCheck.assertTrue(steps.isEmpty(), "no steps while fed");
            SelfCheck.assertTrue(!goal.done(), "still standing by, not finished");
            SelfCheck.assertEquals(StopReason.NONE, goal.stopReason(), "no stop");
        });
    }

    private static void eatGoalEatsWhenHungry() {
        SelfCheck.check("automation: auto eat selects the food and uses it once", () -> {
            FakeWorld world = new FakeWorld().withFloor(4).withFood(6)
                    .withHotbar(0, "minecraft:stone")
                    .withHotbar(3, "minecraft:cooked_beef")
                    .holdingSlot(0);
            EatGoal goal = new EatGoal();
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(256), steps);
            SelfCheck.assertEquals(3, steps.size(), "select, use, wait");
            SelfCheck.assertEquals(StepKind.SELECT_SLOT, steps.get(0).kind(), "select first");
            SelfCheck.assertEquals(3, steps.get(0).x(), "the slot with the beef");
            SelfCheck.assertEquals(StepKind.USE_ITEM, steps.get(1).kind(), "then eat");
            SelfCheck.assertEquals(StepKind.WAIT, steps.get(2).kind(), "then let it finish");
            SelfCheck.assertTrue(steps.get(2).ticks() >= 32,
                    "vanilla eating takes 32 ticks: " + steps.get(2).ticks());
        });
    }

    /**
     * The slot already in hand is used without a swap: selecting the same slot again is a no-op
     * in game but shows up as an extra step, and worse, the earlier draft sent the use to the
     * previously held slot when the selection happened on the same tick.
     */
    private static void eatGoalRefusesDebuffFood() {
        SelfCheck.check("automation: auto eat never touches rotten flesh or pufferfish", () -> {
            FakeWorld world = new FakeWorld().withFloor(4).withFood(2)
                    .withHotbar(0, "minecraft:rotten_flesh")
                    .withHotbar(1, "minecraft:pufferfish")
                    .withHotbar(2, "minecraft:poisonous_potato");
            EatGoal goal = new EatGoal();
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(256), steps);
            SelfCheck.assertTrue(steps.isEmpty(), "nothing eaten");
            // Hungry with only bad food is the same answer as hungry with none: stop and say
            // why, instead of eating a debuff to hide the problem.
            SelfCheck.assertEquals(StopReason.NO_TOOL, goal.stopReason(), "reports it");
            SelfCheck.assertTrue(!EatGoal.edible("minecraft:rotten_flesh"), "flesh refused");
            SelfCheck.assertTrue(!EatGoal.edible("minecraft:pufferfish"), "pufferfish refused");
            SelfCheck.assertTrue(!EatGoal.edible("minecraft:cobblestone"), "stone is not food");
            SelfCheck.assertTrue(!EatGoal.edible(null), "null is not food");
            SelfCheck.assertTrue(EatGoal.edible("minecraft:golden_apple"), "golden apple is food");
            SelfCheck.assertTrue(!EatGoal.edible("minecraft:POISONOUS_POTATO"),
                    "a mod may hand over an upper-case id");
            // The refusal table and the allow table must never contradict each other. The
            // first version of this goal listed pufferfish in both and the answer depended on
            // which check ran first -- a mutation proved the test could not see it, because
            // deleting the refusal branch changed nothing while the lists were disjoint.
            SelfCheck.assertTrue(!EatGoal.listsOverlap(),
                    "allow and refuse lists must stay disjoint");
        });
    }

    private static void eatGoalStopsWithNothingToEat() {
        SelfCheck.check("automation: auto eat stops with NO_TOOL when the hotbar is empty", () -> {
            FakeWorld world = new FakeWorld().withFloor(4).withFood(3);
            EatGoal goal = new EatGoal();
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(256), steps);
            SelfCheck.assertTrue(steps.isEmpty(), "no steps");
            SelfCheck.assertEquals(StopReason.NO_TOOL, goal.stopReason(), "names the reason");
            SelfCheck.assertTrue(goal.done(), "gives up instead of spinning");
        });
    }

    // ------------------------------------------------------------------ tool select (A10)

    private static void toolSelectPicksTheRightFamily() {
        SelfCheck.check("automation: tool select answers in the block's own family", () -> {
            // Stone wants a pickaxe even when a better-tier axe is in the next slot: tier only
            // orders items inside one family, never across families.
            FakeWorld world = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:netherite_axe")
                    .withHotbar(1, "minecraft:iron_pickaxe")
                    .withHotbar(2, "minecraft:stone_shovel");
            SelfCheck.assertEquals(1,
                    ToolSelect.bestSlotFor(world, "minecraft:stone"), "pickaxe for stone");
            SelfCheck.assertEquals(0,
                    ToolSelect.bestSlotFor(world, "minecraft:oak_log"), "axe for a log");
            SelfCheck.assertEquals(2,
                    ToolSelect.bestSlotFor(world, "minecraft:dirt"), "shovel for dirt");
            // A hotbar with no tool of the wanted family answers -1, and the caller keeps its
            // own behaviour (MineGoal reads breakTicks and decides for itself).
            FakeWorld bare = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:bread")
                    .withHotbar(1, "minecraft:sword");
            SelfCheck.assertEquals(-1,
                    ToolSelect.bestSlotFor(bare, "minecraft:stone"), "no pickaxe, no slot");
            SelfCheck.assertTrue(ToolSelect.stepsToHoldTool(bare, "minecraft:stone").isEmpty(),
                    "nothing to select when there is no tool");
        });
    }

    private static void toolSelectPrefersTheBetterTier() {
        SelfCheck.check("automation: tool select prefers the better tier and skips the swap", () -> {
            FakeWorld world = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:wooden_pickaxe")
                    .withHotbar(4, "minecraft:diamond_pickaxe")
                    .withHotbar(5, "minecraft:stone_axe");
            SelfCheck.assertEquals(4,
                    ToolSelect.bestSlotFor(world, "minecraft:iron_ore"), "diamond wins");

            // Already holding it: no SELECT_SLOT step, or the run shows a pointless flicker.
            FakeWorld holding = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:wooden_pickaxe")
                    .withHotbar(4, "minecraft:diamond_pickaxe")
                    .holdingSlot(4);
            SelfCheck.assertTrue(
                    ToolSelect.stepsToHoldTool(holding, "minecraft:iron_ore").isEmpty(),
                    "nothing to do when the best tool is already held");

            // Holding something worse: exactly one step, to the best slot.
            FakeWorld worse = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:wooden_pickaxe")
                    .withHotbar(4, "minecraft:diamond_pickaxe")
                    .holdingSlot(0);
            List<Step> steps = ToolSelect.stepsToHoldTool(worse, "minecraft:iron_ore");
            SelfCheck.assertEquals(1, steps.size(), "one step");
            SelfCheck.assertEquals(StepKind.SELECT_SLOT, steps.get(0).kind(), "a selection");
            SelfCheck.assertEquals(4, steps.get(0).x(), "the diamond slot");

            // Golden tools are fast but fragile; they rank with wood, not above diamond.
            FakeWorld gold = new FakeWorld().withFloor(4)
                    .withHotbar(0, "minecraft:golden_pickaxe")
                    .withHotbar(1, "minecraft:diamond_pickaxe");
            SelfCheck.assertEquals(1,
                    ToolSelect.bestSlotFor(gold, "minecraft:stone"), "diamond beats gold");
        });
    }

    /**
     * The bug A10 exists for: the right tool was in the hotbar but not in hand, so the block's
     * break time looked infinite and the goal reported NO_TOOL. The plan must now select it.
     */
    private static void miningHoldsTheToolInsteadOfGivingUp() {
        SelfCheck.check("automation: mining selects the tool it needs instead of NO_TOOL", () -> {
            FakeWorld world = new FakeWorld().withFloor(4)
                    .withBlock(1, 64, 0, "minecraft:iron_ore")
                    .withBreakTicks(1, 64, 0, 15)
                    .withHotbar(0, "minecraft:bread")
                    .withHotbar(2, "minecraft:iron_pickaxe")
                    .holdingSlot(0);
            MineGoal goal = new MineGoal(List.of(new BlockPos(1, 64, 0)), true);
            goal.reset(world);
            List<Step> steps = new ArrayList<>();
            goal.planSteps(world, new Budget(1024), steps);
            SelfCheck.assertEquals(2, steps.size(), "select, then mine");
            SelfCheck.assertEquals(StepKind.SELECT_SLOT, steps.get(0).kind(), "tool first");
            SelfCheck.assertEquals(2, steps.get(0).x(), "the pickaxe slot");
            SelfCheck.assertEquals(StepKind.MINE, steps.get(1).kind(), "then the block");
            SelfCheck.assertTrue(!goal.done(), "still working, not stopped for a missing tool");
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
