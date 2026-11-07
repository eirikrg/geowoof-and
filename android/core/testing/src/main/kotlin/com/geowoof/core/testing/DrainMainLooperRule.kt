package com.geowoof.core.testing

import android.os.Looper
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric empties the main looper between tests, and AndroidUiDispatcher, which is one for the whole JVM,
 * believes for ever that what it had just posted is on its way: it runs nothing else, and LazyPagingItems needs it.
 * The screen stays in Loading, in about half of the runs, depending on how the previous test ended. It goes before
 * the compose rule: `@get:Rule(order = -2)`.
 */
class DrainMainLooperRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            try {
                base.evaluate()
            } finally {
                shadowOf(Looper.getMainLooper()).idle()
            }
        }
    }
}
