package red.man10.tablist.listener

import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.connection.DisconnectEvent
import com.velocitypowered.api.event.connection.PostLoginEvent
import com.velocitypowered.api.event.player.ServerPostConnectEvent
import red.man10.tablist.render.TabListRenderer
import red.man10.tablist.state.TabListState

class PlayerLifecycleListener(
    private val state: TabListState,
    private val renderer: TabListRenderer,
) {

    private companion object {
        const val SERVER_PENDING = "?"
    }

    @Subscribe
    fun onLogin(event: PostLoginEvent) {
        val player = event.player
        state.addOrGet(player.uniqueId, player.username, SERVER_PENDING)
        renderer.renderAll()
    }

    @Subscribe
    fun onServerPostConnect(event: ServerPostConnectEvent) {
        val player = event.player
        val serverName = player.currentServer
            .map { it.serverInfo.name }
            .orElse(SERVER_PENDING)
        state.upsert(player.uniqueId, player.username, serverName)
        renderer.renderAll()
    }

    @Subscribe
    fun onDisconnect(event: DisconnectEvent) {
        val player = event.player
        state.remove(player.uniqueId)
        renderer.renderAll()
    }
}
