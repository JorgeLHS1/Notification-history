package br.com.lugedevelopment.notificationhistory.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import br.com.lugedevelopment.notificationhistory.ingest.SearchHit
import kotlinx.coroutines.launch
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import br.com.lugedevelopment.notificationhistory.R
import br.com.lugedevelopment.notificationhistory.data.AppEntity
import br.com.lugedevelopment.notificationhistory.data.MessageEntity
import br.com.lugedevelopment.notificationhistory.ingest.ConversationSummary
import br.com.lugedevelopment.notificationhistory.settings.RetentionSettings

// --- Shared helpers -------------------------------------------------------------------

private fun formatTime(millis: Long, now: Long = System.currentTimeMillis()): String {
    val flags = if (DateUtils.isToday(millis)) {
        DateUtils.FORMAT_SHOW_TIME
    } else {
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_NUMERIC_DATE or DateUtils.FORMAT_ABBREV_ALL
    }
    return DateUtils.formatDateTime(null, millis, flags)
}

/** App icon from the package manager; falls back to a letter avatar if the icon is unavailable. */
@Composable
private fun AppIcon(packageName: String, label: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) {
        runCatching { context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap() }
            .getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.clip(CircleShape),
        )
    } else {
        Box(
            modifier = modifier
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label.trim().take(1).uppercase(),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

@Composable
private fun DeletedBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = stringResource(R.string.deleted_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

// --- Home -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenConversation: (id: Long, title: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMessage: (conversationId: Long, title: String, messageId: Long, query: String) -> Unit = { id, title, _, _ ->
        onOpenConversation(id, title)
    },
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    var confirmDelete by remember { mutableStateOf(false) }
    val appLabels = remember(state.apps) { state.apps.associate { it.packageName to it.label } }
    val selecting = state.selection.isNotEmpty()
    BackHandler(enabled = selecting) { viewModel.clearSelection() }
    BackHandler(enabled = !selecting && state.searchOpen) { viewModel.closeSearch() }
    Scaffold(
        topBar = {
            when {
                selecting -> SelectionTopBar(state.selection.size, onCancel = viewModel::clearSelection)
                state.searchOpen -> SearchTopBar(state.query, viewModel::setQuery, onClose = viewModel::closeSearch)
                else -> CenterAlignedTopAppBar(
                    title = { Text(stringResource(R.string.home_title)) },
                    actions = {
                        TextButton(onClick = viewModel::openSearch) { Text(stringResource(R.string.search_action)) }
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.settings_title)) }
                    },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionActionBar(
                    onMarkRead = { scope.launch { viewModel.markSelectedRead(true) } },
                    onMarkUnread = { scope.launch { viewModel.markSelectedRead(false) } },
                    onPin = { scope.launch { viewModel.pinSelected(true) } },
                    onUnpin = { scope.launch { viewModel.pinSelected(false) } },
                    onDelete = { confirmDelete = true },
                )
            }
        },
    ) { padding ->
        if (confirmDelete) {
            ConfirmDeleteDialog(
                count = state.selection.size,
                onConfirm = {
                    confirmDelete = false
                    scope.launch { viewModel.deleteSelected() }
                },
                onDismiss = { confirmDelete = false },
            )
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            FilterChips(
                apps = state.apps,
                selected = state.selected,
                onToggle = viewModel::toggleApp,
                onClear = viewModel::clearFilter,
                deletedOnly = if (state.searchOpen) state.deletedOnly else null,
                onDeletedOnlyChange = viewModel::setDeletedOnly,
            )
            when {
                !state.loaded -> Box(Modifier.fillMaxSize())
                state.searchOpen -> SearchResults(
                    state = state,
                    appLabels = appLabels,
                    onClick = { hit ->
                        if (selecting) {
                            viewModel.toggleSelected(hit.message.id)
                        } else {
                            onOpenMessage(hit.conversation.id, hit.conversation.title, hit.message.id, state.query)
                        }
                    },
                    onLongClick = { viewModel.toggleSelected(it.message.id) },
                )
                state.conversations.isEmpty() -> EmptyMessage(stringResource(R.string.home_empty))
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(state.conversations, key = { it.conversation.id }) { row ->
                        ConversationRow(
                            row = row,
                            appLabel = appLabels[row.conversation.packageName] ?: row.conversation.packageName,
                            onClick = { onOpenConversation(row.conversation.id, row.conversation.title) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: HomeState,
    appLabels: Map<String, String>,
    onClick: (SearchHit) -> Unit,
    onLongClick: (SearchHit) -> Unit,
) {
    when {
        !state.searching -> EmptyMessage(stringResource(R.string.search_prompt))
        state.hits.isEmpty() -> EmptyMessage(stringResource(R.string.search_no_results))
        else -> {
            val highlight = MaterialTheme.colorScheme.tertiaryContainer
            LazyColumn(Modifier.fillMaxSize()) {
                items(state.hits, key = { it.message.id }) { hit ->
                    val message = hit.message
                    val selectedRow = message.id in state.selection
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (selectedRow) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                            .combinedClickable(onClick = { onClick(hit) }, onLongClick = { onLongClick(hit) })
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val pkg = hit.conversation.packageName
                        AppIcon(pkg, appLabels[pkg] ?: pkg, Modifier.size(40.dp))
                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = hit.conversation.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (message.deleted) DeletedBadge()
                                if (message.pinned) PinnedBadge()
                            }
                            Text(
                                text = highlighted(message.text, state.query, highlight),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = formatTime(message.postedAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun FilterChips(
    apps: List<AppEntity>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onClear: () -> Unit,
    /** Null hides the chip (outside search mode). */
    deletedOnly: Boolean? = null,
    onDeletedOnlyChange: (Boolean) -> Unit = {},
) {
    if (apps.isEmpty() && deletedOnly == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (deletedOnly != null) {
            FilterChip(
                selected = deletedOnly,
                onClick = { onDeletedOnlyChange(!deletedOnly) },
                label = { Text(stringResource(R.string.filter_deleted_only)) },
            )
        }
        FilterChip(
            selected = selected.isEmpty(),
            onClick = onClear,
            label = { Text(stringResource(R.string.filter_all)) },
        )
        apps.forEach { app ->
            FilterChip(
                selected = app.packageName in selected,
                onClick = { onToggle(app.packageName) },
                label = { Text(app.label) },
            )
        }
    }
}

@Composable
private fun ConversationRow(row: ConversationSummary, appLabel: String, onClick: () -> Unit) {
    val conversation = row.conversation
    val last = row.lastMessage
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppIcon(conversation.packageName, appLabel, Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = conversation.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (conversation.unreadCount > 0) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (last != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (last.deleted) DeletedBadge()
                    Text(
                        text = last.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = formatTime(conversation.lastActivityAt),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Conversation ---------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationScreen(
    viewModel: ConversationViewModel,
    title: String,
    onBack: () -> Unit,
    /** Message to scroll to and emphasize when the screen opens from a search result. */
    focusMessageId: Long? = null,
    /** Search words highlighted inside the messages. */
    query: String = "",
) {
    val state by viewModel.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf(false) }
    var focusHandled by rememberSaveable(focusMessageId) { mutableStateOf(false) }
    val selecting = state.selection.isNotEmpty()
    BackHandler(enabled = selecting) { viewModel.clearSelection() }

    LaunchedEffect(state.loaded, focusMessageId) {
        if (state.loaded && focusMessageId != null && !focusHandled) {
            val index = state.messages.indexOfFirst { it.id == focusMessageId }
            if (index >= 0) listState.scrollToItem(index)
            focusHandled = true
        }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                SelectionTopBar(state.selection.size, onCancel = viewModel::clearSelection)
            } else {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
                )
            }
        },
        bottomBar = {
            if (selecting) {
                SelectionActionBar(
                    onMarkRead = { scope.launch { viewModel.markSelectedRead(true) } },
                    onMarkUnread = { scope.launch { viewModel.markSelectedRead(false) } },
                    onPin = { scope.launch { viewModel.pinSelected(true) } },
                    onUnpin = { scope.launch { viewModel.pinSelected(false) } },
                    onDelete = { confirmDelete = true },
                )
            }
        },
    ) { padding ->
        if (confirmDelete) {
            ConfirmDeleteDialog(
                count = state.selection.size,
                onConfirm = {
                    confirmDelete = false
                    scope.launch { viewModel.deleteSelected() }
                },
                onDismiss = { confirmDelete = false },
            )
        }
        when {
            !state.loaded -> Box(Modifier.padding(padding).fillMaxSize())
            state.messages.isEmpty() -> EmptyMessage(stringResource(R.string.conversation_empty), Modifier.padding(padding))
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.messages, key = { it.id }) { message ->
                    MessageBubble(
                        message = message,
                        query = query,
                        selected = message.id in state.selection,
                        focused = message.id == focusMessageId,
                        onClick = { if (selecting) viewModel.toggleSelected(message.id) },
                        onLongClick = { viewModel.toggleSelected(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PinnedBadge() {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = stringResource(R.string.pinned_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MessageBubble(
    message: MessageEntity,
    query: String,
    selected: Boolean,
    focused: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (focused) it.border(2.dp, MaterialTheme.colorScheme.tertiary, CardDefaults.shape) else it }
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = when {
                selected -> MaterialTheme.colorScheme.secondaryContainer
                message.deleted -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = message.sender ?: stringResource(R.string.unknown_sender),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (message.pinned) PinnedBadge()
                if (message.deleted) DeletedBadge()
            }
            Text(
                text = highlighted(message.text, query, MaterialTheme.colorScheme.tertiaryContainer),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (message.read) FontWeight.Normal else FontWeight.Bold,
            )
            Text(
                text = formatTime(message.postedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// --- Settings -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onToggleCapture: (AppEntity, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back)) } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            item { SectionTitle(stringResource(R.string.settings_retention)) }
            item {
                RetentionPicker(
                    label = stringResource(R.string.retention_general),
                    value = state.generalDays,
                    onSelect = viewModel::setGeneralDays,
                )
            }
            item {
                RetentionPicker(
                    label = stringResource(R.string.retention_deleted),
                    value = state.deletedDays,
                    onSelect = viewModel::setDeletedDays,
                )
            }
            item {
                Text(
                    stringResource(R.string.retention_pinned_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item { SectionTitle(stringResource(R.string.settings_capture)) }
            if (state.apps.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.settings_apps_empty),
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(state.apps, key = { it.packageName }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AppIcon(app.packageName, app.label, Modifier.size(40.dp))
                    Text(app.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Switch(checked = app.captureEnabled, onCheckedChange = { onToggleCapture(app, it) })
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun RetentionPicker(label: String, value: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetentionSettings.OPTIONS.forEach { days ->
                FilterChip(
                    selected = value == days,
                    onClick = { onSelect(days) },
                    label = {
                        Text(
                            if (days == RetentionSettings.KEEP_FOREVER) {
                                stringResource(R.string.retention_forever)
                            } else {
                                stringResource(R.string.retention_days, days)
                            },
                        )
                    },
                )
            }
        }
    }
}
