@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.catlife.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import io.github.sceneview.Scene
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.node.ModelNode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.colorResource
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.catlife.app.ui.appColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.catlife.app.cat.*
import com.catlife.app.data.*
import com.catlife.app.ui.SettingsPanel
import com.catlife.app.ui.TutorialScreen
import com.catlife.app.settings.TutorialSettings
import com.catlife.app.settings.korokkeLifeTutorialStore
import com.catlife.app.settings.shouldShowTutorial
import com.catlife.app.settings.LocationCatalog
import com.catlife.app.settings.LocationSettings
import com.catlife.app.ui.AppAssets
import com.catlife.app.ui.CatAnimationPlayback
import com.catlife.app.ui.KorokkeTypography
import com.catlife.app.ui.TodoDatePickerDialog
import com.catlife.app.ui.TodoDueDraft
import com.catlife.app.ui.TodoTimePickerDialog
import com.catlife.app.ui.formatTodoDueSummary
import com.catlife.app.ui.rememberCatVoiceController
import com.catlife.app.ui.rememberDecisionSoundController
import com.catlife.app.settings.CatVoiceEnabledKey
import com.catlife.app.settings.korokkeLifeDataStore
import com.catlife.app.reminder.canScheduleExactTodoAlarms
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.sceneview.rememberCameraManipulator
import com.google.android.filament.utils.Manipulator
import java.time.LocalDate
import java.time.LocalTime

private val KorokkeFont = FontFamily(
    Font(com.catlife.app.R.font.korotan)
)

private val soundEnabledKey = booleanPreferencesKey("sound_enabled")
private enum class Panel { TODO, SHOPPING, SETTINGS }

private const val WALK_ANIMATION_INDEX = 0
private const val IDLE_PURR_ANIMATION_INDEX = 1
private const val LICK_CLEAN_ANIMATION_INDEX = 2
private const val MEOW_ANIMATION_INDEX = 3
private const val MEOW_FALLBACK_MILLIS = 900L
private const val CAT_MODEL = "models/korokke_with_fur.glb"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = appColorScheme(),
                typography = KorokkeTypography.run {
                    copy(
                        displayLarge = displayLarge.copy(fontFamily = KorokkeFont),
                        displayMedium = displayMedium.copy(fontFamily = KorokkeFont),
                        displaySmall = displaySmall.copy(fontFamily = KorokkeFont),
                        headlineLarge = headlineLarge.copy(fontFamily = KorokkeFont),
                        headlineMedium = headlineMedium.copy(fontFamily = KorokkeFont),
                        headlineSmall = headlineSmall.copy(fontFamily = KorokkeFont),
                        titleLarge = titleLarge.copy(fontFamily = KorokkeFont),
                        titleMedium = titleMedium.copy(fontFamily = KorokkeFont),
                        titleSmall = titleSmall.copy(fontFamily = KorokkeFont),
                        bodyLarge = bodyLarge.copy(fontFamily = KorokkeFont),
                        bodyMedium = bodyMedium.copy(fontFamily = KorokkeFont),
                        bodySmall = bodySmall.copy(fontFamily = KorokkeFont),
                        labelLarge = labelLarge.copy(fontFamily = KorokkeFont),
                        labelMedium = labelMedium.copy(fontFamily = KorokkeFont),
                        labelSmall = labelSmall.copy(fontFamily = KorokkeFont)
                    )
                }
            ) {
                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
                    KorokkeLifeApp()
                }
            }
        }
    }
}

