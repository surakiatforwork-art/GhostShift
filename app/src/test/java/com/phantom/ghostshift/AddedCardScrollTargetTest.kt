package com.phantom.ghostshift

import com.phantom.ghostshift.domain.AddedCardScrollTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AddedCardScrollTargetTest {
    @Test fun insideZoneAndFullyVisibleDoesNotMove() {
        for (center in listOf(200, 400, 600)) {
            assertEquals(0f, AddedCardScrollTarget.distance(center - 100, 200, 0, 1000), 0f)
        }
    }
    @Test fun lowerCardMovesTowardFortyPercent() {
        assertEquals(500f, AddedCardScrollTarget.distance(800, 200, 0, 1000), 0f)
    }
    @Test fun upperCardMovesDownTowardFortyPercent() {
        assertEquals(-300f, AddedCardScrollTarget.distance(0, 200, 0, 1000), 0f)
    }
    @Test fun inFlightDestinationDoesNotStopAtZoneBoundary() {
        assertEquals(150f, AddedCardScrollTarget.distance(450, 200, 0, 1000, true), 0f)
        assertEquals(0f, AddedCardScrollTarget.distance(300, 200, 0, 1000, true), 0f)
    }
    @Test fun largeCardIsFullyVisibleIfPossible() {
        assertEquals(400f, AddedCardScrollTarget.distance(400, 900, 0, 1000), 0f)
        assertEquals(0f, AddedCardScrollTarget.distance(0, 900, 0, 1000, true), 0f)
    }
    @Test fun viewportOffsetAndSizeAreRespected() {
        assertEquals(260f, AddedCardScrollTarget.distance(660, 200, 100, 1100), 0f)
        assertEquals(0f, AddedCardScrollTarget.distance(200, 200, 100, 1100), 0f)
        assertTrue(AddedCardScrollTarget.tailPadding(1000, 76) >= 600)
        assertEquals(76, AddedCardScrollTarget.tailPadding(100, 76))
    }
}
