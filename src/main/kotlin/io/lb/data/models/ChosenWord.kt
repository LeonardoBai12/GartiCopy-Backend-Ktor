package io.lb.data.models

data class ChosenWord(
    val chosenWord: String,
    val roomName: String
) : BaseModel(Type.CHOSEN_WORD)
