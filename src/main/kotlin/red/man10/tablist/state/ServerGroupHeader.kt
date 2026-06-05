package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class ServerGroupHeader internal constructor(
    val serverName: String,
) : TabSlot() {

    override val uuid: UUID = UUID.nameUUIDFromBytes("man10tablist:server-header:$serverName".toByteArray(Charsets.UTF_8))

    // GameProfile の name は 16 文字以内に収める必要がある。長いサーバー名でも超過しないよう
    // 英数字と '_' にサニタイズしプレフィックス付与後 take(16) で切り詰める (OverflowSlot と同じ流儀)。
    // uuid はフルの serverName から導出して一意性を保つため、username 側の衝突は表示に影響しない
    // (username は displayName で上書きされ表示には使われない)。
    override val username: String = ("m10hdr_" + serverName.filter { it.isLetterOrDigit() || it == '_' }).take(16)

    @Volatile
    var count: Int = 0
        internal set(value) {
            if (field == value) return
            field = value
            cachedDisplayName = null
        }

    @Volatile
    private var cachedDisplayName: Component? = null

    override fun computeDisplayName(formatter: TabListFormatter): Component {
        var cached = cachedDisplayName
        if (cached == null) {
            cached = formatter.formatServerHeader(this)
            cachedDisplayName = cached
        }
        return cached
    }
}
