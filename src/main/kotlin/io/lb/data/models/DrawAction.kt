package io.lb.data.models

data class DrawAction(
    val action: String
) : BaseModel(Type.DRAW_ACTION) {
    enum class ActionType {
        UNDO
    }
}
