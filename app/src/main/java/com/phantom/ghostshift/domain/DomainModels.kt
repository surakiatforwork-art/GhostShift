package com.phantom.ghostshift.domain

data class SchedulePhoto(
    val id: Long,
    val tag: String,
    val kind: Kind,
    val idx: Int,
    val downloaded: Boolean,
    val downloadedAt: Long?
)

enum class Kind {
    IN, OUT
}

fun String.parseTag(): Pair<Kind, Int>? {
    val regex = """^(IN|OUT)-(\d+)$""".toRegex(RegexOption.IGNORE_CASE)
    val match = regex.find(this.trim()) ?: return null
    val kindStr = match.groupValues[1].uppercase()
    val idx = match.groupValues[2].toInt()
    val kind = if (kindStr == "IN") Kind.IN else Kind.OUT
    return kind to idx
}


fun Kind.next(): Kind = if (this == Kind.IN) Kind.OUT else Kind.IN

data class TimerState(
    val running: Boolean,
    val locked: Boolean,
    val startAt: Long?,
    val targetAt: Long?
)

data class ScheduleResult(
    val ok: Boolean,
    val error: String = "",
    val warn: String = "",
    val nextTag: String? = null,
    val nextAt: Long? = null,
    val planAtByTag: Map<String, Long> = emptyMap()
)