@Composable
private fun KorokkeLifeApp(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val backupService = (context.applicationContext as KorokkeLifeApplication).backupService
    val recoveryRequired by backupService.recoveryRequired.collectAsState()
    var recoveryReady by remember { mutableStateOf(false) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var recoveryAttempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(backupService, recoveryAttempt, recoveryRequired) {
        recoveryError = null
        try {
            backupService.recover()
            recoveryReady = true
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            recoveryError = "中断した復元の回復に失敗しました。空き容量などを確認して再試行してください。"
        }
    }
    if (!recoveryReady || recoveryRequired) {
        Box(Modifier.fillMaxSize().background(colorResource(R.color.home_background)), contentAlignment = Alignment.Center) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                if (recoveryError == null) CircularProgressIndicator()
                else {
                    Text(recoveryError!!)
                    TextButton(onClick = { recoveryAttempt++ }) { Text("再試行") }
                }
            }
        }
        return
    }
    val locationCatalog by produceState<LocationCatalog?>(initialValue = null, context) {
        value = withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.assets.open("locations/regions.tsv").bufferedReader().use(LocationCatalog::read)
        }
    }
    val locationSettings = remember(context, locationCatalog) {
        LocationSettings(context.korokkeLifeDataStore, locationCatalog)
    }
    val weatherLocation by locationSettings.location.collectAsState(initial = null)
    val tutorialSettings = remember(context) { TutorialSettings(context.korokkeLifeTutorialStore) }
    val tutorialDone by tutorialSettings.completed.map { it as Boolean? }.collectAsState(initial = null)
    var manualTutorial by rememberSaveable { mutableStateOf(false) }
    var savingTutorial by remember { mutableStateOf(false) }
    var tutorialError by remember { mutableStateOf<String?>(null) }
    val soundEnabled by context.korokkeLifeDataStore.data.map { it[soundEnabledKey] ?: true }.collectAsState(initial = true)
    val catVoiceEnabled by context.korokkeLifeDataStore.data.map { it[CatVoiceEnabledKey] ?: true }.collectAsState(initial = true)
    var panel by rememberSaveable { mutableStateOf<Panel?>(null) }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateAsState().value
    val catActive = lifecycle.isAtLeast(Lifecycle.State.RESUMED) && panel == null && tutorialDone == true && !manualTutorial
    LaunchedEffect(lifecycle, panel, tutorialDone, manualTutorial) {
        android.util.Log.d("CatAction", "ACTIVE INPUT active=$catActive lifecycle=$lifecycle panel=$panel tutorialDone=$tutorialDone manualTutorial=$manualTutorial")
    }
    val scope = rememberCoroutineScope()
    val decisionSound = rememberDecisionSoundController()
    val playDecisionSound = { decisionSound.play(soundEnabled) }

    if (tutorialDone == null) {
        Box(Modifier.fillMaxSize().background(colorResource(R.color.home_background)), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (shouldShowTutorial(tutorialDone == true, manualTutorial)) {
        TutorialScreen(
            manuallyOpened = manualTutorial,
            saving = savingTutorial,
            error = tutorialError,
            onFinish = { pageIndex ->
                savingTutorial = true
                tutorialError = null
                scope.launch {
                    try {
                        tutorialSettings.complete(pageIndex)
                        manualTutorial = false
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        tutorialError = "完了状態を保存できませんでした。もう一度お試しください。"
                    } finally { savingTutorial = false }
                }
            },
            onCloseManual = { manualTutorial = false },
        )
        return
    }
    Box(Modifier.fillMaxSize().background(colorResource(R.color.home_background))) {
        Home(
            onTodo = { playDecisionSound(); panel = Panel.TODO },
            onShopping = { playDecisionSound(); panel = Panel.SHOPPING },
            onSettings = { playDecisionSound(); panel = Panel.SETTINGS },
            catActive = catActive,
            catVoiceEnabled = catVoiceEnabled,
            tutorial = false,
            weatherLocation = weatherLocation
        )
        when (panel) {
            Panel.TODO -> TodoPanel(vm, playDecisionSound) { panel = null }
            Panel.SHOPPING -> ShoppingPanel(vm, playDecisionSound) { panel = null }
            Panel.SETTINGS -> SettingsPanel(
                catVoiceEnabled = catVoiceEnabled,
                onCatVoiceEnabledChange = { enabled ->
                    scope.launch { context.korokkeLifeDataStore.edit { it[CatVoiceEnabledKey] = enabled } }
                },
                soundEnabled = soundEnabled,
                onSoundEnabledChange = { enabled ->
                    scope.launch { context.korokkeLifeDataStore.edit { it[soundEnabledKey] = enabled } }
                },
                backupService = backupService,
                locationSettings = locationSettings,
                operationScope = scope,
                onShowTutorial = { panel = null; tutorialError = null; manualTutorial = true },
                onClose = { panel = null }
            )
            null -> Unit
        }
    }
}

@Composable
private fun Home(
    onTodo: () -> Unit,
    onShopping: () -> Unit,
    onSettings: () -> Unit,
    catActive: Boolean,
    catVoiceEnabled: Boolean,
    tutorial: Boolean,
    weatherLocation: String?
) {
    Box(Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(R.drawable.cozy_reading_room),
            contentDescription = null,
            modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.78f)
            .align(Alignment.BottomCenter)
            .offset(y = (-20).dp),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.size(52.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.settings_gears),
                        contentDescription = "設定",
                        modifier = Modifier.size(36.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Card(
                    modifier = Modifier.width(226.dp),
                    colors = CardDefaults.cardColors(containerColor = colorResource(R.color.home_card_background).copy(alpha = .92f)),
                    shape = RoundedCornerShape(22.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            weatherLocation?.let {
                                Text(it, fontSize = 11.sp, maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "☀",
                                    fontFamily = KorokkeFont,
                                    fontSize = 27.sp,
                                    color = Color(0xFFE49A25)
                                )
                                Spacer(Modifier.width(7.dp))
                                Text("晴れ", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.height(2.dp))
                            Text("24℃", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            Text("現在気温", fontSize = 12.sp, color = colorResource(R.color.ui_muted_content))
                        }
                        VerticalDivider(
                            Modifier.height(72.dp).padding(horizontal = 10.dp),
                            color = colorResource(R.color.ui_outline)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column {
                                Text("最高", fontSize = 12.sp, color = Color(0xFF9A5C43))
                                Text("27℃", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Column {
                                Text("最低", fontSize = 12.sp, color = Color(0xFF557080))
                                Text("18℃", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 142.dp, bottom = 86.dp)
                    .align(Alignment.Center)
            ) {
                CatLayer(active = catActive, forcedSitting = tutorial, catVoiceEnabled = catVoiceEnabled)
            }
            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val buttonColors = ButtonDefaults.buttonColors(
                    containerColor = colorResource(R.color.home_button_background).copy(alpha = .94f),
                    contentColor = colorResource(R.color.home_button_content)
                )
                Button(
                    onClick = onTodo,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = buttonColors,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                ) { Text("やること", fontSize = 18.sp) }
                Button(
                    onClick = onShopping,
                    modifier = Modifier.weight(1f).height(58.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = buttonColors,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 1.dp)
                ) { Text("買い物", fontSize = 18.sp) }
            }
        }
    }
}

@Composable
private fun CatLayer(active: Boolean, forcedSitting: Boolean, catVoiceEnabled: Boolean) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val catVoice = rememberCatVoiceController()

    var catMode by remember { mutableStateOf(CatMode.WALKING) }
    var pendingVoice by remember { mutableStateOf<CatVoice?>(null) }
    val actionTransitions = remember { CatActionTransitions() }
    val currentActive by rememberUpdatedState(active)

    // 寝そべり・毛繕いを挟んでも、直前の歩行方向を保持する
    var walkDirection by remember { mutableStateOf(1.0f) }

    val walking = active && catMode == CatMode.WALKING

    val cameraManipulator = rememberCameraManipulator {
        Manipulator.Builder()
            .orbitSpeed(0.02f, 0.0f)
            .build(Manipulator.Mode.ORBIT)
    }

    val modelNode = remember(modelLoader) {
        ModelNode(
            modelInstance = modelLoader.createModelInstance(
                CAT_MODEL
            ),
            scaleToUnits = 0.00375f
        ).apply {
            position = dev.romainguy.kotlin.math.Float3(0.0f, -0.35f, 0.0f)
            playAnimation(WALK_ANIMATION_INDEX, loop = true)
        }
    }

    val animationPlayback = remember(modelNode) {
        CatAnimationPlayback(
            stopAnimation = modelNode::stopAnimation,
            playAnimation = modelNode::playAnimation
        )
    }

    // Observational logging only: these values are not effect keys.
    val layerId = remember { java.util.UUID.randomUUID().toString().take(8) }
    val currentModel by rememberUpdatedState(modelNode)
    fun actionLog(message: String) {
        android.util.Log.d("CatAction", "layer=$layerId model=${System.identityHashCode(modelNode)} $message")
    }
    fun changeMode(next: CatMode, reason: String) {
        actionLog("MODE $catMode -> $next reason=$reason active=$currentActive")
        catMode = next
    }
    fun cancellationReason(expected: CatMode): String = when {
        !currentActive -> "screen_inactive"
        currentModel !== modelNode -> "model_replaced"
        catMode != expected -> "mode_changed_to_$catMode"
        else -> "effect_cancelled_or_layer_disposed"
    }
    DisposableEffect(Unit) {
        actionLog("LAYER START mode=$catMode active=$active forcedSitting=$forcedSitting animations=${modelNode.animationCount}")
        onDispose { actionLog("LAYER DISPOSE mode=$catMode active=$currentActive") }
    }
    LaunchedEffect(active) {
        actionLog("ACTIVE active=$active mode=$catMode forcedSitting=$forcedSitting")
    }

    // Each active walking period owns one timer. Meow/rest/inactivity cancels it.
    LaunchedEffect(modelNode, active, catMode) {
        if (!active || catMode != CatMode.WALKING) {
            actionLog("TIMER SKIP mode=$catMode active=$active")
            return@LaunchedEffect
        }
        val waitMillis = actionTransitions.nextWalkingDelayMillis()
        val timerId = java.util.UUID.randomUUID().toString().take(8)
        actionLog("TIMER START id=$timerId waitMillis=$waitMillis waitSeconds=${waitMillis / 1000.0} mode=$catMode active=$active")
        try {
            delay(waitMillis)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            actionLog("TIMER CANCEL id=$timerId reason=${cancellationReason(CatMode.WALKING)}")
            throw cancelled
        }
        actionLog("TIMER COMPLETE id=$timerId mode=$catMode active=$currentActive")
        val rest = actionTransitions.nextRest()
        actionLog("REST DRAW id=$timerId selected=$rest")
        changeMode(actionTransitions.startRest(catMode, currentActive, rest), "automatic_rest_timer_$timerId")
    }

    // Discard interrupted rests instead of continuing a stale timer on return.
    LaunchedEffect(modelNode, active) {
        if (!active) {
            modelNode.stopAnimation(LICK_CLEAN_ANIMATION_INDEX)
            modelNode.stopAnimation(IDLE_PURR_ANIMATION_INDEX)
            animationPlayback.resumeWalking()
            pendingVoice = null
            changeMode(actionTransitions.onInactive(catMode), "screen_inactive_reset")
        }
    }

    LaunchedEffect(modelNode, walking) {
        if (!walking) return@LaunchedEffect

        actionLog("WALKING START/RETURN mode=$catMode active=$active")
        var x = modelNode.position.x

        // 毛繕いで正面を向いた後、直前の進行方向へ向きを戻す
        modelNode.rotation =
            if (walkDirection > 0.0f) {
                dev.romainguy.kotlin.math.Float3(0.0f, 0.0f, 0.0f)
            } else {
                dev.romainguy.kotlin.math.Float3(0.0f, 180.0f, 0.0f)
            }
        while (true) {
            x += walkDirection * 0.008f

            if (x >= 0.25f) {
                x = 0.25f
                walkDirection = -1.0f
                modelNode.rotation =
                    dev.romainguy.kotlin.math.Float3(0.0f, 180.0f, 0.0f)
            } else if (x <= -0.25f) {
                x = -0.25f
                walkDirection = 1.0f
                modelNode.rotation =
                    dev.romainguy.kotlin.math.Float3(0.0f, 0.0f, 0.0f)
            }

            modelNode.position =
                dev.romainguy.kotlin.math.Float3(x, -0.35f, 0.0f)
            delay(50L)
        }
    }

    LaunchedEffect(active, catMode) {
        if (!active || catMode != CatMode.MEOWING) return@LaunchedEffect
        val selectedVoice = pendingVoice ?: return@LaunchedEffect

        actionLog("MEOWING START")

        val hasMeowAnimation = modelNode.animationCount > MEOW_ANIMATION_INDEX
        val reactionMillis = if (hasMeowAnimation) {
            animationPlayback.startMeow()
            (modelNode.animator.getAnimationDuration(MEOW_ANIMATION_INDEX) * 1_000L)
                .toLong()
                .coerceAtLeast(1L)
        } else {
            modelNode.stopAnimation(WALK_ANIMATION_INDEX)
            MEOW_FALLBACK_MILLIS
        }

        catVoice.play(selectedVoice)

        var meowCompleted = false
        try {
            delay(reactionMillis)
            meowCompleted = true
        } finally {
            // A keyed LaunchedEffect may be cancelled by lifecycle/state changes.
            // Walking restoration must still complete as one non-cancellable step.
            withContext(NonCancellable) {
                actionLog("MEOWING END reason=${if (meowCompleted) "completed" else cancellationReason(CatMode.MEOWING)}")
                animationPlayback.resumeWalking()
                pendingVoice = null
                changeMode(actionTransitions.finished(CatMode.MEOWING, currentActive), "meow_end_or_cancel")
                actionLog("WALKING RESTORE animation=0")
            }
        }
    }

    LaunchedEffect(modelNode, active, catMode) {
        if (!active || catMode != CatMode.GROOMING) return@LaunchedEffect

        modelNode.stopAnimation(WALK_ANIMATION_INDEX)
        modelNode.stopAnimation(MEOW_ANIMATION_INDEX)
        actionLog("GROOMING START")
        val groomingX = modelNode.position.x
        modelNode.position = dev.romainguy.kotlin.math.Float3(groomingX, -0.35f, 0.0f)
        modelNode.rotation = dev.romainguy.kotlin.math.Float3(0.0f, 0.0f, 0.0f)
        val groomingMillis = kotlin.math.ceil(
            modelNode.animator.getAnimationDuration(LICK_CLEAN_ANIMATION_INDEX) * 1_000.0
        ).toLong().coerceAtLeast(1L)

        // Drive grooming directly through Filament Animator so the pose is
        // applied to the skinned model every frame.
        val animator = modelNode.animator
        val duration = animator.getAnimationDuration(LICK_CLEAN_ANIMATION_INDEX)
        val startTime = System.nanoTime()

        try {
            while (true) {
                val elapsedSeconds =
                    (System.nanoTime() - startTime) / 1_000_000_000f
                if (elapsedSeconds >= duration) break

                animator.applyAnimation(
                    LICK_CLEAN_ANIMATION_INDEX,
                    elapsedSeconds % duration
                )
                animator.updateBoneMatrices()
                delay(16L)
            }
        } finally {
            modelNode.stopAnimation(LICK_CLEAN_ANIMATION_INDEX)
            actionLog("GROOMING END reason=${if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) "completed" else cancellationReason(CatMode.GROOMING)}")
        }
        // Cancellation skips this transition, including when the screen pauses.
        changeMode(actionTransitions.finished(CatMode.GROOMING, currentActive), "grooming_complete")
    }

    // IdlePurr remains at half speed for 16 seconds, also when entered directly.
    LaunchedEffect(modelNode, active, catMode) {
        if (!active || catMode != CatMode.IDLE) return@LaunchedEffect

        modelNode.stopAnimation(WALK_ANIMATION_INDEX)
        modelNode.stopAnimation(MEOW_ANIMATION_INDEX)
        modelNode.stopAnimation(LICK_CLEAN_ANIMATION_INDEX)
        actionLog("IDLE START durationMillis=16000")
        val animator = modelNode.animator
        val duration = animator.getAnimationDuration(IDLE_PURR_ANIMATION_INDEX)
        val speed = 0.5f
        val startTime = System.nanoTime()

        try {
            while (true) {
                val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000f
                if (elapsedSeconds >= 16.0f) break
                animator.applyAnimation(IDLE_PURR_ANIMATION_INDEX, (elapsedSeconds * speed) % duration)
                animator.updateBoneMatrices()
                delay(16L)
            }
        } finally {
            modelNode.stopAnimation(IDLE_PURR_ANIMATION_INDEX)
            actionLog("IDLE END reason=${if (kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.isActive == true) "completed" else cancellationReason(CatMode.IDLE)}")
        }
        animationPlayback.resumeWalking()
        changeMode(actionTransitions.finished(CatMode.IDLE, currentActive), "idle_complete")
    }

    Box(Modifier.fillMaxSize()) {
        Scene(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.62f)
                .align(Alignment.BottomCenter),
            engine = engine,
            modelLoader = modelLoader,
            isOpaque = false,
            cameraManipulator = cameraManipulator,
            onTouchEvent = { event, hitResult ->
                if (event.action == android.view.MotionEvent.ACTION_UP) {
                    android.util.Log.d(
                        "KorokkeTap",
                        "SCENE TAP node=${hitResult?.node}"
                    )
                    android.util.Log.d(
                        "KorokkeTap",
                        "STATE active=$active mode=$catMode voice=$catVoiceEnabled"
                    )
                    if (hitResult?.node != null && active && catMode == CatMode.WALKING) {
                        android.util.Log.d("KorokkeTap", "VOICE PLAY")
                        val selectedVoice = catVoice.choose(catVoiceEnabled)
                        if (selectedVoice != null) {
                            pendingVoice = selectedVoice
                            changeMode(CatMode.MEOWING, "cat_tap")
                        }
                    }
                }
                false
            },
            childNodes = remember(modelNode) { listOf(modelNode) }
        )
    }
}

@Composable
internal fun PanelShell(title: String, onClose: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BoxWithConstraints(Modifier.fillMaxWidth(.92f).aspectRatio(16f / 9f)) {
            val panelBackground = colorResource(R.color.home_card_background)
            Image(
                painter = painterResource(R.drawable.dialog_frame),
                contentDescription = null,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    drawContent()
                    // Recolor only the interior of the 1920 x 1080 frame asset.
                    // Keep its decorative lines and their original geometry intact.
                    drawRect(
                        color = panelBackground,
                        topLeft = Offset(size.width * 110f / 1920f, size.height * 110f / 1080f),
                        size = Size(size.width * 1700f / 1920f, size.height * 860f / 1080f),
                    )
                },
                contentScale = ContentScale.Fit
            )
            // The frame lines sit at about 5.3% horizontally and 9.4% vertically.
            val horizontalInset = maxWidth * 0.06f + 16.dp
            val verticalInset = maxHeight * 0.10f + 8.dp
            Column(Modifier.fillMaxSize().padding(horizontal = horizontalInset, vertical = verticalInset)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    TextButton(onClick = onClose) { Text("×", fontSize = 28.sp) }
                }
                content()
            }
        }
    }
}

