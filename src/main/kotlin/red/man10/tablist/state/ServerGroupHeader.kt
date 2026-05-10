package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class ServerGroupHeader internal constructor(
    val serverName: String,
) : TabSlot() {

    override val uuid: UUID = UUID.nameUUIDFromBytes("man10tablist:server-header:$serverName".toByteArray(Charsets.UTF_8))
    override val username: String = "_${serverName}_hdr"

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
