package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import com.enviouse.progressivestages.common.api.ProgressiveStagesAPI;
import com.enviouse.progressivestages.common.api.StageCause;
import com.enviouse.progressivestages.common.api.StageId;

import net.neoforged.neoforge.network.PacketDistributor;
import uk.foreverserver.pathways.net.NotifyPayload;

import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.stats.StatsCounter;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.block.Block;

/**
 * Tasks, bounties, discoveries and the death penalty.
 *
 * <p><b>Tasks</b> belong to nodes and seals. A task starts counting once its node is open (everything but the
 * task and the shards is met). At that moment its stat is recorded as a baseline, and progress is the stat minus
 * that baseline. When it finishes it pays its reward once, opens the node for buying, and is done for good.
 *
 * <p><b>Bounties</b> are timed jobs outside the tree. Each player has {@code rules.bountySlots} slots (two). At
 * every dawn each empty slot is filled with a bounty drawn at random from the player's pool: every bounty whose
 * node they own, so the pool grows with the tree. A bounty must be finished within its {@code days}; one that runs
 * out simply leaves. A slot emptied by a finished bounty stays empty until the next dawn, so at most two pay a day.
 *
 * <p><b>Dying</b> costs shards, keeps 75% of the progress on every running task and bounty, and freezes them all
 * for ten minutes (rules.freezeTicks): while frozen the baseline is dragged along with the stat, so nothing done in that time
 * counts. A bounty's deadline keeps running while frozen.
 *
 * <p>Everything is stored in ProgressiveStages counters on the player.
 */
public final class TaskBook {
    public static final String POINTS = PathwayService.POINTS;
    private static final String INIT = "fp2_init";
    private static final String PAID = "fp2_paid";
    private static final String FREEZE = "fp2_freeze";
    /** The day (plus one) whose dawn last refilled the bounty slots. */
    private static final String BOUNTY_DAY = "fpq_day";

    /** Task states in the snapshot. */
    public static final int WAITING = 0;
    public static final int RUNNING = 1;
    public static final int DONE = 4;
    /** Bounty slot states in the snapshot. */
    public static final int SLOT_EMPTY = 0;
    public static final int SLOT_RUNNING = 1;
    public static final int SLOT_DONE = 5;
    public static final int SLOT_EXPIRED = 6;

    private static final Map<String, List<Block>> BLOCKS = new HashMap<>();
    private static final Map<String, List<EntityType<?>>> ENTITIES = new HashMap<>();
    private static final Map<String, List<Item>> ITEMS = new HashMap<>();
    private static final Map<String, Predicate<String>> ID_RULES = new HashMap<>();

    private TaskBook() {}

    // ------------------------------------------------------------------ ticking

