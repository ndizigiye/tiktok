package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.common.EmptyBox
import com.ndizigiye.loop.ui.theme.LoopColors

private const val HINT_TITLE = "Find creators, hashtags and videos"
private const val HINT_SUBTITLE = "Search by name, @username, #hashtag or words from a caption."

/** What the search screen shows for a blank query: recent searches and a hint. */
@Composable
internal fun RecentSearches(
    recents: List<String>,
    onSelect: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize()) {
        if (recents.isEmpty()) {
            item(key = "hint") {
                EmptyBox(
                    title = HINT_TITLE,
                    subtitle = HINT_SUBTITLE,
                    icon = Icons.Outlined.Search,
                    modifier = Modifier.fillParentMaxSize(),
                )
            }
        } else {
            item(key = "recents-header", contentType = "header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 4.dp, top = 8.dp)
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Recent searches",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onClearAll) { Text("Clear all") }
                }
            }
            items(items = recents, key = { "recent:$it" }, contentType = { "recent" }) { text ->
                RecentSearchRow(text = text, onClick = { onSelect(text) }, onRemove = { onRemove(text) })
            }
            item(key = "hint", contentType = "hint") {
                Text(
                    text = HINT_SUBTITLE,
                    style = MaterialTheme.typography.bodySmall,
                    color = LoopColors.TextMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentSearchRow(text: String, onClick: () -> Unit, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.History,
            contentDescription = null,
            tint = LoopColors.TextMuted,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove \"$text\" from recent searches",
                tint = LoopColors.TextMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
