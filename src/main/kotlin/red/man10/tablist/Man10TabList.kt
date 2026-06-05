package red.man10.tablist

import com.github.retrooper.packetevents.PacketEvents
import com.google.inject.Inject
import com.velocitypowered.api.event.Subscribe
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent
import com.velocitypowered.api.plugin.Plugin
import com.velocitypowered.api.plugin.annotation.DataDirectory
import com.velocitypowered.api.proxy.ProxyServer
import com.velocitypowered.api.scheduler.ScheduledTask
import io.github.retrooper.packetevents.velocity.factory.VelocityPacketEventsBuilder
import org.slf4j.Logger
import red.man10.tablist.listener.PlayerLifecycleListener
import red.man10.tablist.listener.TabListPacketListener
import red.man10.tablist.render.TabListFormatter
import red.man10.tablist.render.TabListRenderer
import red.man10.tablist.state.TabListState
import java.nio.file.Path
import java.util.concurrent.TimeUnit

@Plugin(
    id = "man10tablist",
    name = "Man10TabList",
    version = "1.0.0-SNAPSHOT",
    description = "Tab list plugin for the Man10 Velocity proxy",
    authors = ["Man10"],
)
class Man10TabList @Inject constructor(
    private val server: ProxyServer,
    private val logger: Logger,
    @DataDirectory private val dataDirectory: Path,
) {

    private var state: TabListState? = null
    private var packetListener: TabListPacketListener? = null
    private var refreshTask: ScheduledTask? = null

    @Subscribe
    fun onProxyInitialization(event: ProxyInitializeEvent) {
        val pluginContainer = server.pluginManager.fromInstance(this).orElseThrow()
        PacketEvents.setAPI(
            VelocityPacketEventsBuilder.build(server, pluginContainer, logger, dataDirectory)
        )
        PacketEvents.getAPI().load()

        val formatter = TabListFormatter()
        val newState = TabListState(formatter)
        this.state = newState

        val renderer = TabListRenderer(server, newState, formatter, this)
        val listener = PlayerLifecycleListener(newState, renderer)
        server.eventManager.register(this, listener)

        val newPacketListener = TabListPacketListener(newState, renderer)
        this.packetListener = newPacketListener
        PacketEvents.getAPI().eventManager.registerListener(newPacketListener)

        PacketEvents.getAPI().init()

        refreshTask = server.scheduler.buildTask(this, Runnable { renderer.renderAll() })
            .repeat(REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS)
            .schedule()

        logger.info("Man10TabList initialized.")
    }

    @Subscribe
    fun onProxyShutdown(event: ProxyShutdownEvent) {
        refreshTask?.cancel()
        refreshTask = null

        try {
            val api = PacketEvents.getAPI()
            if (api != null) {
                packetListener?.let { api.eventManager.unregisterListener(it) }
                api.terminate()
            }
        } catch (e: Throwable) {
            logger.warn("PacketEvents teardown failed", e)
        }
        packetListener = null

        server.eventManager.unregisterListeners(this)
        state?.clear()
        state = null
        logger.info("Man10TabList shut down.")
    }

    private companion object {
        const val REFRESH_INTERVAL_SECONDS: Long = 30L
    }
}
