package com.catlife.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catlife.app.R
import kotlin.math.roundToInt

internal enum class HomeGuideStep(val message: String) {
    TODO("やることを追加できます"),
    SHOPPING("買い物メモを追加できます"),
    SETTINGS("天気やサウンドを設定できます"),
}

/** Only the bubble receives taps; the rest of the home stays interactive. */
@Composable
internal fun HomeGuideBubble(
    step: HomeGuideStep,
    target: Rect,
    saving: Boolean,
    error: Boolean,
    onNext: () -> Unit,
) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val targetX = with(density) { target.center.x.toDp() }
        val settings = step == HomeGuideStep.SETTINGS
        // Asset: 197 x 113; tail tip at approximately (78, 103).
        // Normal orientation for both bottom buttons; vertical flip only for settings.
        val tailFraction = 78f / 197f
        val width = minOf(if (settings) 160.dp else 220.dp, maxWidth - 16.dp)
        val height = width * (113f / 197f)
        val left = (targetX - width * tailFraction).coerceIn(8.dp, maxWidth - width - 8.dp)
        val top = with(density) {
            if (settings) target.bottom.toDp() + 4.dp - height * (10f / 113f)
            else target.top.toDp() - 4.dp - height * (103f / 113f)
        }
        val message = step.message
            .replace("追加できます", "\n追加できます")
            .replace("設定できます", "\n設定できます")
        Box(
            Modifier.offset { IntOffset(with(density) { left.toPx().roundToInt() }, with(density) { top.toPx().roundToInt() }) }
                .width(width).height(height)
                .clickable(enabled = !saving, role = Role.Button,
                    onClickLabel = if (settings) "案内を完了" else "次の案内", onClick = onNext),
        ) {
            Image(
                painterResource(R.drawable.home_guide_bubble),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().graphicsLayer {
                    scaleY = if (settings) -1f else 1f
                },
                contentScale = ContentScale.FillBounds,
            )
            Column(
                Modifier.fillMaxWidth().height(height * (89f / 113f))
                    .offset(y = height * (if (settings) 19f / 113f else 5f / 113f))
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = colorResource(R.color.home_button_background),
                    fontSize = 16.sp, lineHeight = 20.sp, textAlign = TextAlign.Center)
                Text(
                    if (error) "保存を再試行" else if (saving) "保存中…"
                    else if (settings) "3/3 完了" else "${step.ordinal + 1}/3 次へ",
                    color = colorResource(R.color.ui_muted_content), fontSize = 12.sp,
                    lineHeight = 14.sp, fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
