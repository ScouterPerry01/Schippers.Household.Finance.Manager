package ca.schippers.hfm.desktop

import kotlin.test.Test
import kotlin.test.assertEquals

/** DOC-01: turning pages and zooming in the document viewer. */
class PageZoomTest {

    @Test
    fun `pages turn within the document`() {
        assertEquals(1, PageZoom.turn(0, 1, 2))
        assertEquals(1, PageZoom.turn(1, 1, 2), "no page after the last")
        assertEquals(0, PageZoom.turn(0, -1, 2), "no page before the first")
        assertEquals(0, PageZoom.turn(0, 1, 1), "an image is one page")
        assertEquals(0, PageZoom.turn(3, 1, 0), "nothing to show")
    }

    @Test
    fun `zoom goes by steps, from half the width to five times, and back to fit`() {
        assertEquals(1.25f, PageZoom.zoomIn(PageZoom.FIT))
        assertEquals(0.75f, PageZoom.zoomOut(PageZoom.FIT))
        assertEquals(PageZoom.MAX, PageZoom.zoomIn(PageZoom.MAX))
        assertEquals(PageZoom.MIN, PageZoom.zoomOut(PageZoom.MIN))
        // From a zoom between two steps, the next step either way.
        assertEquals(2f, PageZoom.zoomIn(1.7f))
        assertEquals(1.5f, PageZoom.zoomOut(1.7f))
        var z = PageZoom.FIT
        repeat(20) { z = PageZoom.zoomIn(z) }
        assertEquals(5f, z)
    }
}
