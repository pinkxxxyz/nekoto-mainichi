package com.catlife.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private val DueBrown = Color(0xFF755A42)
private val DueCream = Color(0xFFFFF7E9)
private val DueSelected = Color(0xFFFFDDA3)
private val DueSelectedText = Color(0xFF5B402E)
private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

internal fun selectedTimeWheelIndex(
    visibleItems: List<LazyListItemInfo>,
    viewportStartOffset: Int,
    viewportEndOffset: Int,
    firstVisibleItemIndex: Int,
): Int {
    val viewportCenter = (viewportStartOffset + viewportEndOffset) / 2f
    return visibleItems.minByOrNull { item ->
        abs(item.offset + item.size / 2f - viewportCenter)
    }?.index ?: firstVisibleItemIndex
}

private fun LazyListState.centeredTimeIndex(): Int {
    val layout = layoutInfo
    return selectedTimeWheelIndex(
        layout.visibleItemsInfo,
        layout.viewportStartOffset,
        layout.viewportEndOffset,
        firstVisibleItemIndex,
    )
}

internal fun formatTodoPickerTime(hour: Int, minute: Int): String =
    LocalTime.of(hour, minute).format(TimeFormatter)

internal data class TodoDueDraft(
    val date: String?,
    val time: String?,
    val pendingDate: LocalDate? = null,
) {
    fun beginDateSelection(selectedDate: LocalDate) = copy(pendingDate = selectedDate)

    fun cancelDateSelection() = copy(pendingDate = null)

    fun commitDateSelection(selectedTime: String?): TodoDueDraft =
        pendingDate?.let { TodoDueDraft(date = it.toString(), time = selectedTime) } ?: this
}

internal fun formatTodoDueSummary(date: String?, time: String?): String? {
    val rawDate = date?.takeIf { it.isNotBlank() } ?: return null
    val parsedDate = runCatching { LocalDate.parse(rawDate) }.getOrNull()
    val dateText = parsedDate?.let { "${it.year}年${it.monthValue}月${it.dayOfMonth}日" } ?: rawDate
    val normalizedTime = time?.takeIf { it.isNotBlank() }?.take(5)
    return if (normalizedTime == null) "$dateText\n時間指定なし" else "$dateText $normalizedTime"
}

@Composable
internal fun TodoDatePickerDialog(
    selectedDate: LocalDate?,
    onDismiss: () -> Unit,
    onDateSelected: (LocalDate) -> Unit,
    onClear: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var displayedMonth by remember(selectedDate) {
        mutableStateOf(YearMonth.from(selectedDate ?: today))
    }
    val firstDayOffset = displayedMonth.atDay(1).dayOfWeek.value - 1
    val cellCount = firstDayOffset + displayedMonth.lengthOfMonth()
    val rowCount = (cellCount + 6) / 7

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.width(340.dp),
            color = DueCream,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 4.dp,
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { displayedMonth = displayedMonth.minusMonths(1) }) {
                        Text("‹", fontSize = 26.sp, color = DueBrown)
                    }
                    Text(
                        "${displayedMonth.year}年 ${displayedMonth.monthValue}月",
                        fontWeight = FontWeight.Bold,
                        color = DueBrown,
                    )
                    TextButton(onClick = { displayedMonth = displayedMonth.plusMonths(1) }) {
                        Text("›", fontSize = 26.sp, color = DueBrown)
                    }
                }
                Row(Modifier.fillMaxWidth()) {
                    listOf("月", "火", "水", "木", "金", "土", "日").forEach { label ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(label, fontSize = 12.sp, color = DueBrown.copy(alpha = 0.75f))
                        }
                    }
                }
                repeat(rowCount) { row ->
                    Row(Modifier.fillMaxWidth()) {
                        repeat(7) { column ->
                            val day = row * 7 + column - firstDayOffset + 1
                            val date = day.takeIf { it in 1..displayedMonth.lengthOfMonth() }
                                ?.let(displayedMonth::atDay)
                            Box(
                                modifier = Modifier.weight(1f).height(36.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (date != null) {
                                    val isSelected = date == selectedDate
                                    val isToday = date == today
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .then(
                                                if (isToday && !isSelected) {
                                                    Modifier.border(1.dp, DueBrown, RoundedCornerShape(12.dp))
                                                } else Modifier
                                            )
                                            .background(
                                                if (isSelected) DueSelected else Color.Transparent,
                                                RoundedCornerShape(12.dp),
                                            )
                                            .clickable { onDateSelected(date) },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            day.toString(),
                                            color = DueBrown,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onClear) { Text("期日をクリア", color = DueBrown) }
                    TextButton(onClick = onDismiss) { Text("閉じる", color = DueBrown) }
                }
            }
        }
    }
}

