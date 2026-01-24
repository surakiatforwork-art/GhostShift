package com.phantom.ghostshift.domain

object UnlockRules {
    /**
     * Checks if there is a contiguous sequence of Pairs (IN+OUT) from 1 to N,
     * where N >= 20.
     * Returns N (maxPair) if eligible, or 0 if not.
     */
    fun getUnlockMaxPairIfEligible(photos: List<SchedulePhoto>): Int {
        val map = mutableMapOf<Int, Pair<Boolean, Boolean>>()
        
        for (p in photos) {
            val current = map.getOrDefault(p.idx, false to false)
            val updated = if (p.kind == Kind.IN) {
                current.copy(first = true)
            } else {
                current.copy(second = true)
            }
            map[p.idx] = updated
        }
        
        var maxPair = 0
        for ((idx, status) in map) {
            if (status.first && status.second) {
                if (idx > maxPair) maxPair = idx
            }
        }
        
        if (maxPair < 20) return 0
        
        // Strict continuity check: 1..maxPair must all exist
        for (i in 1..maxPair) {
            val status = map[i]
            if (status == null || !status.first || !status.second) {
                return 0
            }
        }
        
        return maxPair
    }
}
