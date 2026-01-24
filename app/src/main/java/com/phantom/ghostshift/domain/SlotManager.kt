package com.phantom.ghostshift.domain

object SlotManager {
    fun computeNextSlot(photos: List<SchedulePhoto>): String {
        val occ = mutableMapOf<Int, Pair<Boolean, Boolean>>() // idx -> (hasIN, hasOUT)
        
        for (p in photos) {
            val current = occ.getOrDefault(p.idx, false to false)
            val updated = if (p.kind == Kind.IN) {
                current.copy(first = true)
            } else {
                current.copy(second = true)
            }
            occ[p.idx] = updated
        }
        
        var i = 1
        while (true) {
            val (hasIN, hasOUT) = occ.getOrDefault(i, false to false)
            if (!hasIN) return "IN-$i"
            if (!hasOUT) return "OUT-$i"
            i++
        }
    }
    
    fun nextTagInSequence(tag: String): String? {
        val (kind, idx) = tag.parseTag() ?: return null
        return if (kind == Kind.IN) {
            "OUT-$idx"
        } else {
            "IN-${idx + 1}"
        }
    }
}
