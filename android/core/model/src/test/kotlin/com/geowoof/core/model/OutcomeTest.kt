package com.geowoof.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OutcomeTest {
    @Test
    fun map_transforms_only_successes() {
        val failure: Outcome<Int> = Outcome.Failure(AppError.Network)

        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test
    fun api_errors_expose_their_code() {
        assertEquals("auth.invalid_credentials", AppError.Api(401, "auth.invalid_credentials").code)
        assertNull(AppError.Network.code)
    }
}
