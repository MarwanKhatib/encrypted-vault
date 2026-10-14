package com.example.encryptedvault.security

/** Face-down detection from the accelerometer z axis. Gravity reads about -9.8 m/s² when the screen points at the floor. */
object FaceDown {
    /** About two thirds of gravity: a phone tilted towards the floor locks, a phone held upright does not. */
    const val THRESHOLD = -6.5f

    /** True when the screen points mostly down. */
    fun isFaceDown(z: Float): Boolean = z < THRESHOLD
}
