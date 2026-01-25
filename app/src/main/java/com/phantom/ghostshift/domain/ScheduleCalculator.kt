package com.phantom.ghostshift.domain

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

object ScheduleCalculator {
    // Gap constraints: IN photos 3-25 min, OUT photos 4-30 min
    private fun minGapMinutes(kind: Kind): Double = if (kind == Kind.IN) 3.0 else 4.0
    private fun maxGapMinutes(kind: Kind): Double = if (kind == Kind.IN) 25.0 else 30.0

    private fun now(): Long = System.currentTimeMillis()

    data class SolveResult(val ok: Boolean, val a: Double?, val b: Double?)

    data class ScheduleItem(
        val tag: String,
        val planAt: Long,
        val kind: Kind
    )

    // Ported from JS: solveExactAB
    fun solveExactAB(totalMin: Double, nIn: Int, nOut: Int): SolveResult {
        if (nIn == 0 && nOut == 0) return SolveResult(false, null, null)

        if (nOut == 0) {
            val a = totalMin / max(1, nIn)
            val ok = (a >= 3.0 && a <= 25.0)
            return SolveResult(ok, max(3.0, min(25.0, a)), null)
        }
        if (nIn == 0) {
            val b = totalMin / max(1, nOut)
            val ok = (b >= 4.0 && b <= 30.0)
            return SolveResult(ok, null, max(4.0, min(30.0, b)))
        }

        val mean = totalMin / (nIn + nOut)
        var bestA: Double? = null
        var bestB: Double? = null
        var bestScore = 1e18

        // Iterate a from 3.00 to 25.00 step 0.01
        var valA = 300
        while (valA <= 2500) {
            val a = valA / 100.0
            val b = (totalMin - a * nIn) / nOut
            
            if (b >= 4.0 && b <= 30.0 && b > a + 0.001) {
                val score = abs(a - mean) + abs(b - mean) + 0.08 * abs(b - a)
                if (score < bestScore) {
                    bestScore = score
                    bestA = a
                    bestB = b
                }
            }
            valA++
        }

        if (bestA != null && bestB != null) {
            return SolveResult(true, bestA, bestB)
        }

        // Fallback strategy per JS
        val a0 = max(3.0, min(25.0, mean - 1))
        val b0 = max(4.0, min(30.0, mean + 1))
        val bAdj = if (b0 <= a0) min(30.0, a0 + 0.25) else b0
        return SolveResult(false, a0, bAdj)
    }

    // Ported from JS: distributeMsToMatchTotal
    private fun distributeMsToMatchTotal(gapsMs: MutableList<Long>, kinds: List<Kind>, totalMs: Long): List<Long> {
        var sum = gapsMs.sum()
        var diff = totalMs - sum
        if (diff == 0L) return gapsMs

        var safety = 200000
        while (diff != 0L && safety-- > 0) {
            var moved = false
            for (i in gapsMs.indices.reversed()) {
                if (diff == 0L) break
                val kind = kinds[i]
                val minMs = (minGapMinutes(kind) * 60000).roundToLong()
                val maxMs = (maxGapMinutes(kind) * 60000).roundToLong()
                
                if (diff > 0) {
                    if (gapsMs[i] < maxMs) {
                        gapsMs[i] = gapsMs[i] + 1
                        diff -= 1
                        moved = true
                    }
                } else {
                    if (gapsMs[i] > minMs) {
                        gapsMs[i] = gapsMs[i] - 1
                        diff += 1
                        moved = true
                    }
                }
            }
            if (!moved) break
        }
        return gapsMs
    }

