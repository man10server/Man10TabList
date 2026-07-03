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
import red.man10.tablist.state.SpectatorTracker
import red.man10.tablist.state.TabListState
import red.man10.tablist.state.TabSlot
import red.man10.tablist.state.TopLabelSlot
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TabListRenderer(
    private val server: ProxyServer,
    private val state: TabListState,
    private val formatter: TabListFormatter,
    private val spectatorTracker: SpectatorTracker,
    private val plugin: Any,
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

    // ADD_PLAYER 通過で整形が崩れた viewer の再描画予約 (デバウンス用)。バースト分を 1 回にまとめる。
    private val pendingRefresh: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // スペクテイター突入時のバニラ復元予約 (デバウンス用)。
    private val pendingRestore: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    // @Synchronized で render 全体を直列化する。renderAll はスケジューラスレッドと
    // Velocity の login/connect/disconnect イベントの両方から非同期に呼ばれ、
    // 共有インスタンス (ServerGroupHeader.count / OverflowSlot.remaining) の書き換えや
    // 同一 viewer.tabList への add/remove が競合しうるため。
    @Synchronized
    fun renderAll() {
        // server.allPlayers は呼ぶたびに新規コレクションを確保するため 1 度だけ取得して使い回す
        // (latency 更新ループと描画ループで同一スナップショットを共有し、確保を 1 回に抑える)。
        val players = server.allPlayers

        // 各プレイヤーの latency を Velocity の Player.getPing() から state へ反映する。
        // applySlot が PlayerEntry.latency をそのまま送るため、ここで更新しないと ping バーが 0 のままになる。
        for (p in players) {
            // ping は未測定時 -1 がありうるので負値は 0 にクランプする。
            state.updateLatency(p.uniqueId, maxOf(0, p.ping.toInt()))
        }

        val footer = footerFor(state.playerCount())
        val viewerCtx = buildViewerContext()
        for (viewer in players) {
            if (spectatorTracker.isSpectator(viewer.uniqueId)) continue
            val viewerServerName = currentServerOf(viewer)
            val bypassPrivate = viewer.hasPermission(BYPASS_PRIVATE_PERMISSION)
            val composed = state.composedSlots(viewerServerName, viewer.uniqueId, bypassPrivate)
            val desired = collectIds(composed)
            renderFor(viewer, composed, desired, footer, viewerCtx, viewerServerName)
        }
    }

    // 指定 viewer 1 人だけを再描画する (renderAll の単一 viewer 版)。ADD_PLAYER 通過後の整形戻しに使う。
    // renderAll と同一モニタで直列化する。latency は renderAll が全員分更新するためここでは触らない。
    @Synchronized
    fun renderViewer(viewer: Player) {
        if (spectatorTracker.isSpectator(viewer.uniqueId)) return
        val footer = footerFor(state.playerCount())
        val viewerCtx = buildViewerContext()
        val viewerServerName = currentServerOf(viewer)
        val bypassPrivate = viewer.hasPermission(BYPASS_PRIVATE_PERMISSION)
        val composed = state.composedSlots(viewerServerName, viewer.uniqueId, bypassPrivate)
        val desired = collectIds(composed)
        renderFor(viewer, composed, desired, footer, viewerCtx, viewerServerName)
    }

    // backend の ADD_PLAYER は listOrder/listed を運ばないため、通過するとプラグインが組んだ整形が崩れる
    // (エントリが listOrder=0 で最下段にバラつく・listed=true で退避が戻る)。検知した viewer を次 tick で
    // 1 回だけ再描画して listOrder/listed/displayName を一括で戻す。サーバー移動時の一斉 ADD_PLAYER は
    // pendingRefresh で 1 回にまとめ、Netty スレッドはブロックせずスケジューラスレッドで renderViewer を実行する。
    fun scheduleViewerRefresh(viewerId: UUID) {
        if (!pendingRefresh.add(viewerId)) return
        server.scheduler.buildTask(plugin, Runnable {
            pendingRefresh.remove(viewerId)
            val viewer = server.getPlayer(viewerId).orElse(null) ?: return@Runnable
            renderViewer(viewer)
        }).delay(REFRESH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).schedule()
    }

    // スペクテイター突入時に偽エントリを除去し、同サーバーの実プレイヤーをバニラ表示へ戻す。
    // Netty スレッドから呼ばれるためスケジューラ経由で restoreVanilla を実行する。
    fun scheduleViewerRestore(viewerId: UUID) {
        if (!pendingRestore.add(viewerId)) return
        server.scheduler.buildTask(plugin, Runnable {
            pendingRestore.remove(viewerId)
            val viewer = server.getPlayer(viewerId).orElse(null) ?: return@Runnable
            restoreVanilla(viewer)
        }).delay(REFRESH_DEBOUNCE_MS, TimeUnit.MILLISECONDS).schedule()
    }

    @Synchronized
    fun restoreVanilla(viewer: Player) {
        if (!spectatorTracker.isSpectator(viewer.uniqueId)) return

        val tabList = viewer.tabList
        val viewerServerName = currentServerOf(viewer)
        val toRemove = ArrayList<UUID>()

        for (existing in tabList.entries) {
            val id = existing.profile.id
            val player = state.get(id)
            if (player != null && player.serverName == viewerServerName) {
                if (!existing.isListed) existing.setListed(true)
                if (existing.listOrder != 0) existing.setListOrder(0)
                if (existing.displayNameComponent.isPresent) existing.setDisplayName(null)
            } else {
                toRemove.add(id)
            }
        }

        for (id in toRemove) {
            tabList.removeEntry(id)
        }

        viewer.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty())
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
        viewerServerName: String?,
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
            if (id in desired) continue
            val player = state.get(id)
            if (player != null && player.serverName == viewerServerName) {
                // viewer と同じサーバーの実プレイヤーで表示枠から外れた人。removeEntry すると player info が
                // 消えてスキン (Steve 化) と chat session (チャット検証エラー) が壊れるため、listed=false で
                // player info だけ残しタブ非表示にする (バニラの 80 人超と同じ挙動)。setListed は無条件送信
                // なので差分時のみ呼ぶ。
                if (existing.isListed) existing.setListed(false)
            } else {
                // 他サーバーの実プレイヤー (viewer のクライアントに実体が無い) や不要になった偽エントリは
                // player info を残す意味が無いので削除する。
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
            // 表示枠に入ったエントリは listed=true に戻す (前 render で listed=false 退避された自サーバー
            // プレイヤーの再表示など)。setListed は無条件送信なので差分時のみ呼ぶ。
            if (!tle.isListed) tle.setListed(true)
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
            .listed(true)

        if (slot is PlayerEntry) {
            builder.latency(slot.latency)
        }

        tabList.addEntry(builder.build())
    }

    private companion object {
        const val REFRESH_DEBOUNCE_MS: Long = 50L
        const val BYPASS_PRIVATE_PERMISSION: String = "man10tablist.bypass-private"

        const val GRAY_SKIN_VALUE: String =
            "ewogICJ0aW1lc3RhbXAiIDogMTc2NTIxMDEyMTk2OSwKICAicHJvZmlsZUlkIiA6ICI0MDU4NDhjMmJjNTE0ZDhkOThkOTJkMGIwYzhiZDQ0YiIsCiAgInByb2ZpbGVOYW1lIiA6ICJMaWFtX1NhZ2UiLAogICJzaWduYXR1cmVSZXF1aXJlZCIgOiB0cnVlLAogICJ0ZXh0dXJlcyIgOiB7CiAgICAiU0tJTiIgOiB7CiAgICAgICJ1cmwiIDogImh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTIyMmFjOTUyYTEyOGRlNmYzYjE4ZjE3YTE0Y2EzMWExYjJmMWFlYzliNGZiMGFjYWRjOTI1NWViYjgyOGE1NyIKICAgIH0KICB9Cn0="

        const val GRAY_SKIN_SIGNATURE: String =
            "rqNnSjbfuFIh822rvUIyCPN9QHax5FymQJXtz3Bb5UqIEr1g8U0QyiYk7RMWBCjIXacbJ/8vBMZ+uW+FXp1eTDZZsT7avXGMLinpP/Yzy5NpIL/kGf7QA1CMFXFLZNWb0CJQhwYmIzWKHXOrrpj8BaHS4sF1eDJuu1yD8S9uWALfXQvzAwEwEOzx4SnX09LcanVHDA0gtLPlMq1fSeVcPSJ0VlvMHSc3MjG9QiSbU8itdOVh87m6wrOsbzo8I/kiyqiAHs5X7ViSMGLvWStQ1fv8RhF4iSdMsDKNX0oP3FTQ4dKiB1JdKSjXggJ9GcrxyT0WrAN7jMZggsI7m76m9li6CAAp0kRN9W4OvRsBYLTgACSuh0Ho9eeUX+6RXAR/s3smnGSfe5z8MHG++/FrM5OMWsnQ9k5V9FLONTvcOXxCJNPGiBLHt+7+6Syh0cdfwc/uSslIisCqSEU0isiPMMUrElLZP48Dk1QCn/Je2JIW5hDi7xwPv7joGY9oqpZqcUmzpQkiT2Jq248gmsc+QTCONNxcOMKIDlYfDl7hP/b1pQdA6/2y+h4pGlD8hnP416OhN+uEijeOSqMaE6aQAalJBpCZtDfUDUU0C/N/ShKEmn62GA1KVtbf5Mhoglbj2d9pdmsiBg24RqKEKTlOOemcwOR99mFuysRWgCnyfeM="
    }
}
