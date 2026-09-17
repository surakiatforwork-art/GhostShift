package com.phantom.ghostshift.domain

object ScheduleCalculator {
    data class ScheduleItem(val tag: String, val planAt: Long, val kind: Kind)

    fun computeScheduleExactFit(
        photos: List<SchedulePhoto>,
        timer: TimerState,
        settings: ScheduleSettings = ScheduleSettings()
    ): ScheduleResult {
        val startAt = timer.startAt
        val targetAt = timer.targetAt
        if (!timer.running || startAt == null || targetAt == null) {
            return ScheduleResult(false, error = "เริ่มนับเวลาก่อน", nextTag = SlotManager.computeNextSlot(photos))
        }

        val pendingByTag = photos.filter { !it.downloaded }.associateBy { it.tag }
        val last = photos.filter { it.downloadedAt != null }.maxByOrNull { it.downloadedAt ?: Long.MIN_VALUE }
        val nextExpected = last?.let { SlotManager.nextTagInSequence(it.tag) } ?: "IN-1"
        val chain = mutableListOf<SchedulePhoto>()
        var tag = nextExpected
        var missingTag: String? = null
        for (ignored in 0 until 2_000) {
            val photo = pendingByTag[tag]
            if (photo == null) {
                missingTag = tag
                break
            }
            chain += photo
            tag = SlotManager.nextTagInSequence(tag) ?: break
        }
        if (chain.isEmpty()) return ScheduleResult(false, error = "ขาดรูป $nextExpected", nextTag = nextExpected)

        val baseTime = last?.downloadedAt ?: startAt
        val totalMs = targetAt - baseTime
        if (totalMs <= 0L) return ScheduleResult(false, error = "เลยเวลาเป้าหมายแล้ว", nextTag = chain.first().tag, nextAt = System.currentTimeMillis())
        if (last == null && chain.size == 1) {
            val photo = chain.first()
            return ScheduleResult(true, nextTag = photo.tag, nextAt = targetAt, planAtByTag = mapOf(photo.tag to targetAt), items = listOf(ScheduleItem(photo.tag, targetAt, photo.kind)))
        }

        val kinds = if (last != null) buildList {
            add(last.kind)
            for (index in 1 until chain.size) add(chain[index - 1].kind)
        } else chain.dropLast(1).map { it.kind }
        val minimums = kinds.map { minMinutes(it, settings) * 60_000L }
        val maximums = kinds.map { maxMinutes(it, settings)?.times(60_000L) }
        val minimumTotal = minimums.sum()
        val finiteMaximumTotal = if (maximums.all { it != null }) maximums.sumOf { it ?: 0L } else null
        val gaps = when {
            totalMs < minimumTotal -> minimums
            finiteMaximumTotal != null && totalMs > finiteMaximumTotal -> maximums.map { it ?: 0L }
            else -> distribute(totalMs, minimums, maximums)
        }

        val plan = linkedMapOf<String, Long>()
        var time = baseTime
        if (last != null) {
            chain.forEachIndexed { index, photo ->
                time += gaps[index]
                plan[photo.tag] = time
            }
        } else {
            plan[chain.first().tag] = startAt
            for (index in 1 until chain.size) {
                time += gaps[index - 1]
                plan[chain[index].tag] = time
            }
        }
        val error = when {
            totalMs < minimumTotal -> "เวลาน้อยกว่าค่าขั้นต่ำที่ตั้งไว้"
            finiteMaximumTotal != null && totalMs > finiteMaximumTotal -> if (missingTag != null) "ขาดรูป $missingTag" else "รูปไม่พอถึงเป้าหมาย"
            else -> ""
        }
        return ScheduleResult(
            ok = error.isEmpty(), error = error, nextTag = chain.first().tag,
            nextAt = plan[chain.first().tag], planAtByTag = plan,
            items = chain.map { ScheduleItem(it.tag, plan[it.tag] ?: baseTime, it.kind) }
        )
    }

    private fun minMinutes(kind: Kind, settings: ScheduleSettings): Long =
        (if (kind == Kind.IN) settings.inOutMinMinutes else settings.outInMinMinutes).coerceAtLeast(0).toLong()

    private fun maxMinutes(kind: Kind, settings: ScheduleSettings): Long? {
        val minimum = if (kind == Kind.IN) settings.inOutMinMinutes else settings.outInMinMinutes
        return (if (kind == Kind.IN) settings.inOutMaxMinutes else settings.outInMaxMinutes)?.coerceAtLeast(minimum)?.toLong()
    }

    private fun distribute(totalMs: Long, minimums: List<Long>, maximums: List<Long?>): List<Long> {
        val result = minimums.toMutableList()
        var remaining = totalMs - result.sum()
        var active = result.indices.filter { maximums[it] == null || result[it] < maximums[it]!! }
        while (remaining > 0L && active.isNotEmpty()) {
            val share = maxOf(1L, remaining / active.size)
            var moved = 0L
            for (index in active) {
                if (remaining <= 0L) break
                val capacity = maximums[index]?.minus(result[index]) ?: remaining
                val addition = minOf(share, capacity, remaining)
                result[index] += addition
                remaining -= addition
                moved += addition
            }
            if (moved == 0L) break
            active = active.filter { maximums[it] == null || result[it] < maximums[it]!! }
        }
        return result
    }
}
