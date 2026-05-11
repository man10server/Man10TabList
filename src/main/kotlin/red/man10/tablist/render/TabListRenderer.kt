package red.man10.tablist.render

import com.velocitypowered.api.proxy.Player
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.proxy.player.TabList
import com.velocitypowered.api.proxy.player.TabListEntry
import com.velocitypowered.api.util.GameProfile
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import red.man10.tablist.state.BottomLabelSlot
import red.man10.tablist.state.OverflowSlot
import red.man10.tablist.state.PaddingSlot
import red.man10.tablist.state.PlayerEntry
import red.man10.tablist.state.ServerGroupHeader
import red.man10.tablist.state.TabListState
import red.man10.tablist.state.TabSlot
import red.man10.tablist.state.TopLabelSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID

class TabListRenderer(
    private val server: ProxyServer,
    private val state: TabListState,
    private val formatter: TabListFormatter,
) {

    private val grayProfileProperties: List<GameProfile.Property> = listOf(
        GameProfile.Property("textures", GRAY_SKIN_VALUE, GRAY_SKIN_SIGNATURE),
    )

    private val tabHeader: Component =
        Component.text("ようこそ！ Man10サーバーへ！", NamedTextColor.RED, TextDecoration.BOLD)

    private val footerWelcomeLine: Component =
        Component.text("Man10サーバへようこそ Welcome to man10 server", NamedTextColor.GOLD)

    private val footerUrlLine: Component =
        Component.text("https://man10.red", NamedTextColor.AQUA, TextDecoration.UNDERLINED)

    @Volatile
    private var cachedFooter: Component? = null

    @Volatile
    private var cachedFooterPlayerCount: Int = -1

    fun renderAll() {
        val footer = footerFor(state.playerCount())
        val viewerCtx = buildViewerContext()
        for (viewer in server.allPlayers) {
            val composed = state.composedSlots(currentServerOf(viewer))
            val desired = collectIds(composed)
            renderFor(viewer, composed, desired, footer, viewerCtx)
        }
    }

    fun renderFor(viewer: Player) {
        val composed = state.composedSlots(currentServerOf(viewer))
        val desired = collectIds(composed)
        val footer = footerFor(state.playerCount())
        val viewerCtx = buildViewerContext()
        renderFor(viewer, composed, desired, footer, viewerCtx)
    }

    private fun currentServerOf(viewer: Player): String? =
        viewer.currentServer.map { it.serverInfo.name }.orElse(null)

    private data class ViewerContext(
        val now: Instant,
        val greeting: Component,
        val time: Component,
    )

    private val greetingComponent: Component =
        Component.text("おはまん！", NamedTextColor.AQUA)

    private val serverLabel: Component =
        Component.text("Server: ", NamedTextColor.AQUA)

    private val pingLabel: Component =
        Component.text("Ping: ", NamedTextColor.AQUA)

    private val connectTimeLabel: Component =
        Component.text("接続時間: ", NamedTextColor.AQUA)

    private fun buildViewerContext(): ViewerContext {
        val nowTime = LocalTime.now()
        return ViewerContext(
            now = Instant.now(),
            greeting = greetingComponent,
            time = Component.text(formatTime(nowTime), NamedTextColor.YELLOW),
        )
    }

    private fun collectIds(composed: List<TabSlot>): Set<UUID> {
        val ids = HashSet<UUID>(composed.size * 2)
        for (slot in composed) {
            ids.add(slot.uuid)
        }
        return ids
    }

    private fun footerFor(playerCount: Int): Component {
        val cached = cachedFooter
        if (cached != null && cachedFooterPlayerCount == playerCount) return cached
        val countLine = Component.text("${playerCount}人が現在オンラインです", NamedTextColor.YELLOW)
        val footer = Component.empty()
            .append(footerWelcomeLine).appendNewline()
            .append(footerUrlLine).appendNewline()
            .append(countLine)
        cachedFooter = footer
        cachedFooterPlayerCount = playerCount
        return footer
    }

    private fun renderFor(
        viewer: Player,
        composed: List<TabSlot>,
        desired: Set<UUID>,
        footer: Component,
        ctx: ViewerContext,
    ) {
        val tabList = viewer.tabList

        for (i in composed.indices) {
            val slot = composed[i]
            val displayName = computeDisplay(viewer, slot, ctx)
            applySlot(tabList, slot, displayName, listOrderForRowMajorIndex(i))
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

        viewer.sendPlayerListHeaderAndFooter(tabHeader, footer)
    }

    private fun listOrderForRowMajorIndex(index: Int): Int {
        val col = index % TabListState.NUM_COLUMNS
        val row = index / TabListState.NUM_COLUMNS
        return TabListState.TOTAL_ENTRIES - (col * TabListState.ROWS_PER_COLUMN + row)
    }

    private fun computeDisplay(viewer: Player, slot: TabSlot, ctx: ViewerContext): Component =
        when (slot) {
            is TopLabelSlot -> computeTopLabel(viewer, slot.column)
            is BottomLabelSlot -> computeBottomLabel(viewer, slot.column, ctx)
            else -> slot.computeDisplayName(formatter)
        }

    private fun computeTopLabel(viewer: Player, column: Int): Component =
        when (column) {
            0 -> {
                val serverName = viewer.currentServer.map { it.serverInfo.name }.orElse("?")
                Component.empty()
                    .append(serverLabel)
                    .append(Component.text(serverName, NamedTextColor.GREEN))
            }
            1 -> Component.empty()
                .append(pingLabel)
                .append(Component.text("${viewer.ping}ms", NamedTextColor.GREEN))
            else -> formatter.emptyDisplay()
        }

    private fun computeBottomLabel(viewer: Player, column: Int, ctx: ViewerContext): Component =
        when (column) {
            0 -> ctx.greeting
            1 -> ctx.time
            2 -> {
                val player = state.get(viewer.uniqueId)
                val minutes = player?.let { Duration.between(it.loginAt, ctx.now).toMinutes() } ?: 0L
                Component.empty()
                    .append(connectTimeLabel)
                    .append(Component.text("${minutes}分", NamedTextColor.GREEN))
            }
            else -> formatter.emptyDisplay()
        }

    private fun formatTime(time: LocalTime): String {
        val ampm = if (time.hour < 12) "AM" else "PM"
        return "%s %02d:%02d".format(ampm, time.hour, time.minute)
    }

    private fun applySlot(tabList: TabList, slot: TabSlot, displayName: Component, listOrder: Int) {
        val existing = tabList.getEntry(slot.uuid)

        if (existing.isPresent) {
            val tle = existing.get()
            tle.setDisplayName(displayName)
            tle.setListOrder(listOrder)
            if (slot is PlayerEntry) {
                tle.setLatency(slot.latency)
            }
            return
        }

        val profile = when (slot) {
            is PlayerEntry -> server.getPlayer(slot.uuid).orElse(null)?.gameProfile ?: return
            is ServerGroupHeader,
            is OverflowSlot,
            is PaddingSlot,
            is TopLabelSlot,
            is BottomLabelSlot -> GameProfile(slot.uuid, slot.username, grayProfileProperties)
        }

        val builder = TabListEntry.builder()
            .tabList(tabList)
            .profile(profile)
            .displayName(displayName)
            .listOrder(listOrder)

        if (slot is PlayerEntry) {
            builder.latency(slot.latency)
        }

        tabList.addEntry(builder.build())
    }

    private companion object {
        const val GRAY_SKIN_VALUE: String =
            "ewogICJ0aW1lc3RhbXAiIDogMTc2NTIxMDEyMTk2OSwKICAicHJvZmlsZUlkIiA6ICI0MDU4NDhjMmJjNTE0ZDhkOThkOTJkMGIwYzhiZDQ0YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJMaWFtX1NhZ2UiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTIyMmFjOTUyYTEyOGRlNmYzYjE4ZjE3YTE0Y2EzMWExYjJmMWFlYzliNGZiMGFjYWRjOTI1NWViYjgyOGE1NyIKICAgIH0KICB9Cn0="

        const val GRAY_SKIN_SIGNATURE: String =
            "rqNnSjbfuFIh822rvUIyCPN9QHax5FymQJXtz3Bb5UqIEr1g8U0QyiYk7RMWBCjIXacbJ/8vBMZ+uW+FXp1eTDZZsT7avXGMLinpP/Yzy5NpIL/kGf7QA1CMFXFLZNWb0CJQhwYmIzWKHXOrrpj8BaHS4sF1eDJuu1yD8S9uWALfXQvzAwEwEOzx4SnX09LcanVHDA0gtLPlMq1fSeVcPSJ0VlvMHSc3MjG9QiSbU8itdOVh87m6wrOsbzo8I/kiyqiAHs5X7ViSMGLvWStQ1fv8RhF4iSdMsDKNX0oP3FTQ4dKiB1JdKSjXggJ9GcrxyT0WrAN7jMZggsI7m76m9li6CAAp0kRN9W4OvRsBYLTgACSuh0Ho9eeUX+6RXAR/s3smnGSfe5z8MHG++/FrM5OMWsnQ9k5V9FLONTvcOXxCJNPGiBLHt+7+6Syh0cdfwc/uSslIisCqSEU0isiPMMUrElLZP48Dk1QCn/Je2JIW5hDi7xwPv7joGY9oqpZqcUmzpQkiT2Jq248gmsc+QTCONNxcOMKIDlYfDl7hP/b1pQdA6/2y+h4pGlD8hnP416OhN+uEijeOSqMaE6aQAalJBpCZtDfUDUU0C/N/ShKEmn62GA1KVtbf5Mhoglbj2d9pdmsiBg24RqKEKTlOOemcwOR99mFuysRWgCnyfeM="
    }
}
