package com.catlife.app

import androidx.compose.foundation.lazy.LazyListItemInfo
import com.catlife.app.ui.formatTodoPickerTime
import com.catlife.app.ui.selectedTimeWheelIndex
import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWheelSelectionTest {
    private fun item(index: Int, offset: Int, size: Int = 40) = object : LazyListItemInfo {
        override val index = index
        override val offset = offset
        override val size = size
        override val key: Any = index
    }

    @Test fun highlightedHourAndMinuteAreConfirmedInsteadOfUpperRow() {
        val hour = selectedTimeWheelIndex(
            listOf(item(20, -40), item(21, 0), item(22, 40)), -40, 80, 20,
        )
        val minuteIndex = selectedTimeWheelIndex(
            listOf(item(10, -40), item(11, 0)), -40, 80, 10,
        )
        assertEquals("21:55", formatTodoPickerTime(hour, minuteIndex * 5))
    }

    @Test fun initialLayoutWithPaddingSelectsInitialValue() {
        assertEquals(8, selectedTimeWheelIndex(
            listOf(item(7, -40), item(8, 0), item(9, 40)), -40, 80, 8,
        ))
    }

    @Test fun draggingSelectsNearestCenterBeforeAndAfterHalfRow() {
        assertEquals(8, selectedTimeWheelIndex(
            listOf(item(7, -59), item(8, -19), item(9, 21)), -40, 80, 8,
        ))
        assertEquals(9, selectedTimeWheelIndex(
            listOf(item(7, -61), item(8, -21), item(9, 19)), -40, 80, 8,
        ))
    }

    @Test fun snappedAndBoundaryRowsUseCenterIncludingPadding() {
        assertEquals(0, selectedTimeWheelIndex(
            listOf(item(0, 0), item(1, 40)), -40, 80, 0,
        ))
        assertEquals(23, selectedTimeWheelIndex(
            listOf(item(22, -40), item(23, 0)), -40, 80, 22,
        ))
        assertEquals(11, selectedTimeWheelIndex(
            listOf(item(10, -120, 120), item(11, 0, 120)), -120, 240, 10,
        ))
    }

    @Test fun beforeLayoutUsesInitialIndex() {
        assertEquals(21, selectedTimeWheelIndex(emptyList(), 0, 0, 21))
    }
}
