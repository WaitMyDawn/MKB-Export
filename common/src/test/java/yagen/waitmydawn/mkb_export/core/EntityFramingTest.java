package yagen.waitmydawn.mkb_export.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityFramingTest {
    @Test
    void measuresSparseWingsAndCentersActualBoundsWithMargin() {
        var b = EntityFraming.bounds(256, 256, (x, y) -> (x >= 5 && x <= 250 && y >= 100 && y <= 140) ? 255 : 0);
        assertEquals(5, b.left());
        assertEquals(251, b.right());
        var world = EntityFraming.world(b, 256, 10, 64, 64);
        var fit = EntityFraming.fit(world);
        assertEquals(12.8, world.left() * fit.scale() + fit.x(), .001);
        assertEquals(115.2, world.right() * fit.scale() + fit.x(), .001);
        assertEquals(64, (world.top() + world.bottom()) * .5 * fit.scale() + fit.y(), .001);
    }

    @Test
    void handlesOffCenterBoundsAndFlagsEdgesWithoutTreatingEmptyAsFailure() {
        assertNull(EntityFraming.bounds(128, 128, (x, y) -> 0));
        assertTrue(EntityFraming.edge(new EntityFraming.Bounds(0, 30, 60, 70), 128));
        var b = new EntityFraming.Bounds(-100, -5, 240, 20);
        var fit = EntityFraming.fit(b);
        assertTrue(fit.scale() > 0);
        assertEquals(64, (b.left() + b.right()) * .5 * fit.scale() + fit.x(), .001);
        assertTrue(b.union(new EntityFraming.Bounds(-200, -10, 300, 30)).width() > b.width());
    }

    @Test
    void finalFeedbackCorrectsTheReportedCryomancerMarginsAtBothResolutions() {
        for (int size : new int[]{256, 512}) {
            double k = size / 256.0;
            var pixels = new EntityFraming.Bounds(96 * k, 27 * k, 160 * k, 152 * k);
            assertFalse(EntityFraming.acceptable(pixels, size));
            var current = new EntityFraming.Fit(10, 64, 64);
            var world = EntityFraming.world(pixels, size, current.scale(), current.x(), current.y());
            var next = EntityFraming.refine(current, pixels, size);
            var corrected = new EntityFraming.Bounds((world.left() * next.scale() + next.x()) * size / 128, (world.top() * next.scale() + next.y()) * size / 128, (world.right() * next.scale() + next.x()) * size / 128, (world.bottom() * next.scale() + next.y()) * size / 128);
            assertTrue(EntityFraming.acceptable(corrected, size));
            assertEquals(.8, EntityFraming.occupancy(corrected, size), 1e-9);
            assertEquals(size * .5, (corrected.top() + corrected.bottom()) * .5, 1e-9);
        }
    }

    @Test
    void clippedBoundsExpandBeforeRecenteringAndInvalidCompositionIsNotFitted() {
        var fit = new EntityFraming.Fit(10, 64, 64);
        assertTrue(EntityFraming.refine(fit, new EntityFraming.Bounds(0, 20, 256, 200), 256).scale() < fit.scale());
        assertFalse(EntityFraming.acceptable(null, 256));
        assertFalse(EntityFraming.acceptable(new EntityFraming.Bounds(20, 10, 200, 215), 256));
        assertTrue(EntityFraming.acceptable(new EntityFraming.Bounds(100, 26, 156, 230), 256));
    }
}
