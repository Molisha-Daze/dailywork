package io.github.molishadaze.weijing.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 音效与反馈门面的守门测试。
 *
 * ⚠️ 本测试**只能**碰纯数据：`Sounds.Sfx`、`Feedback.Event`、文件级纯函数 [isThrottled]，
 * 以及 `AppSettings` 的 `const` 常量。
 * 绝对不要在这里调用 `Sounds.play(...)` / `Haptics.play(...)` / `Feedback.fire(...)` ——
 * `Sounds` 的 object 初始化会构造 SoundPool 与 AudioAttributes，在 JVM 单测里
 * 一律抛 "Stub!" 异常（没有 Android 运行时）。而 `Sounds.Sfx` 与 `Feedback.Event`
 * 都是独立的静态嵌套枚举类，访问它们不会触发外层 object 的初始化，所以是安全的。
 *
 * 这也是为什么节流窗口逻辑被抽成了文件级纯函数 —— 它本来是引擎内部的一行判断，
 * 抽出来唯一的理由就是让这里能测。
 */
class SoundsTest {

    // ---------------------------------------------------------------- 资源映射

    @Test
    fun `每个音效都映射到非零且互不相同的资源 id`() {
        val ids = Sounds.Sfx.entries.map { it.resId }

        assertEquals(9L, ids.size.toLong())
        assertTrue(
            "存在 resId = 0 的音效，说明 R.raw 里没有对应文件，播放会静默失败",
            ids.all { it != 0 }
        )
        assertEquals(
            "有音效共用了同一个资源 id，多半是复制粘贴时漏改了 resId",
            ids.size.toLong(),
            ids.toSet().size.toLong()
        )
    }

    @Test
    fun `节流槽位一一独立`() {
        // 引擎用 sfx.ordinal 索引节流计时数组。若两个音效共用 ordinal
        // （将来重构枚举时可能发生），「连点计数器」就会把「恰好达标」那一声一起吞掉。
        val slots = Sounds.Sfx.entries.map { it.ordinal }
        assertEquals(slots.size.toLong(), slots.toSet().size.toLong())
        assertEquals(0L, slots.min().toLong())
        assertEquals((slots.size - 1).toLong(), slots.max().toLong())
    }

    @Test
    fun `细节音效恰好两个且都不是成就类`() {
        val detail = Sounds.Sfx.entries.filter { it.detail }

        assertEquals(2L, detail.size.toLong())
        assertEquals(
            "细节音效应当是「撤销」与「步进」这两个高频动作",
            listOf(Sounds.Sfx.UNDO, Sounds.Sfx.STEP_SOFT),
            detail
        )
        // 成就类音效绝对不能划进细节开关 —— 那会让主开关变成形同虚设。
        listOf(
            Sounds.Sfx.ACHIEVE_HABIT,
            Sounds.Sfx.ACHIEVE_PLAN,
            Sounds.Sfx.ACHIEVE_DAY,
            Sounds.Sfx.COUNTER_GOAL,
            Sounds.Sfx.COUNTER_LIMIT,
        ).forEach { assertFalse("$it 不该是细节音效", it.detail) }
    }

    @Test
    fun `默认开关是成就音开细节音关`() {
        assertTrue("成就音必须默认开启，否则功能等于没做", AppSettings.DEFAULT_SOUND_ENABLED)
        assertFalse(
            "细节音必须默认关闭：计数器连点十下就是十声「嗒」，重复短音会被判成噪音而非反馈",
            AppSettings.DEFAULT_SOUND_DETAIL_ENABLED
        )
    }

    // ---------------------------------------------------------------- 节流

    @Test
    fun `节流窗口内的第二次触发被拦下`() {
        val last = 1_000L
        // 典型场景：计数器连点，两次点击间隔 60ms —— 必须被吃掉，否则叠成机关枪。
        assertTrue(isThrottled(lastPlayedAt = last, now = last + 60))
        assertTrue(isThrottled(lastPlayedAt = last, now = last + 149))
    }

    @Test
    fun `超出窗口的触发放行`() {
        val last = 1_000L
        assertFalse(isThrottled(lastPlayedAt = last, now = last + 150))
        assertFalse(isThrottled(lastPlayedAt = last, now = last + 900))
    }

