package com.ndizigiye.loop.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.time.Duration
import java.util.Date

/**
 * Issues and verifies HS256 tokens carrying the user id in the `uid` claim.
 *
 * The signing key combines the configured secret with a random id stored in the database,
 * so wiping the data directory invalidates old tokens instead of letting them address
 * whichever new account reuses the same id.
 */
class TokenService(secret: String, databaseKey: String) {
    private val algorithm = Algorithm.HMAC256("$secret:$databaseKey")

    val verifier: JWTVerifier = JWT.require(algorithm)
        .withIssuer(ISSUER)
        .withAudience(AUDIENCE)
        .withClaimPresence(UID_CLAIM)
        .build()

    fun issue(userId: Long, now: Long = System.currentTimeMillis()): String =
        JWT.create()
            .withIssuer(ISSUER)
            .withAudience(AUDIENCE)
            .withClaim(UID_CLAIM, userId)
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + VALIDITY.toMillis()))
            .sign(algorithm)

    companion object {
        const val UID_CLAIM = "uid"
        const val ISSUER = "loop-server"
        const val AUDIENCE = "loop-app"
        val VALIDITY: Duration = Duration.ofDays(30)
    }
}