@Composable
internal fun TodoTimePickerDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    onNoTime: () -> Unit,
    title: String = "時間を選択",
    allowNoTime: Boolean = true,
) {
    val minuteValues = remember { (0..55 step 5).toList() }
    val roundedMinute = ((initialTime.minute + 2) / 5 * 5).coerceAtMost(55)
    val hourValues = remember { (0..23).toList() }
    val hourState = rememberLazyListState(initialFirstVisibleItemIndex = initialTime.hour)
    val minuteState = rememberLazyListState(
        initialFirstVisibleItemIndex = minuteValues.indexOf(roundedMinute),
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 310.dp),
            color = DueCream,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 4.dp,
        ) {
            Column(
                Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = DueBrown, fontSize = 20.sp)
                Box(
                    modifier = Modifier.width(220.dp).height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(42.dp)
                            .background(DueSelected.copy(alpha = 0.72f), RoundedCornerShape(14.dp)),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimeWheel(
                            values = hourValues,
                            state = hourState,
                        )
                        Box(Modifier.width(36.dp), contentAlignment = Alignment.Center) {
                            Text("：", color = DueSelectedText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                        }
                        TimeWheel(
                            values = minuteValues,
                            state = minuteState,
                        )
                    }
                }
                Row(Modifier.width(220.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(92.dp), contentAlignment = Alignment.Center) {
                        Text("時", color = DueBrown.copy(alpha = 0.68f), fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(36.dp))
                    Box(Modifier.width(92.dp), contentAlignment = Alignment.Center) {
                        Text("分", color = DueBrown.copy(alpha = 0.68f), fontSize = 12.sp)
                    }
                }
                if (allowNoTime) {
                    TextButton(
                        onClick = onNoTime,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text(
                            "時間は指定しない",
                            color = DueBrown,
                            fontWeight = FontWeight.Medium,
                            textDecoration = TextDecoration.Underline,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp),
                    ) {
                        Text("戻る", color = DueBrown, fontWeight = FontWeight.Medium)
                    }
                    TextButton(
                        onClick = {
                            onConfirm(formatTodoPickerTime(
                                hourValues[hourState.centeredTimeIndex().coerceIn(hourValues.indices)],
                                minuteValues[minuteState.centeredTimeIndex().coerceIn(minuteValues.indices)],
                            ))
                        },
                        modifier = Modifier.heightIn(min = 48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                            containerColor = DueSelected.copy(alpha = 0.72f),
                            contentColor = DueSelectedText,
                        ),
                    ) {
                        Text("確定", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeWheel(
    values: List<Int>,
    state: LazyListState,
) {
    val scope = rememberCoroutineScope()
    val selectedIndex by remember(state, values) {
        derivedStateOf { state.centeredTimeIndex().coerceIn(values.indices) }
    }

    Box(
        modifier = Modifier.width(92.dp).height(120.dp),
        contentAlignment = Alignment.Center,
    ) {
        LazyColumn(
            state = state,
            modifier = Modifier.fillMaxWidth().height(120.dp),
            contentPadding = PaddingValues(vertical = 40.dp),
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state),
        ) {
            itemsIndexed(values) { index, value ->
                Box(
                    modifier = Modifier.fillMaxWidth().height(40.dp)
                        .clickable { scope.launch { state.animateScrollToItem(index) } },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "%02d".format(value),
                        fontSize = if (index == selectedIndex) 24.sp else 17.sp,
                        fontWeight = if (index == selectedIndex) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (index == selectedIndex) DueSelectedText else DueBrown.copy(alpha = 0.72f),
                    )
                }
            }
        }
    }
}
