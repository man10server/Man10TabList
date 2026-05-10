package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TabListState(
    private val formatter: TabListFormatter,
) {

    private val entries = ConcurrentHashMap<UUID, PlayerEntry>()

    fun addOrGet(uuid: UUID, username: String, serverName: String): PlayerEntry =
        entries.computeIfAbsent(uuid) { PlayerEntry(it, username, serverName) }

    fun upsert(uuid: UUID, username: String, serverName: String): PlayerEntry {
        val entry = entries.computeIfAbsent(uuid) { PlayerEntry(it, username, serverName) }
        entry.updateServerName(serverName)
        return entry
    }

    fun remove(uuid: UUID): PlayerEntry? = entries.remove(uuid)

    fun get(uuid: UUID): PlayerEntry? = entries[uuid]

    fun updateLatency(uuid: UUID, latency: Int) {
        entries[uuid]?.let { it.latency = latency }
    }

    fun displayNameFor(uuid: UUID): Component? =
        entries[uuid]?.computeDisplayName(formatter)

    fun snapshot(): Collection<PlayerEntry> = entries.values

    val size: Int get() = entries.size

    fun clear() {
        entries.clear()
    }
}
