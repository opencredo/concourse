package com.opencredo.concursus.kotlin

import com.opencredo.concursus.domain.events.dispatching.EventBus
import com.opencredo.concursus.domain.events.processing.EventBatchProcessor
import com.opencredo.concursus.domain.events.sourcing.EventSource
import com.opencredo.concursus.domain.events.storage.InMemoryEventStore
import com.opencredo.concursus.domain.time.StreamTimestamp
import com.opencredo.concursus.kotlin.LightbulbEvent.*
import com.opencredo.concursus.kotlin.LightbulbState.LightbulbTransitions
import java.time.temporal.ChronoUnit.MILLIS
import java.util.*

@HandlesEventsFor("lightbulb")
sealed class LightbulbEvent {

    companion object Factory : KEventFactory<LightbulbEvent>()

    @Initial class Created(val wattage: Int) : LightbulbEvent()
    class ScrewedIn(val location: String) : LightbulbEvent()
    class Unscrewed() : LightbulbEvent()
    class SwitchedOn() : LightbulbEvent()
    class SwitchedOff() : LightbulbEvent()
}

data class LightbulbState(val wattage: Int, val location: String?, val isSwitchedOn: Boolean) {
    companion object LightbulbTransitions : Transitions<LightbulbState, LightbulbEvent> {

        override fun initial(data: LightbulbEvent): LightbulbState? = when(data) {
            is Created -> LightbulbState(data.wattage, null, false)
            else -> null
        }

        override fun next(previousState: LightbulbState, data: LightbulbEvent): LightbulbState = when(data) {
            is Created -> throw IllegalStateException("Lightbulb cannot be created twice")
            is ScrewedIn -> previousState.copy(location = data.location)
            is Unscrewed -> previousState.copy(location = null)
            is SwitchedOn -> previousState.copy(isSwitchedOn = true)
            is SwitchedOff -> previousState.copy(isSwitchedOn = false)
        }
    }
}

fun main(args: Array<String>) {
    val eventStore = InMemoryEventStore.empty()
    val eventBus = EventBus.processingWith(EventBatchProcessor.forwardingTo(eventStore))
    val eventSource = EventSource.retrievingWith(eventStore)

    val lightbulbId = UUID.randomUUID()
    var start = StreamTimestamp.now()

    eventBus.dispatch(LightbulbEvent.Factory, {
        it.write(start.plus(1, MILLIS), lightbulbId, Created(wattage = 40))
          .write(start,                 lightbulbId, ScrewedIn(location = "hallway"))
          .write(start.plus(2, MILLIS), lightbulbId, SwitchedOn())
    })

    val cached = eventSource.preload(LightbulbEvent::class, arrayListOf(lightbulbId))

    val messages = cached.replaying(lightbulbId)
            .inAscendingCausalOrder()
            .collectAll { kevent ->
        val data = kevent.data
        val msg = when(data) {
            is Created -> "Lightbulb created with wattage " + data.wattage
            is ScrewedIn -> "Lightbulb screwed in @ " + data.location
            is Unscrewed -> "Lightbulb unscrewed"
            is SwitchedOn -> "Lightbulb switched on"
            is SwitchedOff -> "Lightbulb switched off"
        }
        msg + " at " + kevent.timestamp.timestamp
    }

    messages.forEach { println(it) }

    val state = cached.replaying(lightbulbId).buildState(LightbulbTransitions)

    println(state)


}