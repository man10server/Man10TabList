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
        // ただし ADD_PLAYER は listOrder/listed を運ばないため、通過すると backend がエントリを
        // listOrder=0・listed=true で(再)追加してプラグインの 4x20 整形を崩す (位置がバラつく・退避が戻る)。
        // そこで backend 由来の ADD_PLAYER (displayName が管理値と異なる) を検知したら、その viewer を
        // 次 tick で 1 回だけ再描画して整形を戻す。デバウンスでサーバー移動時の一斉 ADD_PLAYER を 1 回に
        // まとめる。(cancel 専任・field patch 禁止の方針は維持: パケットは書き換えず Velocity API で戻す)
        if (WrapperPlayServerPlayerInfoUpdate.Action.ADD_PLAYER in actions) {
            val viewerId = event.user.uuid ?: return
            for (entry in wrapper.entries) {
                val expected = state.displayNameFor(entry.profileId) ?: continue
                // プラグイン自身の addEntry (displayName=expected) には反応しないのでループしない。
                if (entry.displayName != expected) {
                    renderer.scheduleViewerRefresh(viewerId)
                    return
                }
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
