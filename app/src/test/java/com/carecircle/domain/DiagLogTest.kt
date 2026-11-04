package com.carecircle.domain

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogTest {
    private fun ev(i: Int, code: DiagCode = DiagCode.STATUS_SENT) = DiagEvent(i.toLong(), code)

    @Test fun keepsAtMostFiftyEventsDroppingTheOldest() {
        var list = emptyList<DiagEvent>()
        for (i in 1..80) list = DiagBuffer.add(list, ev(i))
        assertEquals(DiagBuffer.MAX_EVENTS, list.size)
        assertEquals(31L, list.first().at); assertEquals(80L, list.last().at)
    }

    @Test fun staysUnderTheByteLimit() {
        var list = emptyList<DiagEvent>()
        val frames = List(5) { "com.example.SomeVeryLongPackageName.AndAVeryLongClassName.andAVeryLongMethodName$it".repeat(1) }
        for (i in 1..50) list = DiagBuffer.add(list, DiagEvent(i.toLong(), DiagCode.CRASH, "java.lang.IllegalStateException", frames))
        assertTrue(DiagBuffer.encode(list).toByteArray().size <= DiagBuffer.MAX_BYTES)
    }

    @Test fun anErrorIsReducedToAClassNameAndFramesToPackageAndMethod() {
        val t = IllegalStateException("Metformin 500 mg for Asha at 8:00")
        assertEquals("java.lang.IllegalStateException", DiagBuffer.simpleClassName(t))
        val frames = DiagBuffer.framesOf(t)
        assertTrue(frames.isNotEmpty() && frames.size <= DiagBuffer.MAX_FRAMES)
        for (f in frames) assertFalse("a frame carries only names: $f", f.contains(' ') || f.contains(':') || f.contains('(') || f.contains('/'))
        val joined = DiagBuffer.encode(listOf(DiagEvent(1, DiagCode.CRASH, DiagBuffer.simpleClassName(t), frames)))
        assertFalse(joined.contains("Metformin") || joined.contains("Asha"))
    }

    @Test fun freeTextIsStrippedEvenIfItGetsThroughTheTypes() {
        assertEquals("ExceptionWithMessage", DiagBuffer.clean("Exception With Message!?"))
        assertNull(DiagBuffer.clean("   "))
        assertNull(DiagBuffer.clean(null))
        assertEquals(80, DiagBuffer.clean("a".repeat(200))!!.length)
        val e = DiagBuffer.add(emptyList(), DiagEvent(1, DiagCode.CRASH, "Asha took Metformin", listOf("com.x.Y.z at secret.txt:12")))
        assertEquals("AshatookMetformin", e.single().error)
        assertEquals("com.x.Y.zatsecret.txt12", e.single().frames.single())
    }

    @Test fun roundTripAndUnknownCodesAreIgnored() {
        val list = listOf(DiagEvent(5, DiagCode.APP_OPEN), DiagEvent(6, DiagCode.CRASH, "java.lang.RuntimeException", listOf("a.B.c", "d.E.f")))
        assertEquals(list, DiagBuffer.decode(DiagBuffer.encode(list)))
        assertEquals(1, DiagBuffer.decode("7|NOT_A_CODE||\n8|APP_OPEN||").size)
        assertEquals(emptyList<DiagEvent>(), DiagBuffer.decode(""))
    }

    @Test fun theReportStatesItHoldsNoHealthTextAndListsEvents() {
        val r = DiagReport.build(listOf("App" to "1.9.0", "Firebase configured" to "no"), listOf(DiagEvent(1, DiagCode.PUSH_HELD)), { "T$it" })
        assertTrue(r.contains(DiagReport.NOTE)); assertTrue(r.contains("App: 1.9.0")); assertTrue(r.contains("T1  PUSH_HELD"))
        assertTrue(DiagReport.build(emptyList(), emptyList(), { "" }).contains("(none)"))
    }

    // ---- the guard that the log can never take free text ----

    private val sources: List<File> by lazy {
        val module = File(System.getProperty("user.dir") ?: ".")
        val root = if (File(module, "src/main").exists()) File(module, "src/main") else File(module, "app/src/main")
        root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test fun everyLogCallPassesAnEventCodeNeverAString() {
        val call = Regex("""DiagLog\.log\(([^)]*)\)""")
        var calls = 0
        for (f in sources) for (m in call.findAll(f.readText())) {
            calls++
            val args = m.groupValues[1]
            assertFalse("a string literal in a DiagLog.log call (${f.name}): $args", args.contains('"'))
            assertTrue("DiagLog.log needs a DiagCode (${f.name}): $args", args.contains("DiagCode."))
        }
        assertTrue("expected the app to log some events", calls >= 8)
    }

    @Test fun theLogApiOnlyAcceptsACodeAndAnException() {
        val src = sources.first { it.name == "DiagLog.kt" }.readText()
        assertTrue(src.contains("fun log(ctx: Context, code: DiagCode, error: Throwable? = null)"))
    }
}