    @Test
    fun `节流窗口比触觉更长`() {
        // 音效有尾音（最长 1.05s），振动只有 12~32ms。若沿用触觉的 70ms，
        // 连点时两声尾音会叠在一起变成噪音 —— 这是刻意拉开的一处差异，别改成一样。
        assertTrue(
            "音效节流窗口必须长于 70ms（Haptics 的取值）",
            isThrottled(lastPlayedAt = 0L, now = 100L)
        )
    }

    // ---------------------------------------------------------------- 事件映射

    @Test
    fun `每个事件至少有一种反馈`() {
        Feedback.Event.entries.forEach { event ->
            assertTrue(
                "$event 既没触觉也没音效，等于这个动作被静默丢掉了",
                event.haptic != null || event.sfx != null
            )
        }
    }

    @Test
    fun `成就类事件映射到对应的音效`() {
        assertEquals(Sounds.Sfx.ACHIEVE_HABIT, Feedback.Event.HABIT_DONE.sfx)
        assertEquals(Sounds.Sfx.ACHIEVE_PLAN, Feedback.Event.PLAN_DONE.sfx)
        assertEquals(Sounds.Sfx.ACHIEVE_DAY, Feedback.Event.DAY_DONE.sfx)
        assertEquals(Sounds.Sfx.COUNTER_GOAL, Feedback.Event.COUNTER_GOAL.sfx)
        assertEquals(Sounds.Sfx.COUNTER_LIMIT, Feedback.Event.COUNTER_LIMIT.sfx)
    }

    @Test
    fun `全天完成是最高等级的成就`() {
        // 全天完成必须强振 —— 它比单个计划完成更重，两者同强度会让「今天全干完了」
        // 这个信号被稀释成和「又完成一项」一样。
        assertEquals(Haptics.Level.STRONG, Feedback.Event.DAY_DONE.haptic)
        assertEquals(Haptics.Level.STRONG, Feedback.Event.PLAN_DONE.haptic)
    }

    @Test
    fun `取消类动作只给最轻的反馈`() {
        listOf(
            Feedback.Event.HABIT_UNDONE,
            Feedback.Event.COUNTER_BACK,
            Feedback.Event.SUBTASK_TOGGLED,
            Feedback.Event.COUNTER_STEP,
        ).forEach {
            assertEquals("$it 是撤销/步进语义，不该给强反馈", Haptics.Level.LIGHT, it.haptic)
        }
    }

    @Test
    fun `破坏性操作是中等强度且音效不是成就音`() {
        assertEquals(Haptics.Level.MEDIUM, Feedback.Event.CLEARED.haptic)
        // 清零/删除绝不能借用成就音 —— 那等于在鼓励用户多删数据。
        assertEquals(Sounds.Sfx.CLEARED, Feedback.Event.CLEARED.sfx)
        assertTrue(
            "破坏性操作的音效不能和任何一个成就音重复",
            Feedback.Event.CLEARED.sfx !in setOf(
                Sounds.Sfx.ACHIEVE_HABIT,
                Sounds.Sfx.ACHIEVE_PLAN,
                Sounds.Sfx.ACHIEVE_DAY,
                Sounds.Sfx.COUNTER_GOAL,
                Sounds.Sfx.COUNTER_LIMIT,
            )
        )
    }

    @Test
    fun `备份恢复只出声不振动`() {
        // 用户此刻正盯着屏幕，振动没有额外信息量；而「数据回来了」值得一声明确的收尾。
        assertNull(Feedback.Event.RESTORED.haptic)
        assertNotNull(Feedback.Event.RESTORED.sfx)
    }

    @Test
    fun `导航类动作没有被定义成事件`() {
        // 这是一条**反向**断言，防止有人「顺手」给 tab 切换、翻月、FAB 也加音效。
        // 名字里出现这些词的枚举项一旦冒出来，说明有人越过了设计边界。
        val forbidden = listOf("TAB", "NAVIGATE", "SCROLL", "FAB", "DIALOG", "MONTH")
        Feedback.Event.entries.forEach { event ->
            forbidden.forEach { word ->
                assertFalse(
                    "事件 ${event.name} 疑似导航类动作；音效只给成就与确认时刻，导航必须无声",
                    event.name.contains(word)
                )
            }
        }
    }
}
