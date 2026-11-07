package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntitySamplingScopeTest {
    @Test
    void nestingDoesNotLeakIntoNormalWorldWork() {
        assertFalse(EntitySamplingScope.rejectEntityJoin());
        long before = EntitySamplingScope.blockedCount();
        try (var first = EntitySamplingScope.enter()) {
            assertTrue(EntitySamplingScope.rejectEntityJoin());
            try (var second = EntitySamplingScope.enter()) {
                assertTrue(EntitySamplingScope.rejectEntityJoin());
            }
            assertTrue(EntitySamplingScope.rejectEntityJoin());
        }
        assertFalse(EntitySamplingScope.rejectEntityJoin());
        assertEquals(before + 3, EntitySamplingScope.blockedCount());
    }

    @Test
    void anotherThreadRemainsUnaffected() throws Exception {
        var result = new java.util.concurrent.atomic.AtomicBoolean();
        try (var scope = EntitySamplingScope.enter()) {
            var thread = new Thread(() -> result.set(EntitySamplingScope.rejectEntityJoin()));
            thread.start();
            thread.join();
            assertFalse(result.get());
            assertTrue(EntitySamplingScope.rejectEntityJoin());
        }
    }

    @Test
    void exceptionRestoresScope() {
        assertThrows(IllegalArgumentException.class, () -> {
            try (var scope = EntitySamplingScope.enter()) {
                throw new IllegalArgumentException();
            }
        });
        assertFalse(EntitySamplingScope.rejectEntityJoin());
    }

    @Test
    void onlyPermittedRootCanJoinAndNestedScopeRestoresPermission() {
        Object root = new Object(), child = new Object();
        try (var outer = EntitySamplingScope.enterPermittingEntity(root)) {
            assertFalse(EntitySamplingScope.rejectEntityJoin(root));
            assertTrue(EntitySamplingScope.rejectEntityJoin(child));
            try (var nested = EntitySamplingScope.enter()) {
                assertTrue(EntitySamplingScope.rejectEntityJoin(root));
            }
            assertFalse(EntitySamplingScope.rejectEntityJoin(root));
        }
        assertFalse(EntitySamplingScope.rejectEntityJoin(child));
    }
}
