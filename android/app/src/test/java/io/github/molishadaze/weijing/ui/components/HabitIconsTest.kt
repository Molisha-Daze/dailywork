package io.github.molishadaze.weijing.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 自绘图标的回归测试：每个图标都必须**真的带路径节点**。
 *
 * 起因 —— 曾经把 `addPathNodes(d)` 当成语句写在 `path { }` 的尾随 lambda 里。
 * 库里 `addPathNodes` 是普通函数 `(String) -> List<PathNode>`，**不是** PathBuilder 的扩展，
 * 于是返回值被静默丢弃：编译零警告、单测全绿、APK 正常，
 * 但全部 35 个自绘图标渲染出来都是一片空白。
 *
 * 当时的验证只做到「能编译」+「d 串与源 SVG 逐字节一致」，两条都没碰到真正的渲染路径。
 * 这个测试直接数 [ImageVector] 里的节点数，正好卡在被漏掉的那条路径上。
 */
class HabitIconsTest {

    /** 数一个矢量图里的路径节点总数（递归进子 group）。 */
    private fun nodeCount(vector: ImageVector): Int = count(group = vector.root)

    private fun count(group: VectorGroup): Int = group.sumOf { node ->
        when (node) {
            is VectorPath -> node.pathData.size
            is VectorGroup -> count(node)
            else -> 0
        }
    }

    /** 反射取出 holder 里所有 ImageVector 属性，避免手抄清单漏项。 */
    private fun iconsOf(holder: Any): List<Pair<String, ImageVector>> =
        holder.javaClass.methods
            .filter {
                it.parameterCount == 0 &&
                    it.returnType == ImageVector::class.java &&
                    it.name.startsWith("get")
            }
            .map { method ->
                method.name.removePrefix("get") to (method.invoke(holder) as ImageVector)
            }

    @Test
    fun `自绘图标必须带路径节点`() {
        val all = iconsOf(UiIcons) + iconsOf(HabitIcons)

        // 先保证反射本身没坏，否则下面那条空断言会「假绿」。
        assertTrue("反射没扫到图标，测试自身失效（只扫到 ${all.size} 个）", all.size >= 30)

        val blank = all.filter { nodeCount(it.second) == 0 }.map { it.first }
        assertEquals("这些图标是空壳，渲染出来会是一片空白: $blank", emptyList<String>(), blank)
    }

    @Test
    fun `getIconVector 的所有 key 都解析成非空矢量图`() {
        val keys = listOf(
            "Run", "Book", "Water", "Fitness", "Meditation", "Bike", "Sleep",
            "Meal", "Work", "Medicine", "Money", "Music", "Walk", "Pet", "Sun",
        )
        val blank = keys.filter { nodeCount(getIconVector(it)) == 0 }
        assertEquals("这些 key 解析出的图标是空壳: $blank", emptyList<String>(), blank)

        // 未登记的 key 必须回退到「带节点的 Star」，而不是留空。
        assertTrue(
            "未知 iconName 应回退到带节点的 Star",
            nodeCount(getIconVector("__definitely_not_a_key__")) > 0,
        )
    }

    @Test
    fun `PRESET_ICONS 的 key 与 getIconVector 一一对应`() {
        val mismatch = PRESET_ICONS
            .filter { (key, vector) -> getIconVector(key) !== vector }
            .map { it.first }
        assertEquals("这些候选图标的 key 与 getIconVector 对不上: $mismatch", emptyList<String>(), mismatch)
    }

    /**
     * 反向探针：证明上面那几条测试**不是空转**。
     *
     * 这里刻意复现当初写错的那一行（把 `addPathNodes(d)` 当语句丢进尾随 lambda），
     * 断言它产出的是**空路径**。如果哪天这条断言挂了，说明 Compose 改了 API 行为 ——
     * 那时 HabitIcons.kt 里的那段警示注释就该重写了，不是代码坏了。
     */
    @Test
    fun `探针：把 addPathNodes 写在尾随 lambda 里会静默产出空路径`() {
        val buggy = ImageVector.Builder(
            name = "Probe",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                addPathNodes("M19 13H5v-2h14v2z") // 返回值被丢弃，节点一个都没进去
            }
        }.build()

        assertEquals(
            "Compose 的 API 行为变了：现在这种写法居然能进节点。请更新 HabitIcons.kt 的注释。",
            0,
            nodeCount(buggy),
        )
    }
}
