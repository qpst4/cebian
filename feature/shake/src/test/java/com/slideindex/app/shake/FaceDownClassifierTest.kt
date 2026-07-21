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
}
