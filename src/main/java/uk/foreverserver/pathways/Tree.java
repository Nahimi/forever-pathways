package uk.foreverserver.pathways;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;

/**
 * The whole tree, read from data/foreverpathways/tree.json inside the jar.
 * Nothing in here touches Minecraft, so both sides (and the tests) can use it.
 */
public final class Tree {
    public int version;
    public List<String> tiers = new ArrayList<>();
    public List<PathDef> paths = new ArrayList<>();
    public Map<String, Integer> slots = new HashMap<>();
    public Rules rules = new Rules();
    public List<String> ignore = new ArrayList<>();
    public List<Node> nodes = new ArrayList<>();
    public List<Discovery> discoveries = new ArrayList<>();
    public List<BossDrop> bossDrops = new ArrayList<>();
    /** Player stats that start low and that perks on nodes raise: movement, breaking, attack, boat. */
    public Map<String, StatDef> stats = new LinkedHashMap<>();
    /** Blocks anyone may right-click even while they are locked: doors, gates, buttons. Same spec syntax as locks. */
    public List<String> alwaysUsable = new ArrayList<>();
    /**
     * Blocks a lock should guard even though they keep no block entity: the vanilla workstations.
     * Everything with a block entity is guarded already, and every other block is free to place and
     * break whatever you own. Same spec syntax as locks.
     */
    public List<String> guardedBlocks = new ArrayList<>();
    /** Timed jobs offered at random each dawn, from a pool that grows as nodes are owned. */
    public List<Bounty> bounties = new ArrayList<>();
    /** Display names for bounty tiers, from tier 1. */
    public List<String> bountyTiers = new ArrayList<>();

    public String bountyTierName(int tier) {
        if (bountyTiers != null && tier >= 1 && tier <= bountyTiers.size()) {
            return bountyTiers.get(tier - 1);
        }
        return String.valueOf(tier);
    }

    private transient Map<String, Node> byId;
    private transient Node core;
    private transient List<Node> seals;

    private static Tree instance;

    public static Tree get() {
        if (instance == null) {
            instance = load(Tree.class.getResourceAsStream("/data/foreverpathways/tree.json"));
        }
        return instance;
    }

    public static Tree load(InputStream stream) {
        if (stream == null) {
            throw new IllegalStateException("Missing data/foreverpathways/tree.json");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Tree tree = new Gson().fromJson(reader, Tree.class);
            if (tree == null || tree.nodes == null || tree.nodes.isEmpty()) {
                throw new IllegalStateException("tree.json has no nodes");
            }
            tree.index();
            Availability.prune(tree);
            return tree;
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Could not read tree.json", exception);
        }
    }

    void index() {
        byId = new LinkedHashMap<>();
        seals = new ArrayList<>();
        for (Node node : nodes) {
            if (node.locks == null) {
                node.locks = new ArrayList<>();
            }
            byId.put(node.id, node);
            if (node.isSeal()) {
                seals.add(node);
            } else if (node.requires == null && core == null) {
                core = node;
            }
        }
        seals.sort((a, b) -> Integer.compare(a.seal.opensRing, b.seal.opensRing));
        for (int i = 0; i < seals.size(); i++) {
            Seal seal = seals.get(i).seal;
            if (seal.throughRing < seal.opensRing) {
                seal.throughRing = i + 1 < seals.size() ? seals.get(i + 1).seal.opensRing - 1 : seal.opensRing;
            }
        }
        if (core == null) {
            throw new IllegalStateException("tree.json has no core node (a node without requires)");
        }
        if (stats == null) {
            stats = new LinkedHashMap<>();
        }
        if (alwaysUsable == null) {
            alwaysUsable = new ArrayList<>();
        }
        if (guardedBlocks == null) {
            guardedBlocks = new ArrayList<>();
        }
        if (bounties == null) {
            bounties = new ArrayList<>();
        }
    }

    /** A stat's current value (1.0 is vanilla) for someone owning the given nodes, capped at its maximum. */
    public double statValue(String key, java.util.function.Predicate<String> owns) {
        StatDef def = stats.get(key);
        if (def == null) {
            return 1.0;
        }
        double value = def.base;
        for (Node node : nodes) {
            if (node.perks != null && node.perks.containsKey(key) && owns.test(node.id)) {
                value += node.perks.get(key);
            }
        }
        return Math.min(def.max, value);
    }

    public Node find(String id) {
        return id == null ? null : byId.get(id);
    }

    public Node core() {
        return core;
    }

