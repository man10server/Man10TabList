package red.man10.tablist.state

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SpectatorTracker {

    enum class Transition {
        UNCHANGED,
        ENTERED,
        LEFT,
    }

    private val spectators = ConcurrentHashMap.newKeySet<UUID>()

    fun isSpectator(viewerId: UUID): Boolean = spectators.contains(viewerId)

    fun update(viewerId: UUID, isSpectator: Boolean): Transition {
        val wasSpectator = spectators.contains(viewerId)
        return when {
            isSpectator && !wasSpectator -> {
                spectators.add(viewerId)
                Transition.ENTERED
            }
            !isSpectator && wasSpectator -> {
                spectators.remove(viewerId)
                Transition.LEFT
            }
            else -> Transition.UNCHANGED
        }
    }

    fun remove(viewerId: UUID) {
        spectators.remove(viewerId)
    }

    fun clear() {
        spectators.clear()
    }
}
