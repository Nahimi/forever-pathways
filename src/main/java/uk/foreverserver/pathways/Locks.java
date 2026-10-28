package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which node locks an item, worked out from tree.json and the item registry with the same
 * rules that write the ProgressiveStages files. Both sides build this themselves, so the
 * client knows exactly what the server will refuse without asking anyone.
 */
public final class Locks {
    private static Map<String, String> owners;
    private static List<Predicate<String>> usable;
    private static final Map<Item, String> CACHE = new HashMap<>();
    private static final Map<Item, Boolean> USABLE_CACHE = new HashMap<>();
    private static final Map<Item, Boolean> CRAFT_CACHE = new HashMap<>();
    private static final Map<Block, Boolean> GUARD_CACHE = new HashMap<>();
    private static List<Predicate<String>> guard;
    private static List<Predicate<String>> everyday;

    private Locks() {}

    /** Forgets everything worked out from the tree; it is worked out again from the new one. */
    public static synchronized void reset() {
        owners = null;
        usable = null;
        guard = null;
        everyday = null;
        users = null;
        CACHE.clear();
        USABLE_CACHE.clear();
        CRAFT_CACHE.clear();
        GUARD_CACHE.clear();
        USERS_CACHE.clear();
    }

    private static synchronized Map<String, String> owners() {
        if (owners == null) {
            owners = new HashMap<>(LockResolver.resolve(Tree.get(), StageWriter.allItems()).owner);
        }
        return owners;
    }

    /** The node that locks this item, or null when nothing does (or the Core does, which is the same). */
    public static synchronized String ownerOf(Item item) {
        if (item == null) {
            return null;
        }
        if (CACHE.containsKey(item)) {
            return CACHE.get(item);
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        String owner = key == null ? null : owners().get(key.toString());
        if (owner != null && owner.equals(Tree.get().core().id)) {
            owner = null;
        }
        CACHE.put(item, owner);
        return owner;
    }

    /**
     * Is this block one a lock should actually guard?
     *
     * Terrain, ore, planks, wool and every other plain block are not guarded: anyone may break them
     * and place them, whatever they own. What a lock is for is the things that DO something; a
     * furnace, a chest, an enchanting table, a Create machine. Those stay shut until their node is
     * earned, and are the only blocks Pathways refuses to place or break.
     *
     * The test is simple and needs no list: a block that keeps its own data (a block entity) is doing
     * something, and the handful of vanilla workstations that do not keep data are named in
     * tree.json's "guardedBlocks".
     */
    public static synchronized boolean guarded(BlockState state) {
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        if (block == null || state.isAir()) {
            return false;
        }
        Boolean known = GUARD_CACHE.get(block);
        if (known != null) {
            return known;
        }
        boolean hit;
        try {
            hit = state.hasBlockEntity();
        } catch (RuntimeException exception) {
            hit = false;
        }
        if (!hit) {
            if (guard == null) {
                guard = new ArrayList<>();
                for (String spec : Tree.get().guardedBlocks) {
                    try {
                        guard.add(LockResolver.matcher(spec));
                    } catch (RuntimeException exception) {
                        ForeverPathways.LOGGER.warn("Pathways: bad guardedBlocks spec {}", spec);
                    }
                }
            }
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            if (key != null) {
                String id = key.toString();
                for (Predicate<String> rule : guard) {
                    if (rule.test(id)) {
                        hit = true;
                        break;
                    }
                }
            }
        }
        GUARD_CACHE.put(block, hit);
        return hit;
    }

    /**
     * Decoration, furniture and everyday things: anyone may carry it, place it, use it and break it while it is
     * locked. Only the recipe waits for the node, which {@link CraftGuard} enforces. That is every item on a
     * craft-only node, and every item in the tree's "craftOnlyItems" whatever node it is on.
     */
    public static synchronized boolean craftOnly(Item item) {
        if (item == null) {
            return false;
        }
        Boolean known = CRAFT_CACHE.get(item);
        if (known != null) {
            return known;
        }
        String owner = ownerOf(item);
        Tree.Node node = owner == null ? null : Tree.get().find(owner);
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        boolean hit = node != null && (node.craftOnly || (key != null && everyday(key.toString())));
        CRAFT_CACHE.put(item, hit);
        return hit;
    }

    /** True when the tree's "craftOnlyItems" names this item id. */
    public static synchronized boolean everyday(String id) {
        if (everyday == null) {
            everyday = new ArrayList<>();
            for (String spec : Tree.get().craftOnlyItems) {
                try {
                    everyday.add(LockResolver.matcher(spec));
                } catch (RuntimeException exception) {
                    ForeverPathways.LOGGER.warn("Pathways: bad craftOnlyItems spec {}", spec);
                }
            }
        }
        for (Predicate<String> rule : everyday) {
            if (rule.test(id)) {
                return true;
            }
        }
        return false;
    }

    private static final Map<Item, List<String>> USERS_CACHE = new HashMap<>();
    private static List<Map.Entry<String, Predicate<String>>> users;

    /** Nodes whose {@code uses} list lets their owners open this block though it is locked to them. */
    public static synchronized List<String> usersOf(Item item) {
        if (item == null) {
            return List.of();
        }
        List<String> known = USERS_CACHE.get(item);
        if (known != null) {
            return known;
        }
        if (users == null) {
            users = new ArrayList<>();
            for (Tree.Node node : Tree.get().nodes) {
                if (node.uses == null) {
                    continue;
                }
                for (String spec : node.uses) {
                    try {
                        users.add(Map.entry(node.id, LockResolver.matcher(spec)));
                    } catch (RuntimeException exception) {
                        ForeverPathways.LOGGER.warn("Pathways: bad uses spec {} on {}", spec, node.id);
                    }
                }
            }
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        List<String> hit = new ArrayList<>();
        if (key != null) {
            String id = key.toString();
            for (Map.Entry<String, Predicate<String>> rule : users) {
                if (rule.getValue().test(id) && !hit.contains(rule.getKey())) {
                    hit.add(rule.getKey());
                }
            }
        }
        USERS_CACHE.put(item, hit);
        return hit;
    }

    /** Doors, gates, buttons and the like: anyone may open, close or press them, locked or not. */
    public static synchronized boolean alwaysUsable(Item item) {
        if (item == null) {
            return false;
        }
        Boolean known = USABLE_CACHE.get(item);
        if (known != null) {
            return known;
        }
        if (usable == null) {
            usable = new ArrayList<>();
            for (String spec : Tree.get().alwaysUsable) {
                try {
                    usable.add(LockResolver.matcher(spec));
                } catch (RuntimeException exception) {
                    ForeverPathways.LOGGER.warn("Pathways: bad alwaysUsable spec {}", spec);
                }
            }
        }
        ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
        boolean hit = false;
        if (key != null) {
            String id = key.toString();
            for (Predicate<String> rule : usable) {
                if (rule.test(id)) {
                    hit = true;
                    break;
                }
            }
        }
        USABLE_CACHE.put(item, hit);
        return hit;
    }
}
