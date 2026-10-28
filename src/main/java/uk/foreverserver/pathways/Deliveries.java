package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import uk.foreverserver.pathways.mixin.SmithingTransformRecipeAccessor;

/**
 * Delivery bounties: hand in a number of one crafted item for shards.
 *
 * <p>Every item a recipe makes is given a cost: what one of it takes in raw materials, by its cheapest recipe of
 * any kind (crafting, furnace, smithing, stonecutter, modded machines). A raw material, an item no recipe makes,
 * costs 1 unless {@code delivery.values} says otherwise, so a diamond can cost more than a cobblestone. Precious
 * items (boss drops and the like) cost nothing can pay, so neither they nor anything made from them is asked for.
 *
 * <p>The costs are worked out once per set of recipes and again after a reload.
 */
public final class Deliveries {
    private static final double NEVER = Double.POSITIVE_INFINITY;

    /** One offer: so many of an item, at a tier, for a reward. */
    public record Offer(Item item, int amount, int tier, int reward) {}

    private static RecipeManager pricedFor;
    private static Map<Item, Double> costs = Map.of();
    /** The ingredients of each item's cheapest recipe, to check the player may make them too. */
    private static Map<Item, List<Ingredient>> cheapest = Map.of();
    private static final Map<Long, Item> BY_STAMP = new HashMap<>();

    private Deliveries() {}

    // ------------------------------------------------------------------ costs

    private static synchronized void price(MinecraftServer server, Tree tree) {
        RecipeManager manager = server.getRecipeManager();
        if (manager == pricedFor) {
            return;
        }
        HolderLookup.Provider access = server.registryAccess();
        record Way(Item out, int count, List<Ingredient> inputs) {}
        List<Way> ways = new ArrayList<>();
        for (RecipeHolder<?> holder : manager.getRecipes()) {
            try {
                Recipe<?> recipe = holder.value();
                ItemStack out = recipe.getResultItem(access);
                if (out == null || out.isEmpty()) {
                    continue;
                }
                List<Ingredient> inputs = new ArrayList<>();
                if (recipe instanceof SmithingTransformRecipe smithing) {
                    SmithingTransformRecipeAccessor parts = (SmithingTransformRecipeAccessor) smithing;
                    inputs.add(parts.foreverpathways$template());
                    inputs.add(parts.foreverpathways$base());
                    inputs.add(parts.foreverpathways$addition());
                } else {
                    inputs.addAll(recipe.getIngredients());
                }
                inputs.removeIf(ingredient -> ingredient == null || ingredient.isEmpty());
                if (!inputs.isEmpty()) {
                    ways.add(new Way(out.getItem(), out.getCount(), inputs));
                }
            } catch (RuntimeException exception) {
                // a modded recipe that cannot answer outside a real crafting grid; it is simply not counted
            }
        }

        Set<Item> made = new HashSet<>();
        for (Way way : ways) {
            made.add(way.out);
        }
        Map<Item, Double> fixed = new HashMap<>();
        Tree.Delivery rules = tree.delivery;
        for (Map.Entry<String, Double> entry : rules.values.entrySet()) {
            Item item = item(entry.getKey());
            if (item != null && entry.getValue() != null) {
                fixed.put(item, Math.max(0.01, entry.getValue()));
            }
        }
        for (Item item : precious(tree)) {
            fixed.put(item, NEVER);
        }

        Map<Item, Double> cost = new HashMap<>();
        for (Item item : BuiltInRegistries.ITEM) {
            cost.put(item, fixed.containsKey(item) ? fixed.get(item) : made.contains(item) ? NEVER : 1.0);
        }
        Map<Item, List<Ingredient>> best = new HashMap<>();
        for (int round = 0; round < 2; round++) {
            // Costs settle as cheaper ways are found; a few passes are enough for the deepest chains in a pack.
            for (int pass = 0; pass < 16; pass++) {
                boolean changed = false;
                for (Way way : ways) {
                    if (fixed.containsKey(way.out)) {
                        continue;
                    }
                    double sum = 0;
                    for (Ingredient ingredient : way.inputs) {
                        sum += cheapestOption(ingredient, cost);
                        if (sum == NEVER) {
                            break;
                        }
                    }
                    if (sum == NEVER) {
                        continue;
                    }
                    double each = sum / Math.max(1, way.count);
                    if (each < cost.get(way.out) - 1e-9) {
                        cost.put(way.out, each);
                        best.put(way.out, way.inputs);
                        changed = true;
                    }
                }
                if (!changed) {
                    break;
                }
            }
            // Something only ever unpacked from its own block (wheat from a hay bale) is a raw material: give it
            // the raw cost and settle again, so the block and everything made from it are priced too.
            boolean unpacked = false;
            for (Way way : ways) {
                if (way.inputs.size() == 1 && way.count > 1 && cost.get(way.out) == NEVER && !fixed.containsKey(way.out)
                        && way.inputs.get(0).getItems().length > 0
                        && Arrays.stream(way.inputs.get(0).getItems()).noneMatch(stack -> fixed.containsKey(stack.getItem()))) {
                    cost.put(way.out, 1.0);
                    unpacked = true;
                }
            }
            if (!unpacked) {
                break;
            }
        }
        costs = cost;
        cheapest = best;
        pricedFor = manager;
    }

