package com.lemn.app

import android.app.Application
import com.lemn.app.nostr.RelayDirectory
import com.lemn.app.ui.theme.ThemePreferenceManager
import com.lemn.app.net.ArtiTorManager

/**
 * Main application class for bitchat Android
 */
class BitchatApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize Tor first so any early network goes over Tor
        try {
            val torProvider = ArtiTorManager.getInstance()
            torProvider.init(this)
        } catch (_: Exception){}

        // Initialize relay directory (loads assets/nostr_relays.csv)
        RelayDirectory.initialize(this)

        // Initialize LocationNotesManager dependencies early so sheet subscriptions can start immediately
        try { com.lemn.app.nostr.LocationNotesInitializer.initialize(this) } catch (_: Exception) { }

        // Initialize favorites persistence early so MessageRouter/NostrTransport can use it on startup
        try {
            com.lemn.app.favorites.FavoritesPersistenceService.initialize(this)
        } catch (_: Exception) { }

        // Warm up Nostr identity to ensure npub is available for favorite notifications
        try {
            com.lemn.app.nostr.NostrIdentityBridge.getCurrentNostrIdentity(this)
        } catch (_: Exception) { }

        // Initialize theme preference
        ThemePreferenceManager.init(this)

        // Initialize debug preference manager (persists debug toggles)
        try { com.lemn.app.ui.debug.DebugPreferenceManager.init(this) } catch (_: Exception) { }

        // Initialize Geohash Registries for persistence
        try {
            com.lemn.app.nostr.GeohashAliasRegistry.initialize(this)
            com.lemn.app.nostr.GeohashConversationRegistry.initialize(this)
        } catch (_: Exception) { }

        // Initialize mesh service preferences
        try { com.lemn.app.service.MeshServicePreferences.init(this) } catch (_: Exception) { }

        // Proactively start the foreground service to keep mesh alive
        try { com.lemn.app.service.MeshForegroundService.start(this) } catch (_: Exception) { }

        // TorManager already initialized above
    }
}

