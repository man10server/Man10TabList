package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class BottomLabelSlot internal constructor(
    val column: Int,
) : TabSlot() {

    override val uuid: UUID = UUID.nameUUIDFromBytes("man10tablist:bottom:$column".toByteArray(Charsets.UTF_8))
    override val username: String = "m10bot_$column"

    override fun computeDisplayName(formatter: TabListFormatter): Component =
        formatter.emptyDisplay()
}
