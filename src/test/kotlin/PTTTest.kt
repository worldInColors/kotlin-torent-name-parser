package ptt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PTTTest {
    
    @Test
    fun testBasicParsing() {
        val result = parse("The.Movie.2023.1080p.BluRay.x264-GROUP")
        
        assertNotNull(result)
        assertEquals("The Movie", result.title)
        assertEquals("2023", result.year)
        assertEquals("1080p", result.resolution)
        assertEquals("BluRay", result.quality)
        assertEquals("x264", result.codec)
    }
    
    @Test
    fun testNormalization() {
        val result = parse("Movie.2023.1080p.AC3").normalized()
        
        assertEquals(listOf("DD"), result.audio)
    }
    
    @Test
    fun testTitleCleaning() {
        val cleanedTitle = cleanTitle("The.Movie.2023.1080p")
        assertEquals("The Movie 2023 1080p", cleanedTitle)
    }
    
    @Test
    fun testVersion() {
        val v = version()
        assertEquals("0.11.1", v.toString())
        assertEquals(11001, v.toInt())
    }
}