@Composable
private fun TodoPanel(vm: MainViewModel, playDecisionSound: () -> Unit, onClose: () -> Unit) {
    val items by vm.todos.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<TodoItem?>(null) }
    var adding by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<TodoItem?>(null) }
    var confirmDelete by remember { mutableStateOf<TodoItem?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    PanelShell("やること", onClose) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) Text("なし🐾", modifier = Modifier.align(Alignment.Center), fontSize = 22.sp)
            Column(Modifier.verticalScroll(rememberScrollState())) { items.forEach { item ->
                ListItem(
                    headlineContent = { Text(item.title, color = if (item.isOverdue()) Color(0xFF2769A8) else Color.Unspecified, textDecoration = if (item.completed) TextDecoration.LineThrough else null) },
                    supportingContent = if (!item.completed && item.deadlineLabel() != null) ({ Text(item.deadlineLabel()!!) }) else null,
                    leadingContent = { Checkbox(item.completed, { vm.toggle(item) }) },
                    modifier = Modifier.combinedClickable(onClick = { vm.toggle(item) }, onLongClick = { action = item })
                )
            } }
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).size(56.dp),
                shape = CircleShape,
                containerColor = colorResource(R.color.ui_selection_background),
                contentColor = colorResource(R.color.home_button_background),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
            ) { Text("＋", fontSize = 26.sp) }
        }
        if (items.any { it.completed }) TextButton(onClick = { confirmClear = true }) { Text("完了済みを削除") }
    }
    if (adding || editing != null) TodoEditor(editing, { adding = false; editing = null }, { title, date, time, reminderEnabled, reminderTime ->
        val old = editing
        playDecisionSound()
        vm.saveTodo(title, old, date, time, reminderEnabled, reminderTime)
        adding = false; editing = null
    })
    action?.let { item -> ActionDialog({ action = null }, { editing = item; action = null }, { confirmDelete = item; action = null }) }
    confirmDelete?.let { item -> ConfirmDialog("このTODOを削除しますか？", { confirmDelete = null }, { vm.delete(item); confirmDelete = null }) }
    if (confirmClear) ConfirmDialog("完了済みをすべて削除しますか？", { confirmClear = false }, { vm.deleteCompleted(); confirmClear = false })
}

