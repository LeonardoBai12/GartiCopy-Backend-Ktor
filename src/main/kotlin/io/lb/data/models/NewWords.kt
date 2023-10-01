package io.lb.data.models

data class NewWords(
    val newWords: List<String>
) : BaseModel(Type.NEW_WORD)
