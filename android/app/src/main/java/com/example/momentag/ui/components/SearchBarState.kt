package com.example.momentag.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.momentag.model.TagItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * SearchBarState
 *
 * Manages UI state for the chip-based search bar.
 * Handles text input, chip creation, focus management, and search content parsing.
 * Separated from SearchViewModel to keep business logic separate from UI state.
 */
@Stable
class SearchBarState(
    private val coroutineScope: CoroutineScope,
) {
    // UI state
    val textStates = mutableStateMapOf<String, TextFieldValue>()
    val contentItems = mutableStateListOf<SearchContentElement>()
    val focusRequesters = mutableStateMapOf<String, FocusRequester>()
    val bringIntoViewRequesters = mutableStateMapOf<String, BringIntoViewRequester>()

    private val _focusedElementId = mutableStateOf<String?>(null)
    val focusedElementId: androidx.compose.runtime.State<String?> = _focusedElementId

    private val _ignoreFocusLoss = mutableStateOf(false)
    val ignoreFocusLoss: androidx.compose.runtime.State<Boolean> = _ignoreFocusLoss

    private val _requestFocus = MutableSharedFlow<String>()
    val requestFocus: SharedFlow<String> = _requestFocus.asSharedFlow()

    private val _bringIntoView = MutableSharedFlow<String>()
    val bringIntoView: SharedFlow<String> = _bringIntoView.asSharedFlow()

    init {
        initializeEmptySearchBar()
    }

    private fun initializeEmptySearchBar() {
        if (contentItems.isEmpty()) {
            val initialId = UUID.randomUUID().toString()
            contentItems.add(SearchContentElement.Text(id = initialId, text = ""))
            textStates[initialId] = TextFieldValue("\u200B", TextRange(1))
            focusRequesters[initialId] = FocusRequester()
            bringIntoViewRequesters[initialId] = BringIntoViewRequester()
        }
    }

    fun onFocus(id: String?) {
        if (id == null && _ignoreFocusLoss.value) {
            return
        }
        _focusedElementId.value = id
    }

    fun onContainerClick() {
        val lastTextElement =
            contentItems.lastOrNull { it is SearchContentElement.Text }
        if (lastTextElement != null) {
            val currentTfv = textStates[lastTextElement.id]
            if (currentTfv != null) {
                val end = currentTfv.text.length
                textStates[lastTextElement.id] =
                    currentTfv.copy(selection = TextRange(end))
            }
            _focusedElementId.value = lastTextElement.id

            coroutineScope.launch {
                _requestFocus.emit(lastTextElement.id)
            }
        }
    }

    private fun findNextTextElementId(startIndex: Int): String? {
        if (startIndex >= contentItems.size - 1) return null
        for (i in (startIndex + 1) until contentItems.size) {
            val item = contentItems.getOrNull(i)
            if (item is SearchContentElement.Text) {
                return item.id
            }
        }
        return null
    }

    fun onChipClick(index: Int) {
        val nextTextId = findNextTextElementId(index)
        if (nextTextId != null) {
            val currentTfv = textStates[nextTextId]
            if (currentTfv != null) {
                textStates[nextTextId] =
                    currentTfv.copy(selection = TextRange(1))
            }
            _focusedElementId.value = nextTextId

            coroutineScope.launch {
                _requestFocus.emit(nextTextId)
            }
        }
    }

    fun onTextChange(
        id: String,
        newValue: TextFieldValue,
    ) {
        coroutineScope.launch {
            _bringIntoView.emit(id)
        }

        val oldValue = textStates[id] ?: TextFieldValue()
        val oldText = oldValue.text
        val newText = newValue.text

        // Check if IME is composing (e.g., Korean input)
        val isComposing = newValue.composition != null

        if (isComposing) {
            textStates[id] = newValue
            return
        }

        // Detect if ZWSP was deleted (backspace at start)
        val didBackspaceAtStart =
            oldText.startsWith("\u200B") &&
                !newText.startsWith("\u200B") &&
                oldValue.selection.start == 1

        if (didBackspaceAtStart) {
            _ignoreFocusLoss.value = true
            val currentIndex = contentItems.indexOfFirst { it.id == id }

            if (currentIndex == -1) {
                return
            }

            val currentItem =
                contentItems[currentIndex] as SearchContentElement.Text

            if (currentIndex <= 0) {
                textStates[id] = TextFieldValue("\u200B", TextRange(1))
                return
            }

            val prevItem = contentItems[currentIndex - 1]

            // Previous item is a chip
            if (prevItem is SearchContentElement.Chip) {
                val prevPrevIndex = currentIndex - 2

                // [TextA] [ChipB] [TextC] -> [TextA + TextC]
                if (prevPrevIndex >= 0 && contentItems[prevPrevIndex] is SearchContentElement.Text) {
                    val textA =
                        contentItems[prevPrevIndex] as SearchContentElement.Text
                    val textC = currentItem
                    val mergedText = textA.text + textC.text

                    val chipIdToRemove = contentItems[currentIndex - 1].id
                    val textIdToRemove = id

                    contentItems.removeAt(currentIndex)
                    contentItems.removeAt(currentIndex - 1)
                    textStates.remove(id)

                    focusRequesters.remove(chipIdToRemove)
                    bringIntoViewRequesters.remove(chipIdToRemove)
                    focusRequesters.remove(textIdToRemove)
                    bringIntoViewRequesters.remove(textIdToRemove)

                    contentItems[prevPrevIndex] = textA.copy(text = mergedText)
                    val newTfv =
                        TextFieldValue(
                            "\u200B" + mergedText,
                            TextRange(textA.text.length + 1),
                        )
                    textStates[textA.id] = newTfv

                    coroutineScope.launch {
                        _requestFocus.emit(textA.id)
                    }
                } else {
                    // [ChipA] [ChipB] [TextC] -> [ChipA] [TextC]
                    val chipIdToRemove = contentItems[currentIndex - 1].id

                    contentItems.removeAt(currentIndex - 1)

                    focusRequesters.remove(chipIdToRemove)
                    bringIntoViewRequesters.remove(chipIdToRemove)

                    coroutineScope.launch {
                        _requestFocus.emit(id)
                    }
                }
            } else if (prevItem is SearchContentElement.Text) {
                // Previous item is text: [TextA] [TextC(current)]
                val textA = prevItem
                val textC = currentItem
                val mergedText = textA.text + textC.text

                contentItems.removeAt(currentIndex)
                textStates.remove(id)

                focusRequesters.remove(id)
                bringIntoViewRequesters.remove(id)

                contentItems[currentIndex - 1] = textA.copy(text = mergedText)
                val newTfv =
                    TextFieldValue(
                        "\u200B" + mergedText,
                        TextRange(textA.text.length + 1),
                    )
                textStates[textA.id] = newTfv

                coroutineScope.launch {
                    _requestFocus.emit(textA.id)
                }
            }
            return
        }

        // Ensure ZWSP prefix and cursor position
        val (text, selection) =
            if (newText.startsWith("\u200B")) {
                Pair(newText, newValue.selection)
            } else {
                Pair(
                    "\u200B$newText",
                    TextRange(
                        newValue.selection.start + 1,
                        newValue.selection.end + 1,
                    ),
                )
            }

        val finalSelection =
            if (selection.start == 0 && selection.end == 0) {
                TextRange(1)
            } else {
                selection
            }
        val finalValue = TextFieldValue(text, finalSelection)

        textStates[id] = finalValue
        val currentItemIndex = contentItems.indexOfFirst { it.id == id }
        if (currentItemIndex != -1) {
            contentItems[currentItemIndex] =
                (contentItems[currentItemIndex] as SearchContentElement.Text).copy(
                    text = text.removePrefix("\u200B"),
                )
        }
    }

    fun addTagFromSuggestion(tag: TagItem) {
        _ignoreFocusLoss.value = true

        if (focusedElementId.value == null) return

        val currentId = focusedElementId.value ?: return

        val currentIndex = contentItems.indexOfFirst { it.id == currentId }
        val currentInput = textStates[currentId] ?: return

        val text = currentInput.text
        val cursor = currentInput.selection.start
        val textUpToCursor = text.substring(0, cursor)
        val lastHashIndex = textUpToCursor.lastIndexOf('#')

        if (lastHashIndex != -1) {
            // Split text
            val precedingText = text.substring(0, lastHashIndex).removePrefix("\u200B")
            val succeedingText = text.substring(cursor)

            // Create new chip and text field
            val newChipId = UUID.randomUUID().toString()
            val newChip = SearchContentElement.Chip(newChipId, tag)

            val newTextId = UUID.randomUUID().toString()
            val newText = SearchContentElement.Text(newTextId, succeedingText)

            // Update current text field
            contentItems[currentIndex] =
                (contentItems[currentIndex] as SearchContentElement.Text).copy(text = precedingText)
            textStates[currentId] =
                TextFieldValue("\u200B" + precedingText, TextRange(precedingText.length + 1))

            // Insert new chip and text field
            contentItems.add(currentIndex + 1, newChip)
            contentItems.add(currentIndex + 2, newText)

            focusRequesters[newChip.id] = FocusRequester()
            bringIntoViewRequesters[newChip.id] = BringIntoViewRequester()
            focusRequesters[newTextId] = FocusRequester()
            bringIntoViewRequesters[newTextId] = BringIntoViewRequester()

            // Set new text field state (cursor at 1)
            textStates[newTextId] = TextFieldValue("\u200B" + succeedingText, TextRange(1))

            // Change focus state
            _focusedElementId.value = newTextId

            // Emit focus request
            coroutineScope.launch {
                _requestFocus.emit(newTextId)
            }
        }
    }

    fun buildSearchQuery(): String =
        contentItems
            .joinToString(separator = "") {
                when (it) {
                    is SearchContentElement.Text -> it.text
                    is SearchContentElement.Chip -> "{${it.tag.tagName}}"
                }
            }.trim()
            .replace(Regex("\\s+"), " ")

    /**
     * Parse query string into SearchContentElements
     * @param query Search query string (e.g., "text {tag1} other text")
     * @param allTags List of available tags to match against
     * @return Parsed SearchContentElement list
     */
    fun parseQueryToElements(
        query: String,
        allTags: List<TagItem>,
    ): List<SearchContentElement> {
        val elements = mutableListOf<SearchContentElement>()
        val tagRegex = "\\{([^}]+)\\}".toRegex()
        var lastIndex = 0

        tagRegex.findAll(query).forEach { matchResult ->
            // Add text before tag
            val textBefore = query.substring(lastIndex, matchResult.range.first)
            if (textBefore.isNotEmpty()) {
                elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = textBefore))
            }

            // Add tag chip
            val tagName = matchResult.groupValues[1]
            val tagItem = allTags.find { it.tagName.equals(tagName, ignoreCase = true) }
            if (tagItem == null) {
                return@forEach
            }

            elements.add(SearchContentElement.Chip(id = UUID.randomUUID().toString(), tag = tagItem))

            lastIndex = matchResult.range.last + 1
        }

        // Add remaining text
        val remainingText = query.substring(lastIndex)
        if (remainingText.isNotEmpty()) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = remainingText))
        }

        // Add empty text field if needed
        if (elements.isEmpty() || elements.last() is SearchContentElement.Chip) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = ""))
        }

        // If query had no tags
        if (elements.isEmpty() && query.isNotEmpty()) {
            elements.add(SearchContentElement.Text(id = UUID.randomUUID().toString(), text = query))
        }

        return elements
    }

    fun selectHistoryItem(
        query: String,
        allTags: List<TagItem>,
    ) {
        val newElements = parseQueryToElements(query, allTags)

        // Clear existing state
        contentItems.clear()
        textStates.clear()
        focusRequesters.clear()
        bringIntoViewRequesters.clear()

        // Populate with new query items
        newElements.forEach { element ->
            contentItems.add(element)

            focusRequesters[element.id] = FocusRequester()
            bringIntoViewRequesters[element.id] = BringIntoViewRequester()

            if (element is SearchContentElement.Text) {
                val tfv =
                    TextFieldValue(
                        "\u200B" + element.text,
                        TextRange(element.text.length + 1),
                    )
                textStates[element.id] = tfv
            }
        }
    }

    fun resetIgnoreFocusLossFlag() {
        _ignoreFocusLoss.value = false
    }

    /**
     * Clear all search content and reset to empty state
     * @param keepFocus true to keep focus in new text field, false to clear focus
     */
    fun clearSearchContent(keepFocus: Boolean = false) {
        contentItems.clear()
        textStates.clear()
        focusRequesters.clear()
        bringIntoViewRequesters.clear()

        val initialId = UUID.randomUUID().toString()
        contentItems.add(SearchContentElement.Text(id = initialId, text = ""))
        textStates[initialId] = TextFieldValue("\u200B", TextRange(1))
        focusRequesters[initialId] = FocusRequester()
        bringIntoViewRequesters[initialId] = BringIntoViewRequester()

        if (keepFocus) {
            _focusedElementId.value = initialId
            coroutineScope.launch {
                _requestFocus.emit(initialId)
            }
        } else {
            _focusedElementId.value = null
        }
    }
}