@Composable
private fun ShoppingPanel(vm: MainViewModel, playDecisionSound: () -> Unit, onClose: () -> Unit) {
    val items by vm.shopping.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }; var adding by remember { mutableStateOf(false) }
    var action by remember { mutableStateOf<ShoppingItem?>(null) }; var confirmDelete by remember { mutableStateOf<ShoppingItem?>(null) }; var clear by remember { mutableStateOf(false) }
    PanelShell("買い物", onClose) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) Text("なし🐾", modifier = Modifier.align(Alignment.Center), fontSize = 22.sp)
            Column(Modifier.verticalScroll(rememberScrollState())) { items.forEach { item -> ListItem(
                headlineContent = { Text(item.title, textDecoration = if (item.purchased) TextDecoration.LineThrough else null) },
                leadingContent = { Checkbox(item.purchased, { vm.toggle(item) }) },
                modifier = Modifier.combinedClickable(onClick = { vm.toggle(item) }, onLongClick = { action = item })
            ) } }
            FloatingActionButton(
                onClick = { adding = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp).size(56.dp),
                shape = CircleShape,
                containerColor = colorResource(R.color.ui_selection_background),
                contentColor = colorResource(R.color.home_button_background),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp, pressedElevation = 6.dp)
            ) { Text("＋", fontSize = 26.sp) }
        }
        if (items.any { it.purchased }) TextButton(onClick = { clear = true }) { Text("購入済みを削除") }
    }
    if (adding || editing != null) TextEditor("商品名", editing?.title.orEmpty(), { adding = false; editing = null }) {
        playDecisionSound()
        vm.saveShopping(it, editing)
        adding = false
        editing = null
    }
    action?.let { item -> ActionDialog({ action = null }, { editing = item; action = null }, { confirmDelete = item; action = null }) }
    confirmDelete?.let { item -> ConfirmDialog("この商品を削除しますか？", { confirmDelete = null }, { vm.delete(item); confirmDelete = null }) }
    if (clear) ConfirmDialog("購入済みをすべて削除しますか？", { clear = false }, { vm.deletePurchased(); clear = false })
}

