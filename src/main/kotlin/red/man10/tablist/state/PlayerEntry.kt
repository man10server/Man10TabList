package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.time.Instant
import java.util.UUID

class PlayerEntry internal constructor(
    override val uuid: UUID,
    override val username: String,
    initialServerName: String,
) : TabSlot() {

    val loginAt: Instant = Instant.now()

    @Volatile
    var serverName: String = initialServerName
        private set

    @Volatile
    var latency: Int = 0
        internal set

    @Volatile
    private var cachedDisplayName: Component? = null

    internal fun updateServerName(newName: String) {
        if (serverName == newName) return
        serverName = newName
        cachedDisplayName = null
    }

    override fun computeDisplayName(formatter: TabListFormatter): Component {
        var cached = cachedDisplayName
        if (cached == null) {
            cached = formatter.formatPlayer(this)
            cachedDisplayName = cached
        }
        return cached
    }
}
