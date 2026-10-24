package uk.foreverserver.pathways;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Turns the lock specs in tree.json into concrete item ids. Pure Java: the caller
 * passes every registered item id as "namespace:path".
 *
 * Order of claims (each item has exactly one owner):
 *   1. exact ids, in file order
 *   2. "re:" and "mod:" specs, from the outermost ring inwards
 *   3. "rest:namespace"
 *   4. "rest:*"
 * Items matched by the "ignore" list are never locked.
 * tools/check_tree.py implements the same rules.
 */
public final class LockResolver {
    private LockResolver() {}

    public static final class Result {
        public final Map<String, List<String>> byNode = new LinkedHashMap<>();
        public final Map<String, String> owner = new HashMap<>();
        public final List<String> warnings = new ArrayList<>();

        public List<String> items(String nodeId) {
            return byNode.getOrDefault(nodeId, List.of());
        }
    }

    public static Predicate<String> matcher(String spec) {
        if (spec.startsWith("re:")) {
            Pattern pattern = Pattern.compile(spec.substring(3));
            return id -> pattern.matcher(id).matches();
        }
        if (spec.startsWith("mod:")) {
            String prefix = spec.substring(4) + ":";
            return id -> id.startsWith(prefix);
        }
        return spec::equals;
    }

    public static Result resolve(Tree tree, Collection<String> allItems) {
        Result result = new Result();
        List<Predicate<String>> ignore = new ArrayList<>();
        for (String spec : tree.ignore) {
            try {
                ignore.add(matcher(spec));
            } catch (RuntimeException exception) {
                result.warnings.add("bad ignore spec " + spec + ": " + exception.getMessage());
            }
        }
        List<String> pool = new ArrayList<>();
        for (String id : allItems) {
            boolean skip = false;
            for (Predicate<String> rule : ignore) {
                if (rule.test(id)) {
                    skip = true;
                    break;
                }
            }
            if (!skip) {
                pool.add(id);
            }
        }
        pool.sort(String::compareTo);
        Set<String> known = new HashSet<>(pool);
        for (Tree.Node node : tree.nodes) {
            result.byNode.put(node.id, new ArrayList<>());
        }

        for (Tree.Node node : tree.nodes) {
            for (String spec : node.locks) {
                if (spec.startsWith("re:") || spec.startsWith("mod:") || spec.startsWith("rest:")) {
                    continue;
                }
                if (!known.contains(spec)) {
                    continue;
                }
                claim(result, node.id, spec);
            }
        }

        List<Tree.Node> outward = new ArrayList<>(tree.nodes);
        outward.sort((a, b) -> Integer.compare(b.ring, a.ring));
        for (Tree.Node node : outward) {
            for (String spec : node.locks) {
                if (!spec.startsWith("re:") && !spec.startsWith("mod:")) {
                    continue;
                }
                Predicate<String> rule;
                try {
                    rule = matcher(spec);
                } catch (RuntimeException exception) {
                    result.warnings.add(node.id + ": bad spec " + spec + ": " + exception.getMessage());
                    continue;
                }
                for (String id : pool) {
                    if (!result.owner.containsKey(id) && rule.test(id)) {
                        claim(result, node.id, id);
                    }
                }
            }
        }

        for (Tree.Node node : tree.nodes) {
            for (String spec : node.locks) {
                if (!spec.startsWith("rest:") || spec.equals("rest:*")) {
                    continue;
                }
                String prefix = spec.substring(5) + ":";
                for (String id : pool) {
                    if (!result.owner.containsKey(id) && id.startsWith(prefix)) {
                        claim(result, node.id, id);
                    }
                }
            }
        }

        for (Tree.Node node : tree.nodes) {
            if (node.locks.contains("rest:*")) {
                for (String id : pool) {
                    if (!result.owner.containsKey(id)) {
                        claim(result, node.id, id);
                    }
                }
            }
        }
        return result;
    }

    private static void claim(Result result, String nodeId, String item) {
        if (result.owner.containsKey(item)) {
            return;
        }
        result.owner.put(item, nodeId);
        result.byNode.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(item);
    }
}
