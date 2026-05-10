package red.man10.tablist.render

import net.kyori.adventure.text.Component
import red.man10.tablist.state.PlayerEntry

class TabListFormatter {

    fun format(entry: PlayerEntry): Component =
        Component.text("[${entry.serverName}] ${entry.username}")
}
