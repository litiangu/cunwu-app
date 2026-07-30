package com.litiangu.wuji.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class HealthScoreTest {

    // 草案第四节示例：单件已售 iPhone。v0.4 权重(0.50/0.40/0.05/0.05)下 ≈43.6，仍最低档。
    @Test
    fun iphoneExample_matchesDraft() {
        val items = listOf(
            ItemInput(
                isInUse = false,
                isSold = true,
                purchasePrice = 8999.0,
                extraCost = 0.0,
                resaleValue = 2000.0,
                companionshipDays = 1825
            )
        )
        val r = computeHealthScore(items)
        assertEquals(43.6, r.total, 0.2)
        assertEquals(HealthGrade.POOR, r.grade)
        assertEquals("断舍离时刻", r.comment)
        // 四维子分核对
        assertEquals(0.0, r.dimensions.d1Usage, 0.01)
        assertEquals(100.0, r.dimensions.d2ValueHealth, 0.01)
        assertEquals(50.0, r.dimensions.d3Turnover, 0.01)
        assertEquals(22.2, r.dimensions.d4Recovery, 0.1)
    }

    // 空集合不崩，给最低档 0 分。
    @Test
    fun emptyCollection_isZeroPoor() {
        val r = computeHealthScore(emptyList())
        assertEquals(0.0, r.total, 0.001)
        assertEquals(HealthGrade.POOR, r.grade)
    }

    // 全部使用中且便宜：D1=100、D2=100、D3=0、D4=0 → 0.50*100+0.40*100 = 90（优）。
    // 正是 v0.4 权重调整要达成的结果：高利用率 + 高价值健康即可到「优」，无需售出/变现。
    @Test
    fun allInUseCheap_isExcellent() {
        val items = List(10) {
            ItemInput(
                isInUse = true,
                isSold = false,
                purchasePrice = 100.0,
                extraCost = 0.0,
                resaleValue = 0.0,
                companionshipDays = 365
            )
        }
        val r = computeHealthScore(items)
        assertEquals(90.0, r.total, 0.001)
        assertEquals(HealthGrade.EXCELLENT, r.grade)
        assertEquals("少即是多", r.comment)
    }

    // 中高利用率(60%)+便宜：D1=60、D2=100、D3=0、D4=0 → 0.50*60+0.40*100 = 70（良）。
    @Test
    fun highUsageDecentValue_isGood() {
        val items = List(10) {
            ItemInput(
                isInUse = it < 6,        // 6/10 使用中
                isSold = false,
                purchasePrice = 100.0,
                extraCost = 0.0,
                resaleValue = 0.0,
                companionshipDays = 365
            )
        }
        val r = computeHealthScore(items)
        assertEquals(70.0, r.total, 0.001)
        assertEquals(HealthGrade.GOOD, r.grade)
        assertEquals("张弛有度", r.comment)
    }

    // 零持有成本（净投入≤0）→ D2 满分，不应除零。v0.4 权重下 0.50*100+0.40*100=90。
    @Test
    fun zeroCost_notDivByZero() {
        val items = listOf(
            ItemInput(
                isInUse = true,
                isSold = false,
                purchasePrice = 0.0,
                extraCost = 0.0,
                resaleValue = 0.0,
                companionshipDays = 100
            )
        )
        val r = computeHealthScore(items)
        assertEquals(100.0, r.dimensions.d2ValueHealth, 0.001)
        assertEquals(90, r.totalRounded)
    }

    // D3 流转率封顶 50%：全部已售也只到 50，不会更高。
    @Test
    fun turnoverCappedAt50() {
        val items = List(5) {
            ItemInput(
                isInUse = false,
                isSold = true,
                purchasePrice = 100.0,
                extraCost = 0.0,
                resaleValue = 0.0,
                companionshipDays = 100
            )
        }
        val r = computeHealthScore(items)
        assertEquals(50.0, r.dimensions.d3Turnover, 0.001)
    }

    // D4 变现回收封顶 60%：回收率远超 60% 也只到 60。
    @Test
    fun recoveryCappedAt60() {
        val items = listOf(
            ItemInput(
                isInUse = false,
                isSold = true,
                purchasePrice = 100.0,
                extraCost = 0.0,
                resaleValue = 500.0, // 回收率 5.0
                companionshipDays = 100
            )
        )
        val r = computeHealthScore(items)
        assertEquals(60.0, r.dimensions.d4Recovery, 0.001)
    }

    // 等级阈值边界（四档 85/70/50）。v0.4 权重下 EXCELLENT 已可达（见 allInUseCheap_isExcellent），
    // 此测试仅校验阈值函数本身的正确性，供后续调阈值/权重时复用。
    @Test
    fun gradeThresholds() {
        assertEquals(HealthGrade.EXCELLENT, gradeFromScore(85.0))
        assertEquals(HealthGrade.GOOD, gradeFromScore(84.9))
        assertEquals(HealthGrade.GOOD, gradeFromScore(70.0))
        assertEquals(HealthGrade.MEDIUM, gradeFromScore(69.9))
        assertEquals(HealthGrade.MEDIUM, gradeFromScore(50.0))
        assertEquals(HealthGrade.POOR, gradeFromScore(49.9))
    }
}
