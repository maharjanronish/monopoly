package com.local.monopoly.game

import java.security.SecureRandom

/**
 * Dice source. Uses java.security.SecureRandom — the phone's cryptographic
 * random generator — so rolls are as random as the device can provide
 * (not a seeded/linear PRNG like kotlin.random.Random).
 */
object Dice {
    internal val rng = SecureRandom()

    fun roll(): Pair<Int, Int> = Pair(1 + rng.nextInt(6), 1 + rng.nextInt(6))

    fun sum(d: Pair<Int, Int>): Int = d.first + d.second

    fun isDoubles(d: Pair<Int, Int>): Boolean = d.first == d.second
}
