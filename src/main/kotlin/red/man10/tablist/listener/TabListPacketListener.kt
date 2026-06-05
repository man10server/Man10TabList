package red.man10.tablist.listener

import com.github.retrooper.packetevents.event.PacketListenerAbstract
import com.github.retrooper.packetevents.event.PacketListenerPriority
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate
import red.man10.tablist.render.TabListRenderer
import red.man10.tablist.state.TabListState

class TabListPacketListener(
    private val state: TabListState,
    private val renderer: TabListRenderer,
) : PacketListenerAbstract(PacketListenerPriority.NORMAL) {

    override fun onPacketSend(event: PacketSendEvent) {
        if (event.packetType != PacketType.Play.Server.PLAYER_INFO_UPDATE) return

        val wrapper = WrapperPlayServerPlayerInfoUpdate(event)
        val actions = wrapper.actions

        // ADD_PLAYER を含むパケットは絶対に cancel しない。ADD_PLAYER には署名付きチャット用の
        // chat session が同梱されており、これを握りつぶすとクライアントのチャット署名が壊れるため
        // (コミット 4fadf20 の意図)。
        // ただし通過させると backend の displayName が一瞬クライアントに表示されてしまうので、
        // 管理対象プレイヤーについては Velocity API 経由で displayName を即時に再適用して上書きし、
        // ちらつきを最小化する。setDisplayName は差分チェックせず無条件でパケットを送るため確実に上書きできる。
        // (cancel 専任・field patch 禁止の方針は維持: パケットは書き換えず Velocity API で上書きする)
        if (WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER in actions) {
            val viewerId = event.user.uuid ?: return
            for (entry in wrapper.entries) {
                val expected = state.displayNameFor(entry.profileId) ?: continue
                renderer.reapplyDisplayName(viewerId, entry.profileId, expected)
            }
            return
        }
        if (WrapperPlayServerPlayerInfoUpdate.Action.UPDATE_DISPLAY_NAME !in actions) return

        for (entry in wrapper.entries) {
            val expected = state.displayNameFor(entry.profileId) ?: continue
            if (entry.displayName != expected) {
                // backend が Velocity API 管理の displayName と異なる名前を送ってきた。
                // 設計方針 (cancel 専任・field patch 禁止) によりパケット単位の cancel しか手段がなく、
                // 同梱された他エントリ更新や他アクション (latency 等) を巻き添えにする。
                // ただし latency は Velocity API 管理 (renderAll が Player.getPing から再設定) のため、
                // 巻き添えで消えても次の render で復元され実害は小さい。
                event.isCancelled = true
                return
            }
        }
    }
}
