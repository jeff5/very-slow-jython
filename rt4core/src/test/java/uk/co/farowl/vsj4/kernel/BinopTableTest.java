package uk.co.farowl.vsj4.kernel;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj4.core.PyType;
import uk.co.farowl.vsj4.kernel.BinopTable.Key;

/**
 * The {@link BinopTable} class is used to accelerate binary operations
 * in a few, very common, built-in types. It is tested for function with
 * the binary call site tests. Here we satisfy ourselves that some
 * internal details work as intended.
 */
class BinopTableTest {

    /**
     * Where {@link BinopTable} is used in call sites, it is not in the
     * fast path of the operation, nor of any subclasses defined in
     * Python. It can be in the slow (fallback) path of other built-in
     * types, so we are interested to test that lookup is quick. This
     * will depend on how often more than one binary operation hashes to
     * the same location. We cannot inspect the inner workings of
     * {@code HashMap} (on which {@code BinopTable} is based) so we
     * simulate the same operations.
     * <p>
     * If this test fails, we have probably failed to update
     * {@link BinopTable#ENTRIES} in response to expansion of the
     * representations of the types, so correct that. If it still fails,
     * consider reducing {@link BinopTable#LOAD_FACTOR} so that
     * {@code SIZE} (in the test) reaches the next power of 2. There is
     * some variability between runs because the hash of the classes
     * involved changes.
     */
    @SuppressWarnings("static-method")
    @Test
    @DisplayName("The lookup table is well-distributed")
    void testBinopHash() {

        PyType.TYPE();
        BinopTable binops = Representation.factory.getBinops();

        // See BinopTable constructor.
        int capacity = Math.round(
                0.5f + BinopTable.ENTRIES / BinopTable.LOAD_FACTOR);
        // See HashMap constructor
        int MASK = -1 >>> Integer.numberOfLeadingZeros(capacity - 1);
        int SIZE = MASK + 1;

        Map<Integer, List<Key>> multiples = new HashMap<>();

        for (Entry<Key, MethodHandle> e : binops.entries()) {
            Key key = e.getKey();

            // Calculate an index as in HashMap.hash()
            int h = key.hashCode();
            int hash = h ^ (h >>> 16);
            int index = MASK & hash;

            // Collect keys with that index in the table
            List<Key> keys = multiples.get(index);
            if (keys == null) {
                keys = new LinkedList<>();
                multiples.put(index, keys);
            }
            keys.add(key);
        }

        // Count how many k-fold collisions there are:
        Map<Integer, Integer> distribution = new HashMap<>();
        int maxDepth = 0;
        for (int i = 0; i < SIZE; i++) {
            List<Key> keys = multiples.get(i);
            // The number of keys hashing to index=i
            int depth = keys == null ? 0 : keys.size();
            int count = distribution.computeIfAbsent(depth, k -> 0);
            distribution.put(depth, count + 1);
            // Keep a maximum depth
            maxDepth = Math.max(maxDepth, depth);
        }

        binops.logger.atInfo()
                .setMessage("Up to {} keys share one hash")
                .addArgument(maxDepth).log();
        binops.logger.atDebug().setMessage("Hash distribution [{}]:{}")
                .addArgument(SIZE).addArgument(distribution).log();
        assertTrue(maxDepth <= 3,
                "No more than 3 hash collisions allowed.");
    }
}
