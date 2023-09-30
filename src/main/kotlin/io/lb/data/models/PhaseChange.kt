package io.lb.data.models

import io.lb.data.Room

data class PhaseChange(
    var phase: Room.Phase?,
    var time: Long,
    val drawingPlayer: String? = null
) : BaseModel(Type.PHASE_CHANGE)
