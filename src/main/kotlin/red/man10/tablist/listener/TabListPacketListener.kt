package red.man10.tablist.listener

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import red.man10.tablist.state.TabListState

class TabListPacketListener(
    private val state: TabListState,
) : PacketListenerAbstract(PacketListenerPriority.NORMAL) {

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.PLAYER_INFO_UPDATE) return

        val wrapper = WrapperPlayServerPlayerInfoUpdate(event)
        val actions = wrapper.actions
        if (WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER in actions) return
        if (WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME !in actions) return

        for (entry in wrapper.entries) {
            val expected = state.displayNameFor(entry.profileId) ?: continue
            if (entry.displayName != expected) {
                event.isCancelled = true
                return
            }
        }
    }
}