    private static double cheapestOption(Ingredient ingredient, Map<Item, Double> cost) {
        double low = NEVER;
        for (ItemStack option : ingredient.getItems()) {
            Double value = cost.get(option.getItem());
            if (value != null && value < low) {
                low = value;
            }
        }
        return low;
    }

    private static Set<Item> precious(Tree tree) {
        Set<Item> out = new HashSet<>();
        for (String id : tree.delivery.precious) {
            Item item = item(id);
            if (item != null) {
                out.add(item);
            }
        }
        for (Tree.BossDrop drop : tree.bossDrops) {
            Item item = item(drop.item);
            if (item != null) {
                out.add(item);
            }
        }
        return out;
    }

    /** What one of the item costs in raw materials; infinite when it is never asked for. */
    public static double cost(MinecraftServer server, Item item) {
        price(server, Tree.get());
        return costs.getOrDefault(item, NEVER);
    }

    // ------------------------------------------------------------------ offers

    /**
     * A delivery for this player: an item a recipe makes, whose node and ingredients they own. The draw leans on
     * the highest tiers they have reached, as other bounties do; tier VI stays rare. Null when nothing fits.
     */
    public static Offer offer(ServerPlayer player, Tree tree, Set<String> owned, RandomSource random) {
        price(player.server, tree);
        Tree.Delivery rules = tree.delivery;
        List<Predicate<String>> ignore = new ArrayList<>();
        for (String spec : rules.ignore) {
            ignore.add(LockResolver.matcher(spec));
        }
        Map<Integer, List<Item>> byTier = new HashMap<>();
        for (Map.Entry<Item, List<Ingredient>> entry : cheapest.entrySet()) {
            Item item = entry.getKey();
            double each = costs.getOrDefault(item, NEVER);
            if (each == NEVER || item == Items.AIR) {
                continue;
            }
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String id = key.toString();
            if (ignore.stream().anyMatch(rule -> rule.test(id))) {
                continue;
            }
            if (!mayHave(item, tree, owned) || !entry.getValue().stream().allMatch(ing -> mayUseAny(ing, tree, owned))) {
                continue;
            }
            int tier = tierOf(item, tree);
            int budget = Tree.Delivery.pick(rules.budgets, tier, 16);
            // Something far dearer than the tier's budget would be one item for a fortune; leave it out.
            if (each > budget * 2.0) {
                continue;
            }
            byTier.computeIfAbsent(tier, t -> new ArrayList<>()).add(item);
        }
        if (byTier.isEmpty()) {
            return null;
        }
        int top = byTier.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
        int total = 0;
        for (int tier : byTier.keySet()) {
            total += weight(tier, top);
        }
        int roll = random.nextInt(total);
        int tier = top;
        for (int t : byTier.keySet()) {
            roll -= weight(t, top);
            if (roll < 0) {
                tier = t;
                break;
            }
        }
        List<Item> items = byTier.get(tier);
        Item item = items.get(random.nextInt(items.size()));
        return new Offer(item, amount(item, tier, tree), tier, Tree.Delivery.pick(rules.rewards, tier, 1));
    }

