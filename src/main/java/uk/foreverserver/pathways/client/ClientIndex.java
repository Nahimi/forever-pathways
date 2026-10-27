package uk.foreverserver.pathways.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import uk.foreverserver.pathways.LockResolver;
import uk.foreverserver.pathways.StageWriter;
import uk.foreverserver.pathways.Tree;

/**
 * The client's copy of "which node unlocks which item", built from the same
 * rules the server uses to write the stage files. Feeds the Unlocks grid and
 * the search box.
 */
final class ClientIndex {
    private static ClientIndex instance;

    final Map<String, List<ItemStack>> stacks = new HashMap<>();
    final Map<String, String> owner = new HashMap<>();
    private final List<String> ids = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Map<String, ItemStack> icons = new HashMap<>();

    static void reset() {
        instance = null;
    }

    static ClientIndex get() {
        if (instance == null) {
            instance = new ClientIndex();
        }
        return instance;
    }

    private ClientIndex() {
        Tree tree = Tree.get();
        LockResolver.Result result = LockResolver.resolve(tree, StageWriter.allItems());
        owner.putAll(result.owner);
        for (Map.Entry<String, List<String>> entry : result.byNode.entrySet()) {
            List<ItemStack> list = new ArrayList<>();
            for (String id : entry.getValue()) {
                ItemStack stack = stack(id);
                if (!stack.isEmpty()) {
                    list.add(stack);
                    ids.add(id);
                    String name;
                    try {
                        name = stack.getHoverName().getString();
                    } catch (RuntimeException exception) {
                        name = id;
                    }
                    names.add((name + " " + id).toLowerCase(Locale.ROOT));
                }
            }
            stacks.put(entry.getKey(), list);
        }
        // Items a node lets you use though another node owns them (and their crafting).
        List<String> all = StageWriter.allItems();
        for (Tree.Node node : tree.nodes) {
            if (node.uses == null || node.uses.isEmpty()) {
                continue;
            }
            List<ItemStack> list = new ArrayList<>();
            for (String spec : node.uses) {
                java.util.function.Predicate<String> rule = LockResolver.matcher(spec);
                for (String id : all) {
                    if (rule.test(id)) {
                        ItemStack stack = stack(id);
                        if (!stack.isEmpty() && stack.getItem() != Items.BARRIER) {
                            list.add(stack);
                            useOwner.put(stack.getItem(), owner.get(id));
                        }
                    }
                }
            }
            if (!list.isEmpty()) {
                useStacks.put(node.id, list);
            }
        }
    }

    final Map<String, List<ItemStack>> useStacks = new HashMap<>();
    /** For a use-only item: the node that owns it, where its crafting is unlocked. */
    final Map<Item, String> useOwner = new HashMap<>();

    List<ItemStack> items(String nodeId) {
        return stacks.getOrDefault(nodeId, List.of());
    }

    /** Items this node lets you use, though their crafting is unlocked elsewhere. */
    List<ItemStack> uses(String nodeId) {
        return useStacks.getOrDefault(nodeId, List.of());
    }

    /** Node id to the number of its items whose name or id contains the query. */
    Map<String, Integer> search(String query) {
        Map<String, Integer> hits = new HashMap<>();
        String needle = query.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) {
            return hits;
        }
        for (int i = 0; i < ids.size(); i++) {
            if (names.get(i).contains(needle)) {
                String node = owner.get(ids.get(i));
                if (node != null) {
                    hits.merge(node, 1, Integer::sum);
                }
            }
        }
        return hits;
    }

    ItemStack icon(String id) {
        return icons.computeIfAbsent(id == null ? "" : id, ClientIndex::stack);
    }

    static ItemStack stack(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        if (key == null) {
            return new ItemStack(Items.BARRIER);
        }
        Item item = BuiltInRegistries.ITEM.get(key);
        if (item == Items.AIR) {
            return new ItemStack(Items.BARRIER);
        }
        return new ItemStack(item);
    }
}
