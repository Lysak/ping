// SPDX-License-Identifier: GPL-3.0-or-later
package com.lysak.ping.architecture

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.ext.list.withPackage
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.jupiter.api.Test

/**
 * Architecture guards — the Deptrac equivalent. See docs/tools.md §3.4.
 * Runs via `make test` / `make verify` (`-PwithArchTest`); excluded from the fast
 * `make gate` loop because the Konsist scan is single-threaded and slow.
 *
 * Layers (inner may not know outer):
 *   core  -> pure domain: ping stats, host validation, sample types. No deps.
 *   net   -> sockets / ICMP packets. May use core.
 *   data  -> persistence (DataStore).
 *   presentation -> ViewModel. May use core + net + data.
 *   ui    -> Compose. Goes through presentation, never core/net directly.
 */
class ArchitectureTest {
    // Build the Konsist scope once for the whole class, not once per @Test —
    // scope construction is the expensive part.
    private companion object {
        val production = Konsist.scopeFromProduction()
    }

    @Test
    fun coreDependsOnNothingInternalOrAndroid() {
        production.files
            .withPackage("com.lysak.ping.core..")
            .assertFalse { file ->
                file.hasImport { import ->
                    import.name.startsWith("com.lysak.ping.net") ||
                        import.name.startsWith("com.lysak.ping.data") ||
                        import.name.startsWith("com.lysak.ping.presentation") ||
                        import.name.startsWith("com.lysak.ping.ui") ||
                        import.name.startsWith("androidx.") ||
                        import.name.startsWith("android.")
                }
            }
    }

    @Test
    fun netDoesNotDependOnPresentationOrUi() {
        production.files
            .withPackage("com.lysak.ping.net..")
            .assertFalse { file ->
                file.hasImport { import ->
                    import.name.startsWith("com.lysak.ping.presentation") ||
                        import.name.startsWith("com.lysak.ping.ui") ||
                        import.name.startsWith("androidx.compose")
                }
            }
    }

    @Test
    fun composablesGoThroughPresentation() {
        // @Composable functions must not reach into ping internals directly.
        production
            .functions()
            .filter { function -> function.annotations.any { it.name == "Composable" } }
            .assertFalse { function ->
                function.text.contains(
                    Regex("\\b(Pinger|IcmpSocket|IcmpPacket|PingProbe|HostsRepository)\\b"),
                )
            }
    }

    @Test
    fun productionCodeHasNoStrayPrintln() {
        production
            .functions()
            .assertFalse { function -> function.text.contains("println(") }
    }
}