    public List<Node> seals() {
        return seals;
    }

    /** The seal that must be broken before anything on this ring can be taken, or null. */
    public Node sealFor(int ring) {
        Node best = null;
        for (Node seal : seals) {
            if (seal.seal.opensRing <= ring) {
                best = seal;
            }
        }
        return best;
    }

    public PathDef path(String id) {
        for (PathDef path : paths) {
            if (path.id.equals(id)) {
                return path;
            }
        }
        return null;
    }

    public String tierName(int ring) {
        return ring >= 0 && ring < tiers.size() ? tiers.get(ring) : "Ring " + ring;
    }

    public int outerRing() {
        int max = 0;
        for (Node node : nodes) {
            max = Math.max(max, node.ring);
        }
        return max;
    }

    public static final class PathDef {
        public String id;
        public String title;
        public String colour;
    }

    public static final class Rules {
        public int startingShards = 0;
        public int dayTicks = 24000;
        public int shardsPerDay = 0;
        /** How many bounties a player is offered each dawn. A finished bounty's slot stays empty until the next dawn. */
        public int bountySlots = 2;
        public int deathShardLoss = 1;
        public double deathTaskLoss = 0.25;
        public int freezeTicks = 24000;
    }

    public static final class Seal {
        public int level;
        public int opensRing;
        /** Last ring the seal covers. 0 in old files means just its opening ring. */
        public int throughRing;
    }

    public static final class Task {
        public String kind;
        public String target = "";
        public int amount = 1;
        public String title = "";
        public int reward;
    }

    /** A boss that drops an item (an eye) when a player kills it. That item is stripped from all other loot. */
    public static final class BossDrop {
        public String entity;
        public String boss;
        public String item;
        public double chance = 1.0;
    }

    public static final class StatDef {
        public String title;
        public double base = 1.0;
        public double max = 1.0;
    }

    /**
     * A timed job outside the tree. It joins a player's bounty pool once {@link #requires} is owned, and is
     * offered at random at dawn. It must be finished within {@link #days} in-game days.
     */
    public static final class Bounty {
        public String id;
        /** 1 for the first days, up to 6 for the End. Harder bounties pay more and give more time. */
        public int tier = 1;
        /** Dawns until it expires: 1 means it must be done before the next dawn. */
        public int days = 1;
        public String title;
        public String kind;
        public String target = "";
        public int amount = 1;
        public int reward = 1;
        public String requires;
        public String item;
        private transient Task task;

        public Task task() {
            if (task == null) {
                task = new Task();
                task.kind = kind;
                task.target = target == null ? "" : target;
                task.amount = amount;
                task.title = title;
                task.reward = reward;
            }
            return task;
        }
    }

    public static final class Discovery {
        public String advancement;
        public String title;
        public int reward;
    }

    public static final class Node {
        public String id;
        public String title;
        public String path;
        public int ring;
        public String requires;
        public String also;
        public int cost;
        public String item;
        public String colour;
        public String opens = "";
        public Task task;
        public String slot;
        public Map<String, Integer> slotBonus;
        public List<String> locks = new ArrayList<>();
        public Seal seal;
        /** The strongest enchanting this node allows, as a table level cap. 0 means it teaches none. */
        public int enchantPower;
        /**
         * True for decoration and furniture: anyone may carry, place, use and break what this node
         * holds, and the node only buys the recipe. A locked chair is still a chair someone else can
         * sit on.
         */
        public boolean craftOnly;

        /**
         * Blocks this node lets you open and use, though another node owns them. Placing, breaking
         * and crafting still wait for the owner. Waystones are the case: anyone who owns Explorer may
         * use the ones standing on the Monoliths, but only Attunement makes or moves them.
         */
        public List<String> uses = new ArrayList<>();

        /** Stat bonuses this node grants, e.g. {"movement": 0.1}. */
        public Map<String, Double> perks;

        public boolean isSeal() {
            return seal != null;
        }

        public boolean hasTask() {
            return task != null && task.kind != null && !task.kind.isBlank();
        }

        public boolean counts() {
            return !isSeal() && requires != null;
        }

        public String key() {
            int colon = id.indexOf(':');
            return colon < 0 ? id : id.substring(colon + 1);
        }

        public int rgb() {
            if (colour == null || colour.isBlank()) {
                return 0xC4A574;
            }
            try {
                return Integer.parseInt(colour.startsWith("#") ? colour.substring(1) : colour, 16);
            } catch (NumberFormatException exception) {
                return 0xC4A574;
            }
        }
    }
}
