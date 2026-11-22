package com.jamescolb.launcher.ui.home

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * Focus memory for one horizontal row, kept by the screen rather than the row so it survives the
 * row being scrolled out of the vertical list and recreated.
 *
 * Moving into a row lands on the card that was last focused in it, or on its first card if the
 * row hasn't been visited yet — never on whatever card happens to line up horizontally.
 */
@Stable
class RowFocusState {
    /** The row's horizontal scroll position. */
    val listState = LazyListState()

    /** Key of the card last focused in this row, if any. */
    var lastKey: Any? = null
        private set

    private val requesters = HashMap<Any, FocusRequester>()

    fun requester(key: Any): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    fun onFocused(key: Any) {
        lastKey = key
    }
}

/**
 * Applied to the row container. When focus enters the row, redirects it to the remembered card
 * (if it is still in [keys]) or to the first key.
 */
fun Modifier.rowFocusGroup(state: RowFocusState, keys: List<Any>): Modifier = this
    .focusProperties {
        onEnter = {
            val target = state.lastKey?.takeIf { it in keys } ?: keys.firstOrNull()
            if (target != null) state.requester(target).requestFocus()
        }
    }
    .focusGroup()

/** Applied to each focusable card in the row. */
fun Modifier.rowFocusItem(state: RowFocusState, key: Any): Modifier = this
    .focusRequester(state.requester(key))
    .onFocusChanged { if (it.isFocused) state.onFocused(key) }
