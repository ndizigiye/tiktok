package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ndizigiye.loop.ui.theme.LoopColors

private val FieldHeight = 44.dp
private val FieldShape = RoundedCornerShape(12.dp)

/** The Discover tab's search field: looks like a text field, opens the search screen. */
@Composable
internal fun SearchFieldButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .height(FieldHeight)
            .clip(FieldShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "Search", role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchIcon()
        Spacer(Modifier.width(10.dp))
        Text(text = "Search", style = MaterialTheme.typography.bodyLarge, color = LoopColors.TextMuted, maxLines = 1)
    }
}

/** Single-line search input with a placeholder, IME "search" action and a clear button. */
@Composable
internal fun SearchInputField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(FieldHeight)
                    .clip(FieldShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(start = 14.dp, end = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchIcon()
                Spacer(Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = "Search",
                            style = MaterialTheme.typography.bodyLarge,
                            color = LoopColors.TextMuted,
                            maxLines = 1,
                        )
                    }
                    innerTextField()
                }
                if (value.text.isNotEmpty()) {
                    IconButton(onClick = onClear, modifier = Modifier.size(40.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Cancel,
                            contentDescription = "Clear search",
                            tint = LoopColors.TextMuted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        },
    )
}

@Composable
private fun SearchIcon() {
    Icon(
        imageVector = Icons.Outlined.Search,
        contentDescription = null,
        tint = LoopColors.TextMuted,
        modifier = Modifier.size(20.dp),
    )
}
