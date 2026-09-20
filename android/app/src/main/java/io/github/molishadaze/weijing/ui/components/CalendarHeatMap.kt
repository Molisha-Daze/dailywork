package io.github.molishadaze.weijing.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.molishadaze.weijing.model.DayProgress
import io.github.molishadaze.weijing.util.DateUtils

@Composable
fun CalendarHeatMap(
    progressList: List<DayProgress>,
    modifier: Modifier = Modifier,
    onDayClick: ((DayProgress) -> Unit)? = null
) {
    var selectedDay by remember { mutableStateOf<DayProgress?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打卡热力图 (近 35 天)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (selectedDay != null) {
                    Text(
                        text = "${selectedDay?.date}: ${selectedDay?.completedCount}/${selectedDay?.totalHabitsCount} 完成",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 7 columns (days of week) grid for 35 days
            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                userScrollEnabled = false
            ) {
                items(progressList) { day ->
                    val isToday = day.date == DateUtils.today()
                    val color = getHeatMapColor(day.ratio)

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(color)
                            .then(
                                if (isToday) Modifier.border(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.primary,
                                    RoundedCornerShape(6.dp)
                                ) else Modifier
                            )
                            .clickable {
                                selectedDay = day
                                onDayClick?.invoke(day)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        val dayNumber = try {
                            day.date.split("-").last().toInt().toString()
                        } catch (e: Exception) {
                            ""
                        }
                        Text(
                            text = dayNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (day.ratio > 0.4f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Heatmap Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "完成度: 低",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(0f, 0.25f, 0.5f, 0.75f, 1.0f).forEach { ratio ->
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(getHeatMapColor(ratio))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "高",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getHeatMapColor(ratio: Float): Color {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    return when {
        ratio <= 0f -> if (isDark) Color(0xFF262626) else Color(0xFFE5E7EB)
        ratio < 0.34f -> Color(0xFF6EE7B7) // Light Emerald
        ratio < 0.67f -> Color(0xFF10B981) // Medium Emerald
        ratio < 0.99f -> Color(0xFF059669) // Deep Emerald
        else -> Color(0xFF047857)          // 100% Full Emerald
    }
}
