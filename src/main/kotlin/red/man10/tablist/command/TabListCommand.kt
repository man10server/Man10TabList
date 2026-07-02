package red.man10.tablist.command

import com.velocitypowered.api.command.SimpleCommand
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import red.man10.tablist.config.TabListConfig
import red.man10.tablist.render.TabListRenderer
import red.man10.tablist.state.TabListState
import java.nio.file.Path

class TabListCommand(
    private val dataDirectory: Path,
    private val state: TabListState,
    private val renderer: TabListRenderer,
) : SimpleCommand {

    override fun execute(invocation: SimpleCommand.Invocation) {
        val source = invocation.source()
        val args = invocation.arguments()

        if (args.size != 1 || args[0].lowercase() != "reload") {
            source.sendMessage(Component.text("Usage: /man10tablist reload", NamedTextColor.RED))
            return
        }

        try {
            val config = TabListConfig.load(dataDirectory)
            state.privateServers = config.privateServers
            renderer.renderAll()
            source.sendMessage(Component.text("Man10TabList config reloaded.", NamedTextColor.GREEN))
        } catch (e: Exception) {
            source.sendMessage(
                Component.text("Failed to reload config: ${e.message}", NamedTextColor.RED)
            )
        }
    }

    override fun hasPermission(invocation: SimpleCommand.Invocation): Boolean =
        invocation.source().hasPermission(PERMISSION_RELOAD)

    override fun suggest(invocation: SimpleCommand.Invocation): List<String> =
        if (invocation.arguments().isEmpty()) listOf("reload") else emptyList()

    private companion object {
        const val PERMISSION_RELOAD: String = "man10tablist.reload"
    }
}