    /** As for other bounties: the two highest tiers most, the one below now and then, tier VI seldom. */
    private static int weight(int tier, int top) {
        if (tier >= 6) {
            return 1;
        }
        int behind = top - tier;
        return behind <= 1 ? 6 : behind == 2 ? 3 : 1;
    }

    private static int amount(Item item, int tier, Tree tree) {
        Tree.Delivery rules = tree.delivery;
        double each = Math.max(0.01, costs.getOrDefault(item, 1.0));
        double n = Tree.Delivery.pick(rules.budgets, tier, 16) / each;
        int stack = Math.max(1, item.getDefaultMaxStackSize());
        int cap = stack == 1 ? Tree.Delivery.pick(rules.unstackable, tier, 1) : stack;
        n = Math.min(n, cap);
        int amount = n >= 16 ? (int) Math.round(n / 4.0) * 4 : (int) Math.round(n);
        return Math.max(1, Math.min(cap, amount));
    }

    private static int tierOf(Item item, Tree tree) {
        String owner = Locks.ownerOf(item);
        Tree.Node node = owner == null ? null : tree.find(owner);
        return tree.delivery.tierOf(node == null ? 0 : node.ring);
    }

    private static boolean mayHave(Item item, Tree tree, Set<String> owned) {
        String owner = Locks.ownerOf(item);
        return owner == null || owned.contains(owner) || tree.find(owner) == null;
    }

    private static boolean mayUseAny(Ingredient ingredient, Tree tree, Set<String> owned) {
        for (ItemStack option : ingredient.getItems()) {
            if (mayHave(option.getItem(), tree, owned)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ handing in

    /** Only a plain item counts: no damage, no name, no enchantment, nothing changed about it at all. */
    public static boolean plain(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.is(item) && stack.getComponentsPatch().isEmpty();
    }

    /** How many plain ones the player carries, in the main inventory and the off hand. */
    public static int carried(Player player, Item item) {
        Inventory inventory = player.getInventory();
        int total = 0;
        for (ItemStack stack : inventory.items) {
            if (plain(stack, item)) {
                total += stack.getCount();
            }
        }
        for (ItemStack stack : inventory.offhand) {
            if (plain(stack, item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Takes the whole amount, or nothing if the player does not carry it all. */
    public static boolean take(ServerPlayer player, Item item, int amount) {
        if (carried(player, item) < amount) {
            return false;
        }
        int left = amount;
        Inventory inventory = player.getInventory();
        for (List<ItemStack> part : List.of(inventory.items, inventory.offhand)) {
            for (ItemStack stack : part) {
                if (left <= 0) {
                    break;
                }
                if (plain(stack, item)) {
                    int used = Math.min(left, stack.getCount());
                    stack.shrink(used);
                    left -= used;
                }
            }
        }
        inventory.setChanged();
        player.containerMenu.broadcastChanges();
        return true;
    }

    // ------------------------------------------------------------------ ids

    /** An item as a number for a counter: its id's hash, plus one so 0 means none. */
    public static synchronized long stamp(Item item) {
        long stamp = (BuiltInRegistries.ITEM.getKey(item).toString().hashCode() & 0x7fffffffL) + 1L;
        BY_STAMP.put(stamp, item);
        return stamp;
    }

    public static synchronized Item byStamp(long stamp) {
        if (stamp == 0) {
            return null;
        }
        if (BY_STAMP.isEmpty() || !BY_STAMP.containsKey(stamp)) {
            for (Item item : BuiltInRegistries.ITEM) {
                BY_STAMP.put((BuiltInRegistries.ITEM.getKey(item).toString().hashCode() & 0x7fffffffL) + 1L, item);
            }
        }
        return BY_STAMP.get(stamp);
    }

    private static Item item(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id == null ? "" : id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return null;
        }
        return BuiltInRegistries.ITEM.get(key);
    }
}
