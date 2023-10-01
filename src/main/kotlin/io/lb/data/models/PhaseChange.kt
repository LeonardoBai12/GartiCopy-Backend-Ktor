package io.lb.data.models

import io.lb.data.Room
import kotlinx.coroutines.DelicateCoroutinesApi

data class PhaseChange @DelicateCoroutinesApi constructor(
    var phase: Room.Phase?,
    var timestamp: Long,
    val drawingPlayer: String? = null
) : BaseModel(Type.PHASE_CHANGE)
