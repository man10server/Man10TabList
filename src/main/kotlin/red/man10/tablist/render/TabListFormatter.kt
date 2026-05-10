package red.man10.tablist.render

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import red.man10.tablist.state.OverflowSlot
import red.man10.tablist.state.PlayerEntry
import red.man10.tablist.state.ServerGroupHeader

class TabListFormatter {

    private val emptyComponent: Component = Component.text("                    ")

    fun formatPlayer(entry: PlayerEntry): Component =
        Component.text(entry.username)

    fun formatServerHeader(header: ServerGroupHeader): Component =
        Component.empty()
            .append(Component.text(header.serverName, NamedTextColor.YELLOW, TextDecoration.UNDERLINED, TextDecoration.BOLD))
            .append(Component.text(" (${header.count}):", NamedTextColor.WHITE))

    fun formatOverflow(slot: OverflowSlot): Component =
        Component.text("... and ${slot.remaining} more", NamedTextColor.GRAY, TextDecoration.ITALIC)

    fun emptyDisplay(): Component = emptyComponent
}
