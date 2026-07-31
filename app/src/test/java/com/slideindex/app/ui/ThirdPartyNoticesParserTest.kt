package com.slideindex.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThirdPartyNoticesParserTest {
    @Test
    fun `parse splits intro and sections by level-2 headings`() {
        val markdown = """
            # Third-Party Notices

            Intro paragraph.

            ## SideGesture

            - **License:** Apache-2.0

            ---

            ## Clipboard Whitelist

            - **License:** GPL-3.0
        """.trimIndent()

        val (intro, sections) = parseThirdPartyNoticeSections(markdown)

        assertTrue(intro.contains("Third-Party Notices"))
        assertTrue(intro.contains("Intro paragraph."))
        assertEquals(2, sections.size)
        assertEquals("SideGesture", sections[0].title)
        assertTrue(sections[0].bodyMarkdown.contains("Apache-2.0"))
        assertEquals("Clipboard Whitelist", sections[1].title)
        assertTrue(sections[1].bodyMarkdown.contains("GPL-3.0"))
    }
}