@Composable
private fun TextEditor(label: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember(initial) { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (initial.isBlank()) "追加" else "編集") }, text = {
        OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true)
    }, confirmButton = { TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) { Text("保存") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

@Composable
private fun TodoEditor(
    item: TodoItem?,
    onDismiss: () -> Unit,
    onSave: (String, String?, String?, Boolean, String?) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(item) { mutableStateOf(item?.title.orEmpty()) }
    var dueDraft by remember(item) {
        mutableStateOf(TodoDueDraft(item?.deadlineDate, item?.deadlineTime))
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showReminderTimePicker by remember { mutableStateOf(false) }
    var selectingDateForReminder by remember { mutableStateOf(false) }
    var reminderEnabled by remember(item) { mutableStateOf(item?.reminderEnabled ?: false) }
    var reminderTime by remember(item) { mutableStateOf(item?.reminderTime) }
    var showExactAlarmPermissionGuide by remember { mutableStateOf(false) }
    fun continueReminderEnable() {
        reminderEnabled = true
        if (dueDraft.date == null) {
            selectingDateForReminder = true
            showDatePicker = true
        } else if (dueDraft.time == null) {
            showReminderTimePicker = true
        } else {
            reminderTime = null
        }
    }
    fun continueAfterExactAlarmCheck() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !canScheduleExactTodoAlarms(context)) {
            showExactAlarmPermissionGuide = true
        } else {
            continueReminderEnable()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) continueAfterExactAlarmCheck()
    }
    fun requestReminderEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            continueAfterExactAlarmCheck()
        }
    }
    val parsedDate = remember(dueDraft.date) {
        dueDraft.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }
    val parsedTime = remember(dueDraft.time) {
        dueDraft.time?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
    }
    val dueSummary = formatTodoDueSummary(dueDraft.date, dueDraft.time)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (item == null) "TODO追加" else "TODO編集") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(title, { title = it }, label = { Text("TODO名") }, singleLine = true)
            OutlinedButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (dueSummary == null) "期日を選択" else "期日")
                    if (dueSummary != null) {
                        Spacer(Modifier.height(2.dp))
                        Text(dueSummary, fontWeight = FontWeight.Bold)
                    }
                }
            }
            OutlinedButton(
                onClick = {
                    if (reminderEnabled) {
                        reminderEnabled = false
                        reminderTime = null
                    } else {
                        requestReminderEnable()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = if (reminderEnabled) colorResource(R.color.ui_selection_background) else Color.Transparent,
                    contentColor = colorResource(R.color.home_button_background),
                ),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Text(if (reminderEnabled) "🔔 リマインダー ON" else "🔔 リマインダー OFF")
                    if (reminderEnabled) {
                        val notificationTime = dueDraft.time ?: reminderTime
                        Text(
                            notificationTime?.let { "通知時刻 $it" } ?: "通知時刻を選択",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (reminderEnabled && dueDraft.time == null && reminderTime != null) {
                TextButton(
                    onClick = { showReminderTimePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("通知時刻を変更") }
            }
        }
    }, confirmButton = {
        TextButton(
            onClick = {
                onSave(
                    title,
                    dueDraft.date,
                    dueDraft.date?.let { dueDraft.time },
                    reminderEnabled,
                    reminderTime.takeIf { reminderEnabled && dueDraft.time == null },
                )
            },
            enabled = title.isNotBlank() && (!reminderEnabled || dueDraft.date != null && (dueDraft.time != null || reminderTime != null)),
        ) { Text("保存") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })

    if (showDatePicker) {
        TodoDatePickerDialog(
            selectedDate = dueDraft.pendingDate ?: parsedDate,
            onDismiss = {
                dueDraft = dueDraft.cancelDateSelection()
                showDatePicker = false
                if (selectingDateForReminder) {
                    selectingDateForReminder = false
                    reminderEnabled = false
                }
            },
            onDateSelected = { selected ->
                showDatePicker = false
                if (selectingDateForReminder) {
                    dueDraft = TodoDueDraft(selected.toString(), null)
                    selectingDateForReminder = false
                    showReminderTimePicker = true
                } else {
                    dueDraft = dueDraft.beginDateSelection(selected)
                    showTimePicker = true
                }
            },
            onClear = {
                dueDraft = TodoDueDraft(null, null)
                showDatePicker = false
                selectingDateForReminder = false
                reminderEnabled = false
                reminderTime = null
            },
        )
    }
    if (showTimePicker) {
        TodoTimePickerDialog(
            initialTime = parsedTime ?: LocalTime.now(),
            onDismiss = {
                showTimePicker = false
                showDatePicker = true
            },
            onConfirm = { selectedTime ->
                dueDraft = dueDraft.commitDateSelection(selectedTime)
                reminderTime = null
                showTimePicker = false
            },
            onNoTime = {
                dueDraft = dueDraft.commitDateSelection(null)
                showTimePicker = false
                if (reminderEnabled) showReminderTimePicker = true
            },
        )
    }
    if (showReminderTimePicker) {
        TodoTimePickerDialog(
            initialTime = reminderTime?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: LocalTime.now(),
            onDismiss = {
                showReminderTimePicker = false
                if (reminderTime == null) reminderEnabled = false
            },
            onConfirm = { selectedTime ->
                reminderTime = selectedTime
                reminderEnabled = true
                showReminderTimePicker = false
            },
            onNoTime = {},
            title = "通知時刻を選択",
            allowNoTime = false,
        )
    }
    if (showExactAlarmPermissionGuide) {
        AlertDialog(
            onDismissRequest = {
                showExactAlarmPermissionGuide = false
                continueReminderEnable()
            },
            title = { Text("正確な時刻の通知") },
            text = {
                Text("指定した時間に正確に通知するには、アラームとリマインダーの許可が必要です。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExactAlarmPermissionGuide = false
                        val intent = Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.parse("package:${context.packageName}"),
                        )
                        if (intent.resolveActivity(context.packageManager) != null) {
                            context.startActivity(intent)
                        }
                        continueReminderEnable()
                    },
                ) { Text("許可設定へ") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExactAlarmPermissionGuide = false
                        continueReminderEnable()
                    },
                ) { Text("許可せず続ける") }
            },
        )
    }
}

@Composable
private fun ActionDialog(onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("操作") }, text = {
        Column { TextButton(onClick = onEdit) { Text("編集") }; TextButton(onClick = onDelete) { Text("削除") } }
    }, confirmButton = {})
}

@Composable
private fun ConfirmDialog(message: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("確認") }, text = { Text(message) }, confirmButton = { TextButton(onClick = onConfirm) { Text("削除") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("キャンセル") } })
}

