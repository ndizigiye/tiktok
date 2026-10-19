package com.ndizigiye.loop.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ndizigiye.loop.ui.common.rememberBackAction
import com.ndizigiye.loop.ui.navigation.LocalAppContainer

/** Search for users, videos and hashtags, with recent searches for a blank query. */
@Composable
fun SearchScreen() {
    val container = LocalAppContainer.current
    val context = LocalContext.current
    val viewModel: SearchViewModel = viewModel {
        SearchViewModel(container.discoverRepository, RecentSearchStore(context.applicationContext))
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val query = viewModel.query

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    // Focus the field when the screen opens, not again when coming back to it.
    var autoFocused by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(focusRequester) {
        if (!autoFocused) {
            autoFocused = true
            focusRequester.requestFocus()
        }
    }
    val dismissKeyboard: () -> Unit = {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        SearchTopBar(
            query = query,
            onQueryChange = viewModel::onQueryChange,
            onSearch = {
                dismissKeyboard()
                viewModel.submit()
            },
            onClear = {
                viewModel.clearQuery()
                focusRequester.requestFocus()
            },
            focusRequester = focusRequester,
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            if (query.text.isBlank()) {
                RecentSearches(
                    recents = state.recents,
                    onSelect = { text ->
                        dismissKeyboard()
                        viewModel.selectRecent(text)
                    },
                    onRemove = viewModel::removeRecent,
                    onClearAll = viewModel::clearRecents,
                )
            } else {
                SearchResultsPane(
                    state = state,
                    onRetry = viewModel::retry,
                    onSelectTab = viewModel::selectTab,
                    onOpenResult = {
                        dismissKeyboard()
                        viewModel.onResultOpened()
                    },
                    onScrollStarted = dismissKeyboard,
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = rememberBackAction()) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        SearchInputField(
            value = query,
            onValueChange = onQueryChange,
            onSearch = onSearch,
            onClear = onClear,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
        )
    }
}
