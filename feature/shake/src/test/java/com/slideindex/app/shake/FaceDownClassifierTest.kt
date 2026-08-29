package com.slideindex.app.shake

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FaceDownClassifierTest {
    @Test
    fun isFaceDownFlat_detectsScreenDownOnTable() {
        assertTrue(FaceDownClassifier.isFaceDownFlat(0.2f, -0.3f, -9.6f))
    }

    @Test
    fun isFaceDownFlat_rejectsScreenUp() {
        assertFalse(FaceDownClassifier.isFaceDownFlat(0.2f, -0.3f, 9.6f))
    }

    @Test
    fun isFaceDownFlat_rejectsTiltedPhone() {
        assertFalse(FaceDownClassifier.isFaceDownFlat(5f, 0.2f, 8.5f))
    }

    @Test
    fun isAccelerometerStill_detectsStableSample() {
        assertTrue(
            FaceDownClassifier.isAccelerometerStill(
                previousAx = 0.1f,
                previousAy = 0.2f,
                previousAz = 9.7f,
                ax = 0.15f,
                ay = 0.18f,
                az = 9.72f,
            ),
        )
    }

    @Test
    fun isAccelerometerStill_rejectsLargeDelta() {
        assertFalse(
            FaceDownClassifier.isAccelerometerStill(
                previousAx = 0.1f,
                previousAy = 0.2f,
                previousAz = 9.7f,
                ax = 1.5f,
                ay = 0.2f,
                az = 9.7f,
            ),
        )
    }

    @Test
    fun isProximityNear_usesFractionOfMaxRange() {
        assertTrue(FaceDownClassifier.isProximityNear(0f, 5f))
        assertFalse(FaceDownClassifier.isProximityNear(4f, 5f))
    }

    @Test
    fun isNonFaceDown_detectsScreenUpOrHeldInHand() {
        // Screen up flat on table
        assertTrue(FaceDownClassifier.isNonFaceDown(0f, 0f, 9.8f))
        // Held upright portrait
        assertTrue(FaceDownClassifier.isNonFaceDown(0f, 9.8f, 0f))
        // Held landscape
        assertTrue(FaceDownClassifier.isNonFaceDown(9.8f, 0f, 0f))
        // Held tilted at 45 degrees
        assertTrue(FaceDownClassifier.isNonFaceDown(0f, 7.0f, 7.0f))
    }

    @Test
    fun isNonFaceDown_rejectsFaceDownRestingPosture() {
        // Face down flat on table
        assertFalse(FaceDownClassifier.isNonFaceDown(0f, 0f, -9.8f))
        assertFalse(FaceDownClassifier.isNonFaceDown(0.2f, -0.3f, -9.6f))
        // Face down slightly tilted on soft surface (< 20 degrees)
        assertFalse(FaceDownClassifier.isNonFaceDown(0.5f, 1.5f, -9.6f))
    }
}
