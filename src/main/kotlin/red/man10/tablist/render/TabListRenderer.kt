package red.man10.tablist.render

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.player.TabList
import com.velocitypowered.api.proxy.player.TabListEntry
import red.man10.tablist.state.PlayerEntry
import red.man10.tablist.state.TabListState
import java.util.UUID

class TabListRenderer(
    private val server: ProxyServer,
    private val state: TabListState,
) {

    fun renderAll() {
        val snapshot = state.snapshot().toList()
        val desired = collectIds(snapshot)
        for (viewer in server.allPlayers) {
            renderFor(viewer, snapshot, desired)
        }
    }

    fun renderFor(viewer: Player) {
        val snapshot = state.snapshot().toList()
        val desired = collectIds(snapshot)
        renderFor(viewer, snapshot, desired)
    }

    private fun collectIds(snapshot: List<PlayerEntry>): Set<UUID> {
        val ids = HashSet<UUID>(snapshot.size)
        for (entry in snapshot) {
            ids.add(entry.uuid)
        }
        return ids
    }

    private fun renderFor(viewer: Player, snapshot: List<PlayerEntry>, desired: Set<UUID>) {
        val tabList = viewer.tabList

        for (entry in snapshot) {
            applyEntry(tabList, entry)
        }

        var toRemove: ArrayList<UUID>? = null
        for (existing in tabList.entries) {
            val id = existing.profile.id
            if (id !in desired) {
                if (toRemove == null) toRemove = ArrayList()
                toRemove.add(id)
            }
        }
        if (toRemove != null) {
            for (id in toRemove) {
                tabList.removeEntry(id)
            }
        }
    }

    private fun applyEntry(tabList: TabList, entry: PlayerEntry) {
        val displayName = state.displayNameFor(entry.uuid) ?: return

        val existing = tabList.getEntry(entry.uuid)
        if (existing.isPresent) {
            val tle = existing.get()
            tle.setDisplayName(displayName)
            tle.setLatency(entry.latency)
            return
        }

        val targetPlayer = server.getPlayer(entry.uuid).orElse(null) ?: return
        val tle = TabListEntry.builder()
            .tabList(tabList)
            .profile(targetPlayer.gameProfile)
            .displayName(displayName)
            .latency(entry.latency)
            .build()
        tabList.addEntry(tle)
    }
}
