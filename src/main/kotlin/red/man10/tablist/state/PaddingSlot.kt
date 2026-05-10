package red.man10.tablist.state

import net.kyori.adventure.text.Component
import red.man10.tablist.render.TabListFormatter
import java.util.UUID

class PaddingSlot internal constructor(
    val index: Int,
) : TabSlot() {

    override val uuid: UUID = UUID.nameUUIDFromBytes("man10tablist:padding:$index".toByteArray(Charsets.UTF_8))
    override val username: String = "m10pad_%03d".format(index)

    override fun computeDisplayName(formatter: TabListFormatter): Component =
        formatter.emptyDisplay()
}
