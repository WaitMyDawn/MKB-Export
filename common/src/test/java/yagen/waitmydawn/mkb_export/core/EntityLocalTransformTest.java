package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityLocalTransformTest {
    @Test
    void rotatedParentPreservesChildChainInCanonicalFrame() {
        var v = EntityLocalTransform.toRoot(3, 2, 0, 90);
        assertEquals(0, v.x(), 1e-9);
        assertEquals(2, v.y(), 1e-9);
        assertEquals(-3, v.z(), 1e-9);
        var restored = EntityLocalTransform.toRoot(v.x(), v.y(), v.z(), -90);
        assertEquals(3, restored.x(), 1e-9);
        assertEquals(0, restored.z(), 1e-9);
    }

    @Test
    void nonFiniteAndUnboundedPositionsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> EntityLocalTransform.toRoot(Double.NaN, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> EntityLocalTransform.toRoot(257, 0, 0, 0));
    }
}
