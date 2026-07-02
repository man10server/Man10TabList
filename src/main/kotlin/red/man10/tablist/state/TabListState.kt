package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class TabListState(
    private val formatter: TabListFormatter,
) {

    @Volatile
    var privateServers: Set<String> = emptySet()

    private val players = ConcurrentHashMap<UUID, PlayerEntry>()
    private val serverHeaders = ConcurrentHashMap<String, ServerGroupHeader>()
    private val overflows = ConcurrentHashMap<String, OverflowSlot>()

    private val topLabels: Array<TopLabelSlot> = Array(NUM_COLUMNS) { TopLabelSlot(it) }
    private val bottomLabels: Array<BottomLabelSlot> = Array(NUM_COLUMNS) { BottomLabelSlot(it) }
    private val paddings: Array<PaddingSlot> = Array(TOTAL_CONTENT) { PaddingSlot(it) }

    fun addOrGet(uuid: UUID, username: String, serverName: String): PlayerEntry =
        players.computeIfAbsent(uuid) { PlayerEntry(it, username, serverName) }

    fun upsert(uuid: UUID, username: String, serverName: String): PlayerEntry {
        val entry = players.computeIfAbsent(uuid) { PlayerEntry(it, username, serverName) }
        entry.updateServerName(serverName)
        return entry
    }

    fun remove(uuid: UUID): PlayerEntry? = players.remove(uuid)

    fun get(uuid: UUID): PlayerEntry? = players[uuid]

    fun updateLatency(uuid: UUID, latency: Int) {
        players[uuid]?.let { it.latency = latency }
    }

    fun displayNameFor(uuid: UUID): Component? {
        val player = players[uuid]
        if (player != null) return player.computeDisplayName(formatter)
        return null
    }

    fun playerCount(): Int = players.size

    fun composedSlots(
        uncappedServer: String? = null,
        viewerId: UUID? = null,
        bypassPrivate: Boolean = false,
    ): List<TabSlot> {
        val grouped = sortedMapOf<String, MutableList<PlayerEntry>>()
        for (entry in players.values) {
            grouped.getOrPut(entry.serverName) { ArrayList() }.add(entry)
        }
        serverHeaders.keys.removeAll { it !in grouped.keys }
        overflows.keys.removeAll { it !in grouped.keys }

        val groupOrder = if (uncappedServer != null && grouped.containsKey(uncappedServer)) {
            val ordered = ArrayList<Map.Entry<String, MutableList<PlayerEntry>>>(grouped.size)
            for (e in grouped.entries) if (e.key == uncappedServer) ordered.add(e)
            for (e in grouped.entries) if (e.key != uncappedServer) ordered.add(e)
            ordered
        } else {
            grouped.entries
        }

        val content = ArrayList<TabSlot>(TOTAL_CONTENT)

        for ((server, list) in groupOrder) {
            if (!bypassPrivate && server in privateServers && server != uncappedServer) continue
            if (content.size >= TOTAL_CONTENT) break

            val nextRowStart = ((content.size + NUM_COLUMNS - 1) / NUM_COLUMNS) * NUM_COLUMNS
            while (content.size < nextRowStart && content.size < TOTAL_CONTENT) {
                content.add(paddings[content.size])
            }
            if (content.size >= TOTAL_CONTENT) break

            val available = TOTAL_CONTENT - content.size
            if (available < 1) break

            val header = serverHeaders.computeIfAbsent(server) { ServerGroupHeader(it) }
            header.count = list.size
            list.sortBy { it.username }

            val isUncapped = server == uncappedServer

            // 自サーバーは viewer 自身を先頭固定にして必ず表示枠へ入れる (sortBy の後に移動する)。
            if (isUncapped && viewerId != null) {
                val selfIndex = list.indexOfFirst { it.uuid == viewerId }
                if (selfIndex > 0) {
                    list.add(0, list.removeAt(selfIndex))
                }
            }

            content.add(header)

            // 上限セル数 (自サーバー=MAX_CELLS_FOR_OWN_SERVER / 他=MAX_CELLS_PER_SERVER) を残り枠 available で
            // 頭打ちし、header + 全員がそれを超えるなら最後の 1 セルを overflow にして必ず "... and X more" を
            // 出す (枠不足でも黙って切り捨てない)。自サーバーのあふれ分は renderFor で listed=false 退避する。
            val cap = minOf(if (isUncapped) MAX_CELLS_FOR_OWN_SERVER else MAX_CELLS_PER_SERVER, available)
            val playersToAdd: Int
            val emitOverflow: Boolean
            if (list.size + 1 > cap) {
                playersToAdd = maxOf(0, cap - 2)
                emitOverflow = true
            } else {
                playersToAdd = list.size
                emitOverflow = false
            }

            for (i in 0 until playersToAdd) {
                content.add(list[i])
            }
            if (emitOverflow) {
                val overflow = overflows.computeIfAbsent(server) { OverflowSlot(it) }
                overflow.remaining = list.size - playersToAdd
                content.add(overflow)
            }
        }

        while (content.size < TOTAL_CONTENT) {
            content.add(paddings[content.size])
        }

        val result = ArrayList<TabSlot>(TOTAL_ENTRIES)
        for (col in 0 until NUM_COLUMNS) result.add(topLabels[col])
        result.addAll(content)
        for (col in 0 until NUM_COLUMNS) result.add(bottomLabels[col])
        return result
    }

    fun clear() {
        players.clear()
        serverHeaders.clear()
        overflows.clear()
    }

    companion object {
        const val NUM_COLUMNS: Int = 4
        const val ROWS_PER_COLUMN: Int = 20
        const val CONTENT_ROWS_PER_COLUMN: Int = ROWS_PER_COLUMN - 2
        const val TOTAL_CONTENT: Int = NUM_COLUMNS * CONTENT_ROWS_PER_COLUMN
        const val TOTAL_ENTRIES: Int = NUM_COLUMNS * ROWS_PER_COLUMN
        const val MAX_ROWS_PER_SERVER: Int = 4
        const val MAX_CELLS_PER_SERVER: Int = MAX_ROWS_PER_SERVER * NUM_COLUMNS

        // 自サーバー (uncapped) に割り当てる上限。これを超えた分は overflow + listed=false 退避。
        const val MAX_ROWS_FOR_OWN_SERVER: Int = 12
        const val MAX_CELLS_FOR_OWN_SERVER: Int = MAX_ROWS_FOR_OWN_SERVER * NUM_COLUMNS
    }
}
