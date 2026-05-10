package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class OverflowSlot internal constructor(
    val serverName: String,
) : TabSlot() {

    override val uuid: UUID = UUID.nameUUIDFromBytes("man10tablist:overflow:$serverName".toByteArray(Charsets.UTF_8))
    override val username: String = ("m10ovf_" + serverName.filter { it.isLetterOrDigit() || it == '_' }).take(16)

    @Volatile
    var remaining: Int = 0
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
            cached = formatter.formatOverflow(this)
            cachedDisplayName = cached
        }
        return cached
    }
}
