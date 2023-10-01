package io.lb.data.models

data class DrawData(
    val roomName: String,
    val color: Int,
    val thickness: Int,
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val motionEvent: Int,
) : BaseModel(Type.DRAW_DATA) {
    companion object {
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
    }
}
