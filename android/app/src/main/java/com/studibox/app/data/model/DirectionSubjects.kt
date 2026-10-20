package com.studibox.app.data.model

data class DirectionSubjects(
    val code: String,
    val title: String,
    val groups: List<String>,
    val subjects: List<SubjectInfo>
)

data class SubjectInfo(
    val name: String,
    val types: List<String>
)
