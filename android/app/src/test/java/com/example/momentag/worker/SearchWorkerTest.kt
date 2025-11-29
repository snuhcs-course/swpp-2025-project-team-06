package com.example.momentag.worker

import com.example.momentag.model.TagItem
import com.example.momentag.ui.components.SearchContentElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchWorkerTest {
    private fun createTagItem(
        tagName: String,
        tagId: String = tagName.lowercase(),
    ): TagItem =
        TagItem(
            tagName = tagName,
            coverImageId = null,
            tagId = tagId,
            createdAt = null,
            updatedAt = null,
            photoCount = 0,
        )

    @Test
    fun `parseQueryToElements returns empty text element for empty query`() {
        val result = SearchWorker.parseQueryToElements("", emptyList())

        assertEquals(1, result.size)
        assertTrue(result[0] is SearchContentElement.Text)
        assertEquals("", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements parses simple text without tags`() {
        val result = SearchWorker.parseQueryToElements("simple text", emptyList())

        // Text without tags: only adds the text, no empty text at end
        assertEquals(1, result.size)
        assertTrue(result[0] is SearchContentElement.Text)
        assertEquals("simple text", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements parses query with single tag`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos from {vacation}", tags)

        assertEquals(3, result.size)

        // First: text before tag
        assertTrue(result[0] is SearchContentElement.Text)
        assertEquals("photos from ", (result[0] as SearchContentElement.Text).text)

        // Second: chip
        assertTrue(result[1] is SearchContentElement.Chip)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)

        // Third: empty text after tag
        assertTrue(result[2] is SearchContentElement.Text)
        assertEquals("", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements parses query with multiple tags`() {
        val tags =
            listOf(
                createTagItem("vacation"),
                createTagItem("beach"),
                createTagItem("summer"),
            )

        val result = SearchWorker.parseQueryToElements("photos from {vacation} at {beach} during {summer}", tags)

        assertEquals(7, result.size)

        assertEquals("photos from ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" at ", (result[2] as SearchContentElement.Text).text)
        assertEquals("beach", (result[3] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" during ", (result[4] as SearchContentElement.Text).text)
        assertEquals("summer", (result[5] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[6] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements skips tag not found in allTags`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos {vacation} {nonexistent} fun", tags)

        // Should skip {nonexistent} tag but lastIndex doesn't update, so remainingText includes it
        assertEquals(3, result.size)

        assertEquals("photos ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        // After {vacation}, remaining text is " {nonexistent} fun"
        assertEquals(" {nonexistent} fun", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles query starting with tag`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("{vacation} photos", tags)

        assertEquals(2, result.size)

        assertTrue(result[0] is SearchContentElement.Chip)
        assertEquals("vacation", (result[0] as SearchContentElement.Chip).tag.tagName)

        assertTrue(result[1] is SearchContentElement.Text)
        assertEquals(" photos", (result[1] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles query ending with tag`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos from {vacation}", tags)

        assertEquals(3, result.size)

        assertEquals("photos from ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        // Should add empty text at end when ending with chip
        assertEquals("", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles only tag in query`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("{vacation}", tags)

        assertEquals(2, result.size)

        assertTrue(result[0] is SearchContentElement.Chip)
        assertEquals("vacation", (result[0] as SearchContentElement.Chip).tag.tagName)

        // Should add empty text after chip
        assertTrue(result[1] is SearchContentElement.Text)
        assertEquals("", (result[1] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles adjacent tags`() {
        val tags =
            listOf(
                createTagItem("vacation"),
                createTagItem("beach"),
            )

        val result = SearchWorker.parseQueryToElements("{vacation}{beach}", tags)

        // Adjacent tags: Chip, Text(""), Chip, Text("")
        assertEquals(4, result.size)

        assertTrue(result[0] is SearchContentElement.Chip)
        assertEquals("vacation", (result[0] as SearchContentElement.Chip).tag.tagName)

        // Between adjacent tags, adds empty text element (line 29-31 in SearchWorker)
        assertTrue(result[1] is SearchContentElement.Text)
        assertEquals("", (result[1] as SearchContentElement.Text).text)

        assertTrue(result[2] is SearchContentElement.Chip)
        assertEquals("beach", (result[2] as SearchContentElement.Chip).tag.tagName)

        // Ends with chip, so adds empty text
        assertTrue(result[3] is SearchContentElement.Text)
        assertEquals("", (result[3] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles case-insensitive tag matching`() {
        val tags =
            listOf(
                createTagItem("Vacation"),
                createTagItem("BEACH"),
            )

        val result = SearchWorker.parseQueryToElements("photos {vacation} {beach}", tags)

        assertEquals(5, result.size)

        assertEquals("photos ", (result[0] as SearchContentElement.Text).text)
        // Should match "vacation" to "Vacation" case-insensitively
        assertEquals("Vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" ", (result[2] as SearchContentElement.Text).text)
        // Should match "beach" to "BEACH" case-insensitively
        assertEquals("BEACH", (result[3] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[4] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles multiple spaces between words`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos   from   {vacation}", tags)

        assertEquals(3, result.size)

        // Preserves spaces (no normalization)
        assertEquals("photos   from   ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles tag with spaces in name`() {
        val tags = listOf(createTagItem("summer vacation"))

        val result = SearchWorker.parseQueryToElements("photos from {summer vacation}", tags)

        assertEquals(3, result.size)

        assertEquals("photos from ", (result[0] as SearchContentElement.Text).text)
        assertEquals("summer vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles unclosed brace`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos from {vacation", tags)

        // Unclosed brace is not recognized as tag
        assertEquals(1, result.size)
        assertTrue(result[0] is SearchContentElement.Text)
        assertEquals("photos from {vacation", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles empty braces`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos {} from {vacation}", tags)

        // Empty braces {} should match tag with empty name if it exists, otherwise skip
        // Since there's no tag with empty name, it should be skipped
        assertEquals(3, result.size)
        assertEquals("photos {} from ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles nested braces`() {
        val tags = listOf(createTagItem("vacation"))

        // Regex \{([^{}]+)\} does not match "{vacation" because of the following "{".
        // It matches "{nested}" but "nested" is not in tags, so it is skipped.
        val result = SearchWorker.parseQueryToElements("photos {vacation{nested}}", tags)

        assertEquals(1, result.size)
        assertEquals("photos {vacation{nested}}", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles special characters in text`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos! @#\$% {vacation} &*()", tags)

        assertEquals(3, result.size)
        assertEquals("photos! @#\$% ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" &*()", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles multiple tags not in allTags`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos {unknown1} {vacation} {unknown2}", tags)

        // {unknown1} is skipped (lastIndex not updated), so adds "photos " before {vacation}
        // Then adds {vacation} chip
        // Then remaining text includes " {unknown2}"
        assertEquals(3, result.size)
        assertEquals("photos {unknown1} ", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" {unknown2}", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles query with only unknown tags`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("{unknown}", tags)

        // No matching tags, textBefore is empty, no elements yet, so nothing added before tag
        // Tag doesn't match, so skipped
        // remainingText = entire query since lastIndex = 0
        // elements is empty, so line 54 adds empty text
        // elements is still empty after that, and query is not empty, so line 59 adds full query
        // Wait, let me trace through this more carefully:
        // 1. Match found for {unknown} at positions 0-8
        // 2. textBefore = query.substring(0, 0) = ""
        // 3. textBefore.isNotEmpty() is false
        // 4. elements.isNotEmpty() is false, so line 29-31 doesn't execute
        // 5. tagItem == null, so return@forEach (skip)
        // 6. lastIndex still = 0
        // 7. After forEach: remainingText = query.substring(0) = "{unknown}"
        // 8. remainingText.isNotEmpty() is true, so add Text("{unknown}")
        // 9. elements.last() is Text, not Chip, so line 54-56 doesn't add empty text
        // Result: [Text("{unknown}")] - 1 element
        assertEquals(1, result.size)
        assertEquals("{unknown}", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements generates unique IDs for each element`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("text {vacation} more", tags)

        val ids = result.map { it.id }
        // All IDs should be unique
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `parseQueryToElements handles whitespace-only query`() {
        val result = SearchWorker.parseQueryToElements("   ", emptyList())

        // No tags found, remainingText = "   ", so adds Text("   ")
        // Last element is Text (not Chip), so no empty text added
        assertEquals(1, result.size)
        assertEquals("   ", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles query with newlines`() {
        val tags = listOf(createTagItem("vacation"))

        val result = SearchWorker.parseQueryToElements("photos\n{vacation}\nbeach", tags)

        assertEquals(3, result.size)
        assertEquals("photos\n", (result[0] as SearchContentElement.Text).text)
        assertEquals("vacation", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals("\nbeach", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements preserves tag order`() {
        val tags =
            listOf(
                createTagItem("first"),
                createTagItem("second"),
                createTagItem("third"),
            )

        val result = SearchWorker.parseQueryToElements("{second} {first} {third}", tags)

        // Should preserve order from query, not from allTags list
        assertEquals(6, result.size)
        assertEquals("second", (result[0] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" ", (result[1] as SearchContentElement.Text).text)
        assertEquals("first", (result[2] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" ", (result[3] as SearchContentElement.Text).text)
        assertEquals("third", (result[4] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[5] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles double braces`() {
        val tags = listOf(createTagItem("tag"))

        // {{tag}} -> "{" + {tag} + "}"
        val result = SearchWorker.parseQueryToElements("{{tag}}", tags)

        assertEquals(3, result.size)
        assertEquals("{", (result[0] as SearchContentElement.Text).text)
        assertEquals("tag", (result[1] as SearchContentElement.Chip).tag.tagName)
        assertEquals("}", (result[2] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles regex special characters in tag name`() {
        val tags = listOf(createTagItem("tag.name"), createTagItem("tag*name"))

        val result = SearchWorker.parseQueryToElements("{tag.name} {tag*name}", tags)

        assertEquals(4, result.size)
        assertEquals("tag.name", (result[0] as SearchContentElement.Chip).tag.tagName)
        assertEquals(" ", (result[1] as SearchContentElement.Text).text)
        assertEquals("tag*name", (result[2] as SearchContentElement.Chip).tag.tagName)
        assertEquals("", (result[3] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements handles tag name with opening brace`() {
        val tags = listOf(createTagItem("{tag"))

        // {{tag} -> With regex \{([^{}]+)\}, this does NOT match "{tag" because { is not allowed in tag name.
        val result = SearchWorker.parseQueryToElements("{{tag}", tags)

        assertEquals(1, result.size)
        assertEquals("{{tag}", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements does not match tag with closing brace`() {
        val tags = listOf(createTagItem("tag}name"))

        // Regex \{([^}]+)\} explicitly excludes } from the tag name.
        val result = SearchWorker.parseQueryToElements("{tag}name}", tags)

        assertEquals(1, result.size)
        assertEquals("{tag}name}", (result[0] as SearchContentElement.Text).text)
    }

    @Test
    fun `parseQueryToElements picks first matching tag when duplicates exist in list`() {
        val tag1 = createTagItem("duplicate", "id1")
        val tag2 = createTagItem("duplicate", "id2")
        val tags = listOf(tag1, tag2)

        val result = SearchWorker.parseQueryToElements("{duplicate}", tags)

        assertEquals(2, result.size)
        val chip = result[0] as SearchContentElement.Chip
        assertEquals("duplicate", chip.tag.tagName)
        assertEquals("id1", chip.tag.tagId)
    }
}
