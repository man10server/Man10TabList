package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class PlayerEntry internal constructor(
    val uuid: UUID,
    val username: String,
    initialServerName: String,
) {

    @Volatile
    var serverName: String = initialServerName
        private set

    @Volatile
    var latency: Int = 0
        internal set

    @Volatile
    var cachedDisplayName: Component? = null
        private set

    internal fun updateServerName(newName: String) {
        if (serverName == newName) return
        serverName = newName
        cachedDisplayName = null
    }

    internal fun computeDisplayName(formatter: TabListFormatter): Component {
        var cached = cachedDisplayName
        if (cached == null) {
            cached = formatter.format(this)
            cachedDisplayName = cached
        }
        return cached
    }
}
