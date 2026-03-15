package com.lemn.app.ui
// [Goose] Bridge file share events to ViewModel via dispatcher is installed in ChatScreen composition

// [Goose] Installing FileShareDispatcher handler in ChatScreen to forward file sends to ViewModel


import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.lemn.app.model.BitchatMessage
import com.lemn.app.ui.media.FullScreenImageViewer
import com.lemn.app.ui.theme.ThemePreference
import com.lemn.app.ui.theme.ThemePreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Main ChatScreen - REFACTORED to use component-based architecture
 * This is now a coordinator that orchestrates the following UI components:
 * - ChatHeader: App bar, navigation, peer counter
 * - MessageComponents: Message display and formatting
 * - InputComponents: Message input and command suggestions
 * - SidebarComponents: Navigation drawer with channels and people
 * - AboutSheet: App info and password prompts
 * - ChatUIUtils: Utility functions for formatting and colors
 */
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val colorScheme = MaterialTheme.colorScheme
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val connectedPeers by viewModel.connectedPeers.collectAsStateWithLifecycle()
    val nickname by viewModel.nickname.collectAsStateWithLifecycle()
    val selectedPrivatePeer by viewModel.selectedPrivateChatPeer.collectAsStateWithLifecycle()
    val currentChannel by viewModel.currentChannel.collectAsStateWithLifecycle()
    val joinedChannels by viewModel.joinedChannels.collectAsStateWithLifecycle()
    val hasUnreadChannels by viewModel.unreadChannelMessages.collectAsStateWithLifecycle()
    val hasUnreadPrivateMessages by viewModel.unreadPrivateMessages.collectAsStateWithLifecycle()
    val privateChats by viewModel.privateChats.collectAsStateWithLifecycle()
    val channelMessages by viewModel.channelMessages.collectAsStateWithLifecycle()
    val peerNicknames by viewModel.peerNicknames.collectAsStateWithLifecycle()
    val favoritePeers by viewModel.favoritePeers.collectAsStateWithLifecycle()
    val peerFingerprints by viewModel.peerFingerprints.collectAsStateWithLifecycle()
    val showCommandSuggestions by viewModel.showCommandSuggestions.collectAsStateWithLifecycle()
    val commandSuggestions by viewModel.commandSuggestions.collectAsStateWithLifecycle()
    val showMentionSuggestions by viewModel.showMentionSuggestions.collectAsStateWithLifecycle()
    val mentionSuggestions by viewModel.mentionSuggestions.collectAsStateWithLifecycle()
    val showAppInfo by viewModel.showAppInfo.collectAsStateWithLifecycle()
    val showMeshPeerListSheet by viewModel.showMeshPeerList.collectAsStateWithLifecycle()
    val privateChatSheetPeer by viewModel.privateChatSheetPeer.collectAsStateWithLifecycle()
    val showVerificationSheet by viewModel.showVerificationSheet.collectAsStateWithLifecycle()
    val showSecurityVerificationSheet by viewModel.showSecurityVerificationSheet.collectAsStateWithLifecycle()

    val ignoredContactRequests = remember { mutableStateOf<Set<String>>(emptySet()) }
    val starredYouPeerIds = remember(messages, peerNicknames) {
        messages.mapNotNull { msg ->
            val name = extractFavoritedYouName(msg) ?: return@mapNotNull null
            peerNicknames.entries.firstOrNull { it.value == name }?.key
        }.toSet()
    }
    val pendingContactRequest = remember(messages, peerNicknames, ignoredContactRequests.value) {
        messages
            .filter { extractFavoritedYouName(it) != null }
            .sortedByDescending { it.timestamp }
            .mapNotNull { msg ->
                val name = extractFavoritedYouName(msg) ?: return@mapNotNull null
                val peerId = peerNicknames.entries.firstOrNull { it.value == name }?.key ?: return@mapNotNull null
                if (ignoredContactRequests.value.contains(peerId)) return@mapNotNull null
                peerId to name
            }
            .firstOrNull()
    }

    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    var showPasswordPrompt by remember { mutableStateOf(false) }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var passwordInput by remember { mutableStateOf("") }
    var showLocationChannelsSheet by remember { mutableStateOf(false) }
    var showLocationNotesSheet by remember { mutableStateOf(false) }
    var showUserSheet by remember { mutableStateOf(false) }
    var selectedUserForSheet by remember { mutableStateOf("") }
    var selectedMessageForSheet by remember { mutableStateOf<BitchatMessage?>(null) }
    var showFullScreenImageViewer by remember { mutableStateOf(false) }
    var viewerImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var initialViewerIndex by remember { mutableStateOf(0) }
    var forceScrollToBottom by remember { mutableStateOf(false) }
    var isScrolledUp by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) } // 0 = Chat, 1 = Emergency, 2 = Profile
    var showWipeConfirm by remember { mutableStateOf(false) }

    // Show password dialog when needed
    LaunchedEffect(showPasswordPrompt) {
        showPasswordDialog = showPasswordPrompt
    }

    val isConnected by viewModel.isConnected.collectAsStateWithLifecycle()
    val passwordPromptChannel by viewModel.passwordPromptChannel.collectAsStateWithLifecycle()
    val isDark = colorScheme.background.luminance() < 0.5f
    val emergencyScheme = remember(isDark) { com.lemn.app.ui.theme.emergencyColorScheme(isDark) }

    // Get location channel info for timeline switching
    val selectedLocationChannel by viewModel.selectedLocationChannel.collectAsStateWithLifecycle()

    // Determine what messages to show based on current context (unified timelines)
    // Legacy private chat timeline removed - private chats now exclusively use PrivateChatSheet
    val displayMessages = when {
        selectedTab == 1 -> messages // Emergency tab shows public mesh timeline (SOS feed)
        currentChannel != null -> channelMessages[currentChannel] ?: emptyList()
        else -> {
            val locationChannel = selectedLocationChannel
            if (locationChannel is com.lemn.app.geohash.ChannelID.Location) {
                val geokey = "geo:${locationChannel.channel.geohash}"
                channelMessages[geokey] ?: emptyList()
            } else {
                messages // Mesh timeline
            }
        }
    }

    // Determine whether to show media buttons (only hide in geohash location chats)
    val showMediaButtons = when {
        currentChannel != null -> true
        else -> selectedLocationChannel !is com.lemn.app.geohash.ChannelID.Location
    }
    
    // Emergency tab safeguard: force channel to public mesh
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            viewModel.switchToChannel(null)
        }
    }

    // Use WindowInsets to handle keyboard properly
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background) // Extend background to fill entire screen including status bar
    ) {
        val headerHeight = 42.dp
        
        // Main content area that responds to keyboard/window insets
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.ime) // This handles keyboard insets
                .windowInsetsPadding(WindowInsets.navigationBars) // Add bottom padding when keyboard is not expanded
        ) {
            // Header spacer - creates exact space for the floating header (status bar + compact header)
            Spacer(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(headerHeight)
            )

            // Tab Row: Chat / Emergency / Profile
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = colorScheme.background,
                contentColor = colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Chat") }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Emergency") }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("Profile") }
                )
            }

            // Messages / Content area - takes up available space, will compress when keyboard appears
            when (selectedTab) {
                0 -> {
                    // Chat tab: private chat only (contacts list)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // DEBUG UI REMOVED


                        val incomingRequestIds = starredYouPeerIds
                            .filter { peerId -> !ignoredContactRequests.value.contains(peerId) }

                        if (incomingRequestIds.isNotEmpty()) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = colorScheme.surfaceVariant.copy(alpha = 0.2f),
                                shape = MaterialTheme.shapes.medium,
                                border = BorderStroke(1.dp, colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "Incoming Contact Requests",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = colorScheme.onSurface
                                    )
                                    incomingRequestIds.forEach { peerId ->
                                        val displayName = peerNicknames[peerId] ?: peerId.take(12)
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = displayName,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Wants to add you as a contact",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurface.copy(alpha = 0.7f)
                                            )
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        viewModel.toggleFavorite(peerId)
                                                        ignoredContactRequests.value =
                                                            ignoredContactRequests.value + peerId
                                                    }
                                                ) {
                                                    Text("Accept")
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        ignoredContactRequests.value =
                                                            ignoredContactRequests.value + peerId
                                                    }
                                                ) {
                                                    Text("Ignore")
                                                }
                                            }
                                        }
                                        HorizontalDivider(
                                            color = colorScheme.outline.copy(alpha = 0.2f)
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Contacts",
                                style = MaterialTheme.typography.titleMedium,
                                color = colorScheme.onSurface
                            )
                            IconButton(
                                onClick = { viewModel.showMeshPeerList() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = "Discover",
                                    tint = colorScheme.secondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        val contactPeerIds = connectedPeers.filter { peerId ->
                            val fp = peerFingerprints[peerId]
                            fp != null && favoritePeers.contains(fp) && starredYouPeerIds.contains(peerId)
                        }.sorted()

                        if (contactPeerIds.isEmpty()) {
                            Text(
                                text = "No contacts yet. Discover nearby users.",
                                color = colorScheme.onSurface.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            contactPeerIds.forEach { peerID ->
                                val displayName = peerNicknames[peerID] ?: peerID.take(12)
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.showPrivateChatSheet(peerID) },
                                    color = colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Text(
                                        text = displayName,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                        color = colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // Emergency tab: dedicated SOS interface
                    var showSosConfirm by remember { mutableStateOf(false) }
                    var sosCooldown by remember { mutableStateOf(false) }
                    val emergencySnackbarHostState = remember { SnackbarHostState() }
                    val emergencyScope = rememberCoroutineScope()
                    val emergencyContext = androidx.compose.ui.platform.LocalContext.current
                    val sosMessages = remember(messages) {
                        messages
                            .filter { it.content.contains("SOS \uD83D\uDEA8") }
                            .sortedByDescending { it.timestamp }
                    }
                    val emergencyTimeFormatter = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
                    MaterialTheme(
                        colorScheme = emergencyScheme,
                        typography = MaterialTheme.typography
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!showSosConfirm) {
                                    Button(
                                        onClick = { showSosConfirm = true },
                                        enabled = !sosCooldown,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = emergencyScheme.primary,
                                            contentColor = emergencyScheme.onPrimary
                                        ),
                                        modifier = Modifier
                                            .height(72.dp)
                                            .widthIn(min = 200.dp)
                                    ) {
                                        Text(
                                            text = "SOS",
                                            style = MaterialTheme.typography.headlineSmall
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Surface(
                                            shape = MaterialTheme.shapes.large,
                                            color = emergencyScheme.primary,
                                            modifier = Modifier
                                                .height(64.dp)
                                                .widthIn(min = 240.dp)
                                                .pointerInput(Unit) {
                                                    detectTapGestures(
                                                        onLongPress = {
                                                            if (sosCooldown) return@detectTapGestures
                                                            fetchLastLocation(emergencyContext) { result ->
                                                                val sosMessage = when (result) {
                                                                    is LocationFetchResult.Success ->
                                                                        "SOS \uD83D\uDEA8 I need help at ${result.latitude}, ${result.longitude}"
                                                                    LocationFetchResult.PermissionDenied,
                                                                    LocationFetchResult.Unavailable ->
                                                                        "SOS \uD83D\uDEA8 I need help. Location unavailable."
                                                                }
                                                                // Force global/public send path before SOS send.
                                                                viewModel.endPrivateChat()
                                                                viewModel.switchToChannel(null)
                                                                viewModel.sendMessage(sosMessage)
                                                                emergencyScope.launch {
                                                                    emergencySnackbarHostState.showSnackbar("SOS broadcast sent")
                                                                    sosCooldown = true
                                                                    delay(3000)
                                                                    sosCooldown = false
                                                                }
                                                            }
                                                            showSosConfirm = false
                                                        }
                                                    )
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "Hold to Send SOS",
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = emergencyScheme.onPrimary
                                                )
                                            }
                                        }
                                        TextButton(onClick = { showSosConfirm = false }) {
                                            Text(
                                                text = "Cancel",
                                                color = emergencyScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = emergencyScheme.outline.copy(alpha = 0.3f)
                            )
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(sosMessages) { message ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = emergencyScheme.surfaceVariant.copy(alpha = 0.35f),
                                        shape = MaterialTheme.shapes.medium
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Text(
                                                text = message.sender,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = emergencyScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = message.content,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = emergencyScheme.onSurface
                                            )
                                            Text(
                                                text = emergencyTimeFormatter.format(message.timestamp),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = emergencyScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                }
                            }
                            SnackbarHost(
                                hostState = emergencySnackbarHostState,
                                modifier = Modifier
                                    .padding(16.dp)
                            )
                        }
                    }
                }
                else -> {
                    // Profile tab: username + QR/verification access
                    // Profile tab: 4-card settings layout
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 1. Username Card
                        ProfileUsernameCard(
                            nickname = nickname,
                            onNicknameChange = { viewModel.setNickname(it) }
                        )

                        // 2. Verification Card
                        ProfileVerificationCard(
                            onClick = { viewModel.showVerificationSheet() }
                        )

                        // 3. Wipe Data Card
                        ProfileWipeDataCard(
                            onClick = { showWipeConfirm = true }
                        )

                        // 4. Appearance Card
                        val currentTheme by ThemePreferenceManager.themeFlow.collectAsStateWithLifecycle()
                        val context = androidx.compose.ui.platform.LocalContext.current
                        ProfileAppearanceCard(
                            currentTheme = currentTheme,
                            onThemeSelected = { preference -> ThemePreferenceManager.set(context = context, preference) }
                        )
                    }
                }
            }
            // Input area - stays at bottom
        // Bridge file share from lower-level input to ViewModel
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.lemn.app.ui.events.FileShareDispatcher.setHandler { peer, channel, path ->
            viewModel.sendFileNote(peer, channel, path)
        }
    }

    if (selectedTab == 0) {
        // Chat tab input only when a private chat sheet is open
        // (PrivateChatSheet provides its own input; keep this hidden here)
    }
        }

        // Floating header - positioned absolutely at top, ignores keyboard
        ChatFloatingHeader(
            headerHeight = headerHeight,
            selectedPrivatePeer = null,
            currentChannel = currentChannel,
            nickname = nickname,
            viewModel = viewModel,
            colorScheme = colorScheme,
            onSidebarToggle = { },
            onLocationChannelsClick = { },
            onLocationNotesClick = { }
        )

        // Divider under header - positioned after status bar + header height
        HorizontalDivider(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .offset(y = headerHeight)
                .zIndex(1f),
            color = colorScheme.outline.copy(alpha = 0.3f)
        )

        // Scroll-to-bottom floating button
        val scrollColors = if (selectedTab == 1) emergencyScheme else colorScheme
        AnimatedVisibility(
            visible = isScrolledUp,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 64.dp)
                .zIndex(1.5f)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .windowInsetsPadding(WindowInsets.ime)
        ) {
            Surface(
                shape = CircleShape,
                color = scrollColors.background,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
                border = BorderStroke(2.dp, scrollColors.secondary)
            ) {
                IconButton(onClick = { forceScrollToBottom = !forceScrollToBottom }) {
                    Icon(
                        imageVector = Icons.Filled.ArrowDownward,
                        contentDescription = stringResource(com.lemn.app.R.string.cd_scroll_to_bottom),
                        tint = scrollColors.secondary
                    )
                }
            }
        }
    }

    // Full-screen image viewer - separate from other sheets to allow image browsing without navigation
    if (showFullScreenImageViewer) {
        FullScreenImageViewer(
            imagePaths = viewerImagePaths,
            initialIndex = initialViewerIndex,
            onClose = { showFullScreenImageViewer = false }
        )
    }

    // Dialogs and Sheets
    ChatDialogs(
        showPasswordDialog = showPasswordDialog,
        passwordPromptChannel = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = { passwordInput = it },
        onPasswordConfirm = {
            if (passwordInput.isNotEmpty()) {
                val success = viewModel.joinChannel(passwordPromptChannel!!, passwordInput)
                if (success) {
                    showPasswordDialog = false
                    passwordInput = ""
                }
            }
        },
        onPasswordDismiss = {
            showPasswordDialog = false
            passwordInput = ""
        },
        showAppInfo = showAppInfo,
        onAppInfoDismiss = { viewModel.hideAppInfo() },
        showLocationChannelsSheet = showLocationChannelsSheet,
        onLocationChannelsSheetDismiss = { showLocationChannelsSheet = false },
        showLocationNotesSheet = showLocationNotesSheet,
        onLocationNotesSheetDismiss = { showLocationNotesSheet = false },
        showUserSheet = showUserSheet,
        onUserSheetDismiss = { 
            showUserSheet = false
            selectedMessageForSheet = null // Reset message when dismissing
        },
        selectedUserForSheet = selectedUserForSheet,
        selectedMessageForSheet = selectedMessageForSheet,
        viewModel = viewModel,
        showVerificationSheet = showVerificationSheet,
        onVerificationSheetDismiss = viewModel::hideVerificationSheet,
        showSecurityVerificationSheet = showSecurityVerificationSheet,
        onSecurityVerificationSheetDismiss = viewModel::hideSecurityVerificationSheet,
        showMeshPeerListSheet = showMeshPeerListSheet,
        onMeshPeerListDismiss = viewModel::hideMeshPeerList,
        showDiscoveryOnly = selectedTab == 0,
        contactPeerIds = connectedPeers.filter { peerId ->
            val fp = peerFingerprints[peerId]
            fp != null && favoritePeers.contains(fp) && starredYouPeerIds.contains(peerId)
        }.toSet(),
        peerStarredYouIds = starredYouPeerIds
    )

    if (selectedTab != 1) {
        if (showWipeConfirm) {
            AlertDialog(
                onDismissRequest = { showWipeConfirm = false },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showWipeConfirm = false
                            viewModel.panicClearAllData()
                        }
                    ) {
                        Text("Confirm")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWipeConfirm = false }) {
                        Text("Cancel")
                    }
                },
                title = { Text("Wipe All Data") },
                text = { Text("This will erase all local data.") }
            )
        }
    }
}

