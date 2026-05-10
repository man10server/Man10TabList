package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

sealed class TabSlot {
    abstract val uuid: UUID
    abstract val username: String
    abstract fun computeDisplayName(formatter: TabListFormatter): Component
}