    public static void onTick(ServerPlayer player) {
        if (player.tickCount % 20 == 0 && player.isUnderWater() && player.isAlive()) {
            ProgressiveStagesAPI.addCounter(player, eventKey("underwater", ""), 1);
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        update(player);
    }

    /** Starts tasks that have opened, pays finished tasks and bounties, deals new bounties at dawn. */
    public static void update(ServerPlayer player) {
        Tree tree = Tree.get();
        if (!ensureInit(player, tree)) {
            return;
        }
        payStreak(player, tree);
        Set<String> owned = owned(player, tree);
        boolean frozen = frozenTicks(player) > 0;
        updateTasks(player, tree, owned, frozen);
        updateBounties(player, tree, owned, frozen);
        checkDiscoveries(player, tree);
    }

    private static boolean ensureInit(ServerPlayer player, Tree tree) {
        if (ProgressiveStagesAPI.getCounter(player, INIT) != 0) {
            return true;
        }
        ProgressiveStagesAPI.setCounter(player, PAID, daysAlive(player, tree));
        ProgressiveStagesAPI.setCounter(player, FREEZE, 0L);
        if (tree.rules.startingShards > 0) {
            ProgressiveStagesAPI.addCounter(player, POINTS, tree.rules.startingShards);
        }
        ProgressiveStagesAPI.setCounter(player, INIT, 1L);
        PathwayService.ensureCore(player);
        return false;
    }

    // ------------------------------------------------------------------ tasks

    private static void updateTasks(ServerPlayer player, Tree tree, Set<String> owned, boolean frozen) {
        for (Tree.Node node : tree.nodes) {
            if (!node.hasTask() || claimed(player, node)) {
                continue;
            }
            Tree.Task task = node.task;
            String key = node.key();
            if (owned.contains(node.id)) {
                // Owned without its task (granted by command, say): count it as done.
                ProgressiveStagesAPI.setCounter(player, "fpc_" + key, 1L);
                continue;
            }
            if (ProgressiveStagesAPI.getCounter(player, "fpa_" + key) == 0) {
                if (Gates.open(tree, node, owned::contains)) {
                    ProgressiveStagesAPI.setCounter(player, "fpb_" + key, stat(player, task, tree));
                    ProgressiveStagesAPI.setCounter(player, "fpf_" + key, 0L);
                    ProgressiveStagesAPI.setCounter(player, "fpa_" + key, 1L);
                }
                continue;
            }
            if (frozen) {
                if (baselined(task)) {
                    long held = ProgressiveStagesAPI.getCounter(player, "fpf_" + key);
                    ProgressiveStagesAPI.setCounter(player, "fpb_" + key, stat(player, task, tree) - held);
                }
                continue;
            }
            long progress = taskProgress(player, node, tree);
            if (progress < task.amount) {
                seen(player, NotifyPayload.TASK_PROGRESS, node.id, task.title, node.title, node.item, progress,
                        task.amount, task.reward);
                continue;
            }
            ProgressiveStagesAPI.setCounter(player, "fpc_" + key, 1L);
            if (task.reward > 0) {
                ProgressiveStagesAPI.addCounter(player, POINTS, task.reward);
            }
            finished(player, NotifyPayload.TASK_DONE, node.id, task.title, node.title, node.item, task.amount, task.reward);
        }
    }

    private static long taskProgress(ServerPlayer player, Tree.Node node, Tree tree) {
        String key = node.key();
        if (ProgressiveStagesAPI.getCounter(player, "fpa_" + key) == 0) {
            return 0;
        }
        long stat = stat(player, node.task, tree);
        if (!baselined(node.task)) {
            return stat;
        }
        return Math.max(0, stat - ProgressiveStagesAPI.getCounter(player, "fpb_" + key));
    }

    /** True once a node's task has been finished, which is what buying the node needs. */
    public static boolean claimed(ServerPlayer player, Tree.Node node) {
        return node.hasTask() && ProgressiveStagesAPI.getCounter(player, "fpc_" + node.key()) != 0;
    }

    // ------------------------------------------------------------------ notices

    /** The last progress each online player was shown, per task or bounty, so only real movement is sent. */
    private static final Map<java.util.UUID, Map<String, Long>> SEEN = new HashMap<>();

    public static void forgetSeen(net.minecraft.world.entity.player.Player player) {
        SEEN.remove(player.getUUID());
    }

    /**
     * Sends a progress notice when a task or bounty has moved forward. The first look after login or a new deal
     * only records where things stand. Small goals report every step; long ones (blocks walked, stone mined)
     * report each 5% so a long walk does not keep a popup up for its whole length.
     */
    private static void seen(ServerPlayer player, int kind, String id, String title, String subtitle, String item,
                             long progress, long goal, int reward) {
        Map<String, Long> mine = SEEN.computeIfAbsent(player.getUUID(), uuid -> new HashMap<>());
        String key = (kind == NotifyPayload.BOUNTY_PROGRESS ? "b:" : "t:") + id;
        Long before = mine.put(key, progress);
        if (before == null || progress <= before || goal <= 0) {
            return;
        }
        boolean step = goal <= 64 || progress * 20 / goal > before * 20 / goal;
        if (!step) {
            mine.put(key, before);
            return;
        }
        PacketDistributor.sendToPlayer(player, new NotifyPayload(kind, key, title, subtitle, item, progress, goal, reward));
    }

    private static void finished(ServerPlayer player, int kind, String id, String title, String subtitle, String item,
                                 long goal, int reward) {
        String key = (kind == NotifyPayload.BOUNTY_DONE ? "b:" : "t:") + id;
        Map<String, Long> mine = SEEN.get(player.getUUID());
        if (mine != null) {
            mine.remove(key);
        }
        PacketDistributor.sendToPlayer(player, new NotifyPayload(kind, key, title, subtitle, item, goal, goal, reward));
    }

    // ------------------------------------------------------------------ bounties

    /** The current in-game day, counted in dawns. Sleeping through the night moves it on, as it should. */
    public static long today(ServerPlayer player, Tree tree) {
        return Math.max(0, player.server.overworld().getDayTime()) / Math.max(1, tree.rules.dayTicks);
    }

    private static int slots(Tree tree) {
        return Math.max(0, Math.min(6, tree.rules.bountySlots));
    }

    private static Tree.Bounty slotBounty(ServerPlayer player, Tree tree, int slot) {
        int index = (int) ProgressiveStagesAPI.getCounter(player, slotKey(slot, "id")) - 1;
        Tree.Bounty byIndex = index >= 0 && index < tree.bounties.size() ? tree.bounties.get(index) : null;
        long stamp = ProgressiveStagesAPI.getCounter(player, slotKey(slot, "bid"));
        if (stamp == 0) {
            // Dealt before slots remembered what they held, or nothing in the slot at all.
            if (byIndex != null) {
                ProgressiveStagesAPI.setCounter(player, slotKey(slot, "bid"), stamp(byIndex));
            }
            return byIndex;
        }
        if (byIndex != null && stamp(byIndex) == stamp) {
            return byIndex;
        }
        // The list moved under us: find what this slot really holds, and put the position right.
        for (int i = 0; i < tree.bounties.size(); i++) {
            if (stamp(tree.bounties.get(i)) == stamp) {
                ProgressiveStagesAPI.setCounter(player, slotKey(slot, "id"), i + 1L);
                return tree.bounties.get(i);
            }
        }
        clearSlot(player, slot, SLOT_EMPTY);
        return null;
    }

    /**
     * Wipes everything Pathways knows about a player: every node revoked, every task and bounty
     * forgotten, shards back to the starting handful. The next tick sets them up as a new arrival.
     */
    public static void reset(ServerPlayer player) {
        Tree tree = Tree.get();
        for (Tree.Node node : tree.nodes) {
            String key = node.key();
            ProgressiveStagesAPI.setCounter(player, "fpa_" + key, 0L);
            ProgressiveStagesAPI.setCounter(player, "fpb_" + key, 0L);
            ProgressiveStagesAPI.setCounter(player, "fpc_" + key, 0L);
            ProgressiveStagesAPI.setCounter(player, "fpf_" + key, 0L);
            if (node == tree.core()) {
                continue;
            }
            try {
                if (ProgressiveStagesAPI.hasStage(player, node.id)) {
                    ProgressiveStagesAPI.revokeStage(player, StageId.parse(node.id), StageCause.COMMAND);
                }
            } catch (RuntimeException exception) {
                // a stage the pack no longer has; nothing to revoke
            }
        }
        for (Tree.Bounty bounty : tree.bounties) {
            ProgressiveStagesAPI.setCounter(player, "fpn_q_" + bounty.id, 0L);
        }
        for (int slot = 0; slot < slots(tree); slot++) {
            clearSlot(player, slot, SLOT_EMPTY);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "prev"), 0L);
        }
        ProgressiveStagesAPI.setCounter(player, POINTS, 0L);
        ProgressiveStagesAPI.setCounter(player, INIT, 0L);
        ProgressiveStagesAPI.setCounter(player, BOUNTY_DAY, 0L);
        ProgressiveStagesAPI.setCounter(player, PAID, 0L);
        ProgressiveStagesAPI.setCounter(player, FREEZE, 0L);
        forgetSeen(player);
        update(player);
    }

    /** A bounty's own name as a number, so a slot can say what it holds without counting list positions. */
    private static long stamp(Tree.Bounty bounty) {
        return bounty == null || bounty.id == null ? 0L : (bounty.id.hashCode() & 0x7fffffffL) + 1L;
    }

    private static void updateBounties(ServerPlayer player, Tree tree, Set<String> owned, boolean frozen) {
        long day = today(player, tree);
        for (int slot = 0; slot < slots(tree); slot++) {
            Tree.Bounty bounty = slotBounty(player, tree, slot);
            if (bounty == null) {
                continue;
            }
            Tree.Task task = bounty.task();
            if (day >= ProgressiveStagesAPI.getCounter(player, slotKey(slot, "end"))) {
                clearSlot(player, slot, SLOT_EXPIRED);
                player.sendSystemMessage(Component.literal("Bounty expired: " + bounty.title).withStyle(ChatFormatting.GRAY));
                continue;
            }
            if (frozen) {
                if (baselined(task)) {
                    long held = ProgressiveStagesAPI.getCounter(player, slotKey(slot, "held"));
                    ProgressiveStagesAPI.setCounter(player, slotKey(slot, "base"), stat(player, task, tree) - held);
                }
                continue;
            }
            long progress = bountyProgress(player, tree, slot, bounty);
            String tier = "Tier " + tree.bountyTierName(bounty.tier) + " bounty";
            if (progress < task.amount) {
                seen(player, NotifyPayload.BOUNTY_PROGRESS, bounty.id, bounty.title, tier, bounty.item, progress,
                        task.amount, bounty.reward);
                continue;
            }
            clearSlot(player, slot, SLOT_DONE);
            ProgressiveStagesAPI.addCounter(player, POINTS, bounty.reward);
            ProgressiveStagesAPI.addCounter(player, "fpn_q_" + bounty.id, 1);
            finished(player, NotifyPayload.BOUNTY_DONE, bounty.id, bounty.title, tier, bounty.item, task.amount,
                    bounty.reward);
        }

        long dealt = ProgressiveStagesAPI.getCounter(player, BOUNTY_DAY) - 1;
        if (dealt > day) {
            // The clock went backwards (/time set): treat today as already dealt.
            ProgressiveStagesAPI.setCounter(player, BOUNTY_DAY, day + 1);
            return;
        }
        if (dealt == day) {
            return;
        }
        List<String> offered = new ArrayList<>();
        for (int slot = 0; slot < slots(tree); slot++) {
            if (slotBounty(player, tree, slot) != null) {
                continue;
            }
            Tree.Bounty pick = draw(player, tree, owned, slot);
            if (pick == null) {
                continue;
            }
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "id"), tree.bounties.indexOf(pick) + 1L);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "bid"), stamp(pick));
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "prev"), tree.bounties.indexOf(pick) + 1L);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "end"), day + Math.max(1, Math.min(3, pick.days)));
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "base"), stat(player, pick.task(), tree));
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "held"), 0L);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "out"), 0L);
            offered.add(pick.title + " (+" + pick.reward + ", " + pick.days + (pick.days == 1 ? " day" : " days") + ")");
        }
        ProgressiveStagesAPI.setCounter(player, BOUNTY_DAY, day + 1);
        if (!offered.isEmpty()) {
            player.sendSystemMessage(Component.literal((offered.size() == 1 ? "New bounty: " : "New bounties: ")
                    + String.join("; ", offered)).withStyle(ChatFormatting.GOLD));
        }
    }

    /**
     * A random bounty from the pool: owned node, not already in another slot, not what this slot last held.
     * Higher tiers weigh a little more, so a well-grown tree sees more of its harder bounties.
     */
    private static Tree.Bounty draw(ServerPlayer player, Tree tree, Set<String> owned, int slot) {
        Set<Integer> busy = new HashSet<>();
        for (int other = 0; other < slots(tree); other++) {
            busy.add((int) ProgressiveStagesAPI.getCounter(player, slotKey(other, "id")) - 1);
        }
        int previous = (int) ProgressiveStagesAPI.getCounter(player, slotKey(slot, "prev")) - 1;
        List<Tree.Bounty> pool = new ArrayList<>();
        int total = 0;
        for (int i = 0; i < tree.bounties.size(); i++) {
            Tree.Bounty bounty = tree.bounties.get(i);
            if (bounty.kind == null || busy.contains(i) || i == previous) {
                continue;
            }
            String needs = bounty.requires == null ? tree.core().id : bounty.requires;
            if (!owned.contains(needs)) {
                continue;
            }
            pool.add(bounty);
            total += weight(bounty);
        }
        if (pool.isEmpty()) {
            return null;
        }
        int roll = player.getRandom().nextInt(total);
        for (Tree.Bounty bounty : pool) {
            roll -= weight(bounty);
            if (roll < 0) {
                return bounty;
            }
        }
        return pool.get(pool.size() - 1);
    }

    /** Admin command: drops the player's bounties and deals fresh ones now. */
    public static void reroll(ServerPlayer player) {
        Tree tree = Tree.get();
        for (int slot = 0; slot < slots(tree); slot++) {
            clearSlot(player, slot, SLOT_EMPTY);
        }
        ProgressiveStagesAPI.setCounter(player, BOUNTY_DAY, 0L);
        update(player);
    }

    private static int weight(Tree.Bounty bounty) {
        return 2 + Math.max(1, bounty.tier);
    }

    private static void clearSlot(ServerPlayer player, int slot, int outcome) {
        ProgressiveStagesAPI.setCounter(player, slotKey(slot, "id"), 0L);
        ProgressiveStagesAPI.setCounter(player, slotKey(slot, "bid"), 0L);
        ProgressiveStagesAPI.setCounter(player, slotKey(slot, "out"), outcome);
    }

    private static long bountyProgress(ServerPlayer player, Tree tree, int slot, Tree.Bounty bounty) {
        Tree.Task task = bounty.task();
        if ("hold".equals(task.kind)) {
            return held(player, task.target);
        }
        long stat = stat(player, task, tree);
        if (!baselined(task)) {
            return stat;
        }
        return Math.max(0, stat - ProgressiveStagesAPI.getCounter(player, slotKey(slot, "base")));
    }

    /** How many of the target items are in the player's main hand right now. */
    private static long held(ServerPlayer player, String target) {
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            return 0;
        }
        return resolve(ITEMS, BuiltInRegistries.ITEM, target == null ? "" : target).contains(stack.getItem())
                ? stack.getCount() : 0;
    }

    private static String slotKey(int slot, String part) {
        return "fpq" + slot + "_" + part;
    }

    // ------------------------------------------------------------------ counted events

    /** A tamed animal counts toward every tame task or bounty whose target names its type. */
    public static void onTame(ServerPlayer player, EntityType<?> type) {
        for (Tree.Task task : eventTasks("tame")) {
            if (resolve(ENTITIES, BuiltInRegistries.ENTITY_TYPE, task.target).contains(type)) {
                ProgressiveStagesAPI.addCounter(player, eventKey(task.kind, task.target), 1);
            }
        }
    }

    /** A potion taken out of a brewing stand counts toward every brew job whose target names its potion. */
    public static void onBrewed(ServerPlayer player, ItemStack stack) {
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null || contents.potion().isEmpty()) {
            return;
        }
        String id = contents.potion().get().unwrapKey().map(key -> key.location().toString()).orElse("");
        if (id.isEmpty()) {
            return;
        }
        for (Tree.Task task : eventTasks("brew")) {
            if (matchesId(task.target, id)) {
                ProgressiveStagesAPI.addCounter(player, eventKey(task.kind, task.target), Math.max(1, stack.getCount()));
            }
        }
    }

    private static final Map<String, List<Tree.Task>> EVENT_TASKS = new HashMap<>();

    /** Every task or bounty of one kind, one per distinct target. */
    private static synchronized List<Tree.Task> eventTasks(String kind) {
        return EVENT_TASKS.computeIfAbsent(kind, k -> {
            Tree tree = Tree.get();
            Map<String, Tree.Task> byTarget = new java.util.LinkedHashMap<>();
            for (Tree.Node node : tree.nodes) {
                if (node.hasTask() && k.equals(node.task.kind)) {
                    byTarget.putIfAbsent(node.task.target == null ? "" : node.task.target, node.task);
                }
            }
            for (Tree.Bounty bounty : tree.bounties) {
                if (k.equals(bounty.kind)) {
                    byTarget.putIfAbsent(bounty.task().target, bounty.task());
                }
            }
            return new ArrayList<>(byTarget.values());
        });
    }

    private static boolean matchesId(String target, String id) {
        for (String raw : (target == null ? "" : target).split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith("re:")) {
                if (ID_RULES.computeIfAbsent(part, LockResolver::matcher).test(id)) {
                    return true;
                }
            } else if (part.equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String eventKey(String kind, String target) {
        String t = target == null ? "" : target;
        return t.isEmpty() ? "fpx_" + kind : "fpx_" + kind + "_" + Integer.toHexString(t.hashCode());
    }

    // ------------------------------------------------------------------ streak

    private static void payStreak(ServerPlayer player, Tree tree) {
        long days = daysAlive(player, tree);
        long paid = ProgressiveStagesAPI.getCounter(player, PAID);
        if (days > paid) {
            long gained = (days - paid) * Math.max(0, tree.rules.shardsPerDay);
            ProgressiveStagesAPI.setCounter(player, PAID, days);
            if (gained > 0) {
                ProgressiveStagesAPI.addCounter(player, POINTS, (int) gained);
                player.displayClientMessage(Component.literal("Another day survived  +" + gained + " " + shards(gained))
                        .withStyle(ChatFormatting.GOLD), true);
            }
        } else if (days < paid) {
            ProgressiveStagesAPI.setCounter(player, PAID, days);
        }
    }

    public static long daysAlive(ServerPlayer player, Tree tree) {
        return aliveTicks(player) / Math.max(1, tree.rules.dayTicks);
    }

    public static int aliveTicks(ServerPlayer player) {
        return player.getStats().getValue(Stats.CUSTOM.get(Stats.TIME_SINCE_DEATH));
    }

    // ------------------------------------------------------------------ death

    public static void onDeath(ServerPlayer player) {
        Tree tree = Tree.get();
        if (!ensureInit(player, tree)) {
            return;
        }
        long points = ProgressiveStagesAPI.getCounter(player, POINTS);
        int lost = (int) Math.min(points, Math.max(0, tree.rules.deathShardLoss));
        if (lost > 0) {
            ProgressiveStagesAPI.addCounter(player, POINTS, -lost);
        }
        ProgressiveStagesAPI.setCounter(player, PAID, 0L);
        double keep = 1.0 - Math.max(0, Math.min(1, tree.rules.deathTaskLoss));
        for (Tree.Node node : tree.nodes) {
            String key = node.key();
            if (!node.hasTask() || claimed(player, node) || !baselined(node.task)
                    || ProgressiveStagesAPI.getCounter(player, "fpa_" + key) == 0) {
                continue;
            }
            long now = stat(player, node.task, tree);
            long progress = Math.max(0, now - ProgressiveStagesAPI.getCounter(player, "fpb_" + key));
            long kept = (long) Math.floor(progress * keep);
            ProgressiveStagesAPI.setCounter(player, "fpb_" + key, now - kept);
            ProgressiveStagesAPI.setCounter(player, "fpf_" + key, kept);
        }
        for (int slot = 0; slot < slots(tree); slot++) {
            Tree.Bounty bounty = slotBounty(player, tree, slot);
            if (bounty == null || !baselined(bounty.task())) {
                continue;
            }
            long now = stat(player, bounty.task(), tree);
            long progress = Math.max(0, now - ProgressiveStagesAPI.getCounter(player, slotKey(slot, "base")));
            long kept = (long) Math.floor(progress * keep);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "base"), now - kept);
            ProgressiveStagesAPI.setCounter(player, slotKey(slot, "held"), kept);
        }
        long until = player.server.overworld().getGameTime() + Math.max(0, tree.rules.freezeTicks);
        ProgressiveStagesAPI.setCounter(player, FREEZE, until);
        int percent = (int) Math.round(tree.rules.deathTaskLoss * 100);
        player.sendSystemMessage(Component.literal("You died. -" + lost + " " + shards(lost) + ", -" + percent
                + "% progress, frozen for " + Math.max(1, Math.round(tree.rules.freezeTicks / 1200.0)) + " minutes")
                .withStyle(ChatFormatting.RED));
    }

    public static long frozenTicks(ServerPlayer player) {
        long until = ProgressiveStagesAPI.getCounter(player, FREEZE);
        long now = player.server.overworld().getGameTime();
        return Math.max(0, until - now);
    }

    // ------------------------------------------------------------------ discoveries

    private static void checkDiscoveries(ServerPlayer player, Tree tree) {
        for (int i = 0; i < tree.discoveries.size(); i++) {
            Tree.Discovery discovery = tree.discoveries.get(i);
            if (ProgressiveStagesAPI.getCounter(player, discoveryKey(discovery)) != 0) {
                continue;
            }
            if (!advancementDone(player, discovery.advancement)) {
                continue;
            }
            ProgressiveStagesAPI.setCounter(player, discoveryKey(discovery), 1L);
            if (discovery.reward > 0) {
                ProgressiveStagesAPI.addCounter(player, POINTS, discovery.reward);
            }
            player.sendSystemMessage(Component.literal("Discovery: " + discovery.title + "  +" + discovery.reward + " "
                    + shards(discovery.reward)).withStyle(ChatFormatting.GOLD));
        }
    }

    public static boolean discovered(ServerPlayer player, Tree.Discovery discovery) {
        return ProgressiveStagesAPI.getCounter(player, discoveryKey(discovery)) != 0;
    }

    private static boolean advancementDone(ServerPlayer player, String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        if (key == null) {
            return false;
        }
        AdvancementHolder holder = player.server.getAdvancements().get(key);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    // ------------------------------------------------------------------ snapshot

    /**
     * One line per node task, then one per bounty slot.
     *
     * <p>Task lines: node id, state (0 waiting, 1 running, 4 done), progress, goal, 0, 0.
     *
     * <p>Slot lines: "slot:N", state (0 empty, 1 running, 5 done today, 6 expired), progress, goal, bounty index
     * (the one running, or the one that last finished or expired; -1 for none), ticks left (to the deadline while
     * running, otherwise to the next dawn).
     */
    public static String snapshot(ServerPlayer player) {
        Tree tree = Tree.get();
        StringBuilder text = new StringBuilder();
        for (Tree.Node node : tree.nodes) {
            if (!node.hasTask()) {
                continue;
            }
            int state;
            long cur;
            if (claimed(player, node)) {
                state = DONE;
                cur = node.task.amount;
            } else if (ProgressiveStagesAPI.getCounter(player, "fpa_" + node.key()) != 0) {
                state = RUNNING;
                cur = taskProgress(player, node, tree);
            } else {
                state = WAITING;
                cur = 0;
            }
            line(text, node.id, state, Math.min(node.task.amount, cur), node.task.amount, 0, 0);
        }
        long dayTime = Math.max(0, player.server.overworld().getDayTime());
        long dayTicks = Math.max(1, tree.rules.dayTicks);
        long toDawn = dayTicks - dayTime % dayTicks;
        for (int slot = 0; slot < slots(tree); slot++) {
            Tree.Bounty bounty = slotBounty(player, tree, slot);
            if (bounty != null) {
                long end = ProgressiveStagesAPI.getCounter(player, slotKey(slot, "end"));
                long left = Math.max(0, end * dayTicks - dayTime);
                long cur = Math.min(bounty.amount, bountyProgress(player, tree, slot, bounty));
                line(text, "slot:" + slot, SLOT_RUNNING, cur, bounty.amount, tree.bounties.indexOf(bounty), left);
            } else {
                int out = (int) ProgressiveStagesAPI.getCounter(player, slotKey(slot, "out"));
                int prev = (int) ProgressiveStagesAPI.getCounter(player, slotKey(slot, "prev")) - 1;
                int state = out == SLOT_DONE ? SLOT_DONE : out == SLOT_EXPIRED ? SLOT_EXPIRED : SLOT_EMPTY;
                line(text, "slot:" + slot, state, 0, 0, state == SLOT_EMPTY ? -1 : prev, toDawn);
            }
        }
        return text.toString();
    }

    private static void line(StringBuilder text, String id, long... values) {
        if (!text.isEmpty()) {
            text.append('\n');
        }
        text.append(id);
        for (long value : values) {
            text.append('\u001f').append(value);
        }
    }

    public static String foundSnapshot(ServerPlayer player) {
        Tree tree = Tree.get();
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < tree.discoveries.size(); i++) {
            if (discovered(player, tree.discoveries.get(i))) {
                if (!text.isEmpty()) {
                    text.append(',');
                }
                text.append(i);
            }
        }
        return text.toString();
    }

    public static Set<String> owned(ServerPlayer player, Tree tree) {
        Set<String> owned = new LinkedHashSet<>();
        for (Tree.Node node : tree.nodes) {
            if (ProgressiveStagesAPI.hasStage(player, node.id)) {
                owned.add(node.id);
            }
        }
        return owned;
    }

    // ------------------------------------------------------------------ stats

    /** Advancement and streak tasks read a total; "hold" reads the hand. Everything else counts from a baseline. */
    private static boolean baselined(Tree.Task task) {
        return !"advancement".equals(task.kind) && !"streak".equals(task.kind) && !"hold".equals(task.kind);
    }

    public static long stat(ServerPlayer player, Tree.Task task, Tree tree) {
        StatsCounter stats = player.getStats();
        String target = task.target == null ? "" : task.target;
        return switch (task.kind) {
            case "walk" -> custom(stats, Stats.WALK_ONE_CM) / 100;
            case "sprint" -> custom(stats, Stats.SPRINT_ONE_CM) / 100;
            case "swim" -> custom(stats, Stats.SWIM_ONE_CM) / 100;
            case "crouch" -> custom(stats, Stats.CROUCH_ONE_CM) / 100;
            case "climb" -> custom(stats, Stats.CLIMB_ONE_CM) / 100;
            case "fly" -> custom(stats, Stats.AVIATE_ONE_CM) / 100;
            case "boat" -> custom(stats, Stats.BOAT_ONE_CM) / 100;
            case "minecart" -> custom(stats, Stats.MINECART_ONE_CM) / 100;
            case "horse" -> custom(stats, Stats.HORSE_ONE_CM) / 100;
            case "strider" -> custom(stats, Stats.STRIDER_ONE_CM) / 100;
            case "travel" -> (custom(stats, Stats.WALK_ONE_CM) + custom(stats, Stats.SPRINT_ONE_CM)
                    + custom(stats, Stats.CROUCH_ONE_CM) + custom(stats, Stats.SWIM_ONE_CM)
                    + custom(stats, Stats.WALK_ON_WATER_ONE_CM) + custom(stats, Stats.WALK_UNDER_WATER_ONE_CM)
                    + custom(stats, Stats.CLIMB_ONE_CM) + custom(stats, Stats.BOAT_ONE_CM)
                    + custom(stats, Stats.HORSE_ONE_CM) + custom(stats, Stats.MINECART_ONE_CM)
                    + custom(stats, Stats.PIG_ONE_CM) + custom(stats, Stats.STRIDER_ONE_CM)
                    + custom(stats, Stats.AVIATE_ONE_CM)) / 100;
            case "jump" -> custom(stats, Stats.JUMP);
            case "sleep" -> custom(stats, Stats.SLEEP_IN_BED);
            case "breed" -> custom(stats, Stats.ANIMALS_BRED);
            case "enchant" -> custom(stats, Stats.ENCHANT_ITEM);
            case "fish" -> custom(stats, Stats.FISH_CAUGHT);
            case "trade" -> custom(stats, Stats.TRADED_WITH_VILLAGER);
            case "anvil" -> custom(stats, Stats.INTERACT_WITH_ANVIL);
            case "furnace" -> custom(stats, Stats.INTERACT_WITH_FURNACE);
            case "raid" -> custom(stats, Stats.RAID_WIN);
            case "mobs" -> custom(stats, Stats.MOB_KILLS);
            case "mine" -> {
                long total = 0;
                for (Block block : resolve(BLOCKS, BuiltInRegistries.BLOCK, target)) {
                    total += stats.getValue(Stats.BLOCK_MINED.get(block));
                }
                yield total;
            }
            case "kill" -> {
                long total = 0;
                for (EntityType<?> type : resolve(ENTITIES, BuiltInRegistries.ENTITY_TYPE, target)) {
                    total += stats.getValue(Stats.ENTITY_KILLED.get(type));
                }
                yield total;
            }
            // Planting a seed and eating food both count as using the item.
            case "use", "plant", "eat" -> {
                long total = 0;
                for (Item item : resolve(ITEMS, BuiltInRegistries.ITEM, target)) {
                    total += stats.getValue(Stats.ITEM_USED.get(item));
                }
                yield total;
            }
            case "craft" -> {
                long total = 0;
                for (Item item : resolve(ITEMS, BuiltInRegistries.ITEM, target)) {
                    total += stats.getValue(Stats.ITEM_CRAFTED.get(item));
                }
                yield total;
            }
            case "obtain" -> {
                long total = 0;
                for (Item item : resolve(ITEMS, BuiltInRegistries.ITEM, target)) {
                    total += Math.max(stats.getValue(Stats.ITEM_PICKED_UP.get(item)),
                            stats.getValue(Stats.ITEM_CRAFTED.get(item)));
                }
                yield total;
            }
            // Old "brew" tasks without a target count uses of a brewing stand; with a target, brewed potions.
            case "brew" -> target.isEmpty() ? custom(stats, Stats.INTERACT_WITH_BREWINGSTAND)
                    : ProgressiveStagesAPI.getCounter(player, eventKey("brew", target));
            case "tame" -> ProgressiveStagesAPI.getCounter(player, eventKey("tame", target));
            case "underwater" -> ProgressiveStagesAPI.getCounter(player, eventKey("underwater", ""));
            case "hold" -> held(player, target);
            case "advancement" -> advancementDone(player, target) ? 1 : 0;
            case "streak" -> daysAlive(player, tree);
            default -> 0;
        };
    }

    private static long custom(StatsCounter stats, ResourceLocation stat) {
        return stats.getValue(Stats.CUSTOM.get(stat));
    }

    /** Comma-separated ids; an entry starting with "re:" is a regex over the registry. */
    private static <T> List<T> resolve(Map<String, List<T>> cache, Registry<T> registry, String target) {
        List<T> hit = cache.get(target);
        if (hit != null) {
            return hit;
        }
        List<T> found = new ArrayList<>();
        for (String raw : target.split(",")) {
            String part = raw.trim();
            if (part.isEmpty()) {
                continue;
            }
            if (part.startsWith("re:")) {
                Predicate<String> rule = LockResolver.matcher(part);
                for (ResourceLocation key : registry.keySet()) {
                    if (rule.test(key.toString())) {
                        found.add(registry.get(key));
                    }
                }
                continue;
            }
            ResourceLocation key = ResourceLocation.tryParse(part);
            if (key != null && registry.containsKey(key)) {
                found.add(registry.get(key));
            }
        }
        cache.put(target, found);
        return found;
    }

    private static String shards(long amount) {
        return amount == 1 ? "Focal Shard" : "Focal Shards";
    }

    private static String discoveryKey(Tree.Discovery discovery) {
        return "fpd_" + discovery.advancement.replaceAll("[^a-z0-9_]", "_");
    }
}
