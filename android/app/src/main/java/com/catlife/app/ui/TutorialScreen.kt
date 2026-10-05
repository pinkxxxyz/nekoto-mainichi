@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.catlife.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import com.catlife.app.R
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class TutorialPage(val title: String, val description: String, val extra: String? = null)
private val TutorialPages = listOf(
    TutorialPage("ようこそ", "猫と一緒に、毎日のやることや買い物を管理できます。"),
    TutorialPage("猫との暮らし", "猫をタップすると、気まぐれに鳴いてくれます。", "設定から「猫の音声」「効果音」を変更できます。"),
    TutorialPage("やること・買い物", "やることには期日と時間を設定できます。\n時間を設定すると、リマインダーでお知らせします。", "「買い物」では買うものを登録できます。"),
    TutorialPage("設定", "現在地の地域設定や、データのバックアップ・インポートができます。", "バックアップはGoogle Driveなどに保存できます。"),
)

@Composable
internal fun TutorialScreen(
    manuallyOpened: Boolean,
    saving: Boolean,
    error: String?,
    onFinish: (Int) -> Unit,
    onCloseManual: () -> Unit,
) {
    val pager = rememberPagerState(pageCount = { TutorialPages.size })
    val scope = rememberCoroutineScope()
    val charcoal = colorResource(R.color.home_button_background)
    val controlsEnabled = !saving && !pager.isScrollInProgress
    BackHandler(enabled = pager.currentPage > 0 || manuallyOpened || saving) {
        if (!saving) {
            if (pager.currentPage > 0) scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
            else if (manuallyOpened) onCloseManual()
        }
    }
    Surface(Modifier.fillMaxSize(), color = colorResource(R.color.home_background)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), contentAlignment = Alignment.Center) {
            Surface(
                Modifier.widthIn(max = 560.dp).fillMaxWidth().fillMaxHeight(),
                color = colorResource(R.color.home_card_background),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 2.dp,
            ) {
                Column(Modifier.padding(horizontal = 24.dp, vertical = 20.dp)) {
                    Text("KorokkeLife", color = charcoal, style = MaterialTheme.typography.labelLarge)
                    HorizontalPager(
                        state = pager,
                        userScrollEnabled = !saving,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    ) { index ->
                        val page = TutorialPages[index]
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 28.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            Text(page.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = charcoal)
                            Text(page.description, style = MaterialTheme.typography.bodyLarge, color = charcoal)
                            page.extra?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = charcoal) }
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp)
                            .semantics { contentDescription = "${pager.currentPage + 1} / ${TutorialPages.size}ページ" },
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                    ) {
                        repeat(TutorialPages.size) { index ->
                            Box(Modifier.size(9.dp).background(if (index == pager.currentPage) charcoal else charcoal.copy(alpha = 0.2f), CircleShape))
                        }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 8.dp)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        if (pager.currentPage > 0) {
                            TextButton(enabled = controlsEnabled, onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("戻る") }
                        } else Spacer(Modifier.width(1.dp))
                        Button(
                            enabled = controlsEnabled,
                            onClick = {
                                if (pager.currentPage == TutorialPages.lastIndex) onFinish(pager.currentPage)
                                else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = charcoal),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                        ) {
                            Text(if (pager.currentPage == TutorialPages.lastIndex) "はじめる" else "次へ")
                        }
                    }
                    if (saving) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                }
            }
        }
    }
}