    fun computeScheduleExactFit(photos: List<SchedulePhoto>, timer: TimerState): ScheduleResult {
        if (!timer.running || timer.targetAt == null || timer.startAt == null) {
            return ScheduleResult(
                ok = false,
                error = "เริ่มนับเวลาก่อน",
                nextTag = SlotManager.computeNextSlot(photos)
            )
        }

        val pending = photos.filter { !it.downloaded }
        val pendingByTag = pending.associateBy { it.tag }
        
        // Find last downloaded (first time only -> downloadedAt semantics handled by caller/repo)
        // Here we assume photos has correct downloadedAt
        val downloaded = photos.filter { it.downloaded && it.downloadedAt != null }
            .sortedBy { it.downloadedAt }
        val last = downloaded.lastOrNull()
        
        val nextExpected = if (last != null) {
            SlotManager.nextTagInSequence(last.tag) ?: "IN-1"
        } else {
            "IN-1"
        }

        // Build Chain
        val chain = mutableListOf<SchedulePhoto>()
        var currentTag = nextExpected
        var missingTag: String? = null
        
        for (i in 0 until 200) {
            val p = pendingByTag[currentTag]
            if (p == null) {
                missingTag = currentTag
                break
            }
            chain.add(p)
            val nxt = SlotManager.nextTagInSequence(currentTag) ?: break
            currentTag = nxt
        }

        val nextTag = chain.firstOrNull()?.tag ?: nextExpected
        
        if (chain.isEmpty()) {
            return ScheduleResult(
                ok = false,
                error = "ขาดรูป $nextExpected",
                nextTag = nextTag
            )
        }

        val targetAt = timer.targetAt
        // T2 Fix: If last is null (no downloads), baseTime is startAt.
        // using now() causes the schedule to "slide" forward every calc.
        val baseTime = last?.downloadedAt ?: timer.startAt!!
        // Validation handled by caller: timer.startAt checked not null above.

        val totalMs = targetAt - baseTime
        if (totalMs <= 0) {
            return ScheduleResult(
                ok = false,
                error = "เลยเวลาเป้าหมายแล้ว",
                nextTag = nextTag,
                nextAt = now()
            )
        }

        // Kinds list calculation logic
        val kinds = mutableListOf<Kind>()
        if (last != null) {
            // JS: if last exists, kinds[0] is last.kind (wait, JS logic says:)
            // const lastKind = (parseTag(last.tag)?.kind) || "IN";
            // kinds.push(lastKind);
            // for (let i = 1; i < chain.length; i++) kinds.push(...)
            // Wait, chain has pending. If last exists, the gap[0] is gap FROM last TO chain[0].
            // If last was IN, gap[0] is gap for IN (meaning duration of IN task). 
            // Correct.
            
            val lastKind = last.tag.parseTag()?.first ?: Kind.IN
            kinds.add(lastKind)
            for (i in 1 until chain.size) {
                 // gap for the PREVIOUS item in chain?
                 // JS: kinds.push(chain[i-1].kind)
                 // e.g. chain=[OUT, IN]. i=1. push chain[0].kind (OUT). gap[1] corresponds to duration of chain[0].
                 kinds.add(chain[i-1].kind)
            }
        } else {
            // JS: if no last
            // if chain.length == 1 -> immediate planAt = targetAt (handled below)
            // kinds loop: 0 until chain.length - 1
            // kinds.push(chain[i].kind)
            if (chain.size == 1) {
                return ScheduleResult(
                    ok = true,
                    nextTag = nextTag,
                    nextAt = targetAt,
                    planAtByTag = mapOf(chain[0].tag to targetAt)
                )
            }
            for (i in 0 until chain.size - 1) {
                kinds.add(chain[i].kind)
            }
        }

        var minSum = 0L
        var maxSum = 0L
        for (k in kinds) {
            minSum += (minGapMinutes(k) * 60000).toLong()
            maxSum += (maxGapMinutes(k) * 60000).toLong()
        }

        // Helper to plan with fixed MS array
        fun makePlan(gaps: List<Long>, errorMsg: String): ScheduleResult {
             val planMap = mutableMapOf<String, Long>()
             var t = baseTime
             if (last != null) {
                 // chain matching gaps size
                 for (i in chain.indices) {
                     // JS: t += gapsMs[i]
                     // wait, if chain.size > gaps.size ??
                     // JS logic ensures gaps len == chain len if last exists?
                     // kinds len was chain.length (1 from last + chain.len-1 from loop? NO)
                     // last: kinds pushed 1 + (chain.len - 1 times) = chain.len size. Correct.
                     if (i < gaps.size) {
                         t += gaps[i]
                         planMap[chain[i].tag] = t
                     }
                 }
             } else {
                 // no last. kinds len = chain.len - 1.
                 // planAtByTag.set(chain[0].id, baseTime)
                 planMap[chain[0].tag] = baseTime
                 for (i in 1 until chain.size) {
                     if (i - 1 < gaps.size) {
                         t += gaps[i - 1]
                         planMap[chain[i].tag] = t
                     }
                 }
             }

             
             // Convert map to list for UI
             val items = chain.map { p ->
                 ScheduleItem(p.tag, planMap[p.tag] ?: 0L, p.kind)
             }
             
             return ScheduleResult(
                 ok = false,
                 error = errorMsg,
                 nextTag = nextTag,
                 nextAt = planMap[chain[0].tag],
                 planAtByTag = planMap,
                 items = items
             )
        }

        if (totalMs > maxSum + 1) {
            val gaps = kinds.map { (maxGapMinutes(it) * 60000).toLong() }
            val msg = if (missingTag != null) "ขาดรูป $missingTag" else "รูปไม่พอถึงเป้าหมาย"
            return makePlan(gaps, msg)
        }

        if (totalMs < minSum - 1) {
            val gaps = kinds.map { (minGapMinutes(it) * 60000).toLong() }
            return makePlan(gaps, "เวลาน้อยเกินสำหรับจำนวนรูปในลำดับ")
        }

        // Exact Fit
        val nIn = kinds.count { it == Kind.IN }
        val nOut = kinds.count { it == Kind.OUT } // or size - nIn
        val totalMin = totalMs / 60000.0
        
        val solve = solveExactAB(totalMin, nIn, nOut)
        
        val rawGaps = kinds.map { k ->
            val basis = if (k == Kind.IN) (solve.a ?: 10.0) else (solve.b ?: 12.0)
            val clamped = max(minGapMinutes(k), min(maxGapMinutes(k), basis))
            (clamped * 60000).roundToLong()
        }.toMutableList()
        
        val finalGaps = distributeMsToMatchTotal(rawGaps, kinds, totalMs)
        
     // Final Plan Build
        val planMap = mutableMapOf<String, Long>()
        var t = baseTime
        if (last != null) {
             for (i in chain.indices) {
                 t += finalGaps[i]
                 planMap[chain[i].tag] = t
             }
        } else {
             planMap[chain[0].tag] = baseTime
             for (i in 1 until chain.size) {
                 t += finalGaps[i - 1]
                 planMap[chain[i].tag] = t
             }
        }
        
        // Convert map to list for UI
        val items = chain.map { p ->
            ScheduleItem(p.tag, planMap[p.tag] ?: 0L, p.kind)
        }
        
        return ScheduleResult(
            ok = true,
            warn = if (!solve.ok) "เฉลี่ยแบบ IN<OUT เป๊ะไม่ได้ (ใช้ค่าใกล้เคียง)" else "",
            nextTag = nextTag,
            nextAt = planMap[chain[0].tag],
            planAtByTag = planMap,
            items = items
        )
    }
}