@Composable
private fun ContactRequestDialog(
    peerId: String,
    peerName: String,
    onAdd: () -> Unit,
    onIgnore: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onIgnore,
        title = { Text(text = "Contact Request") },
        text = { Text(text = "$peerName wants to add you as a contact") },
        confirmButton = {
            TextButton(onClick = onAdd) { Text("Add Contact") }
        },
        dismissButton = {
            TextButton(onClick = onIgnore) { Text("Ignore") }
        }
    )
}

private fun extractFavoritedYouName(message: com.lemn.app.model.BitchatMessage): String? {
    if (message.sender != "system") return null
    val marker = " favorited you"
    val idx = message.content.indexOf(marker)
    if (idx <= 0) return null
    return message.content.substring(0, idx).trim().takeIf { it.isNotEmpty() }
}

@Composable
fun ChatInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSend: () -> Unit,
    onSendVoiceNote: (String?, String?, String) -> Unit,
    onSendImageNote: (String?, String?, String) -> Unit,
    onSendFileNote: (String?, String?, String) -> Unit,
    showCommandSuggestions: Boolean,
    commandSuggestions: List<CommandSuggestion>,
    showMentionSuggestions: Boolean,
    mentionSuggestions: List<String>,
    onCommandSuggestionClick: (CommandSuggestion) -> Unit,
    onMentionSuggestionClick: (String) -> Unit,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    colorScheme: ColorScheme,
    showMediaButtons: Boolean,
    showLocationButton: Boolean,
    onShareLocation: (() -> Unit)?
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colorScheme.background
    ) {
        Column {
            HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.3f))
            // Command suggestions box
            if (showCommandSuggestions && commandSuggestions.isNotEmpty()) {
                CommandSuggestionsBox(
                    suggestions = commandSuggestions,
                    onSuggestionClick = onCommandSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            // Mention suggestions box
            if (showMentionSuggestions && mentionSuggestions.isNotEmpty()) {
                MentionSuggestionsBox(
                    suggestions = mentionSuggestions,
                    onSuggestionClick = onMentionSuggestionClick,
                    modifier = Modifier.fillMaxWidth()
                )
                HorizontalDivider(color = colorScheme.outline.copy(alpha = 0.2f))
            }
            MessageInput(
                value = messageText,
                onValueChange = onMessageTextChange,
                onSend = onSend,
                onSendVoiceNote = onSendVoiceNote,
                onSendImageNote = onSendImageNote,
                onSendFileNote = onSendFileNote,
                selectedPrivatePeer = selectedPrivatePeer,
                currentChannel = currentChannel,
                nickname = nickname,
                showMediaButtons = showMediaButtons,
                showLocationButton = showLocationButton,
                onShareLocation = onShareLocation,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatFloatingHeader(
    headerHeight: Dp,
    selectedPrivatePeer: String?,
    currentChannel: String?,
    nickname: String,
    viewModel: ChatViewModel,
    colorScheme: ColorScheme,
    onSidebarToggle: () -> Unit,
    onLocationChannelsClick: () -> Unit,
    onLocationNotesClick: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val locationManager = remember { com.lemn.app.geohash.LocationChannelManager.getInstance(context) }
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(1f)
            .windowInsetsPadding(WindowInsets.statusBars), // Extend into status bar area
        color = colorScheme.background // Solid background color extending into status bar
    ) {
        TopAppBar(
            title = {
                ChatHeaderContent(
                    selectedPrivatePeer = selectedPrivatePeer,
                    currentChannel = currentChannel,
                    viewModel = viewModel,
                    onBackClick = {
                        when {
                            selectedPrivatePeer != null -> viewModel.endPrivateChat()
                            currentChannel != null -> viewModel.switchToChannel(null)
                        }
                    },
                    onSidebarClick = onSidebarToggle,
                    onLocationChannelsClick = onLocationChannelsClick,
                    onLocationNotesClick = {
                        // Ensure location is loaded before showing sheet
                        locationManager.refreshChannels()
                        onLocationNotesClick()
                    }
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent
            ),
            modifier = Modifier.height(headerHeight) // Ensure compact header height
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatDialogs(
    showPasswordDialog: Boolean,
    passwordPromptChannel: String?,
    passwordInput: String,
    onPasswordChange: (String) -> Unit,
    onPasswordConfirm: () -> Unit,
    onPasswordDismiss: () -> Unit,
    showAppInfo: Boolean,
    onAppInfoDismiss: () -> Unit,
    showLocationChannelsSheet: Boolean,
    onLocationChannelsSheetDismiss: () -> Unit,
    showLocationNotesSheet: Boolean,
    onLocationNotesSheetDismiss: () -> Unit,
    showUserSheet: Boolean,
    onUserSheetDismiss: () -> Unit,
    selectedUserForSheet: String,
    selectedMessageForSheet: BitchatMessage?,
    viewModel: ChatViewModel,
    showVerificationSheet: Boolean,
    onVerificationSheetDismiss: () -> Unit,
    showSecurityVerificationSheet: Boolean,
    onSecurityVerificationSheetDismiss: () -> Unit,
    showMeshPeerListSheet: Boolean,
    onMeshPeerListDismiss: () -> Unit,
    showDiscoveryOnly: Boolean,
    contactPeerIds: Set<String>,
    peerStarredYouIds: Set<String>,
) {
    val privateChatSheetPeer by viewModel.privateChatSheetPeer.collectAsStateWithLifecycle()

    // Password dialog
    PasswordPromptDialog(
        show = showPasswordDialog,
        channelName = passwordPromptChannel,
        passwordInput = passwordInput,
        onPasswordChange = onPasswordChange,
        onConfirm = onPasswordConfirm,
        onDismiss = onPasswordDismiss
    )

    // About sheet
    var showDebugSheet by remember { mutableStateOf(false) }
    AboutSheet(
        isPresented = showAppInfo,
        onDismiss = onAppInfoDismiss,
        onShowDebug = { showDebugSheet = true }
    )
    if (showDebugSheet) {
        // Debug UI disabled for build stability.
        showDebugSheet = false
    }
    
    // Location channels sheet
    if (showLocationChannelsSheet) {
        LocationChannelsSheet(
            isPresented = showLocationChannelsSheet,
            onDismiss = onLocationChannelsSheetDismiss,
            viewModel = viewModel
        )
    }
    
    // Location notes sheet (extracted to separate presenter)
    if (showLocationNotesSheet) {
        LocationNotesSheetPresenter(
            viewModel = viewModel,
            onDismiss = onLocationNotesSheetDismiss
        )
    }
    
    // User action sheet
    if (showUserSheet) {
        ChatUserSheet(
            isPresented = showUserSheet,
            onDismiss = onUserSheetDismiss,
            targetNickname = selectedUserForSheet,
            selectedMessage = selectedMessageForSheet,
            viewModel = viewModel
        )
    }
    // MeshPeerList sheet (network view)
    if (showMeshPeerListSheet){
        MeshPeerListSheet(
            isPresented = showMeshPeerListSheet,
            viewModel = viewModel,
            onDismiss = onMeshPeerListDismiss,
            onShowVerification = {
                onMeshPeerListDismiss()
                viewModel.showVerificationSheet(fromSidebar = true)
            },
            showOnlyDiscovered = showDiscoveryOnly,
            contactPeerIds = contactPeerIds,
            peerStarredYouIds = peerStarredYouIds
        )
    }

    if (showVerificationSheet) {
        VerificationSheet(
            isPresented = showVerificationSheet,
            onDismiss = onVerificationSheetDismiss,
            viewModel = viewModel
        )
    }

    if (showSecurityVerificationSheet) {
        SecurityVerificationSheet(
            isPresented = showSecurityVerificationSheet,
            onDismiss = onSecurityVerificationSheetDismiss,
            viewModel = viewModel
        )
    }

    if (privateChatSheetPeer != null) {
        PrivateChatSheet(
            isPresented = true,
            peerID = privateChatSheetPeer!!,
            viewModel = viewModel,
            onDismiss = {
                viewModel.hidePrivateChatSheet()
                viewModel.endPrivateChat()
            }
        )
    }
}

@Composable
private fun ProfileCard(
    title: String,
    modifier: Modifier = Modifier,
    colors: CardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = colors,
        border = null
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun ProfileUsernameCard(
    nickname: String,
    onNicknameChange: (String) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }
    var editedName by remember { mutableStateOf(TextFieldValue(nickname)) }

    // Sync external changes unless editing
    LaunchedEffect(nickname) {
        if (!isEditing) {
            editedName = TextFieldValue(nickname)
        }
    }

    ProfileCard(title = "Username") {
        if (isEditing) {
            OutlinedTextField(
                value = editedName,
                onValueChange = { editedName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = {
                        onNicknameChange(editedName.text)
                        isEditing = false
                    }
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                trailingIcon = {
                    IconButton(onClick = {
                        onNicknameChange(editedName.text)
                        isEditing = false
                    }) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Save")
                    }
                }
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isEditing = true }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@$nickname",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileVerificationCard(
    onClick: () -> Unit
) {
    ProfileCard(title = "Verification") {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text("Show Verification QR")
        }
    }
}

@Composable
private fun ProfileWipeDataCard(
    onClick: () -> Unit
) {
    ProfileCard(
        title = "Wipe All Data",
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        )
    ) {
        Text(
            text = "Deletes all local data from this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Text("Wipe Data")
        }
    }
}

@Composable
private fun ProfileAppearanceCard(
    currentTheme: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit
) {
    ProfileCard(title = "Appearance") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeOption(
                label = "System",
                selected = currentTheme == ThemePreference.System,
                onClick = { onThemeSelected(ThemePreference.System) }
            )
            ThemeOption(
                label = "Light",
                selected = currentTheme == ThemePreference.Light,
                onClick = { onThemeSelected(ThemePreference.Light) }
            )
            ThemeOption(
                label = "Dark",
                selected = currentTheme == ThemePreference.Dark,
                onClick = { onThemeSelected(ThemePreference.Dark) }
            )
        }
    }
}

@Composable
private fun ThemeOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = if (selected) {
            { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

