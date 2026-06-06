@file:OptIn(ExperimentalMaterial3Api::class)
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.database.AppDatabase
import com.example.database.Goal
import com.example.database.ProgressLog
import com.example.repository.GoalRepository
import com.example.ui.GoalProgressItem
import com.example.ui.GoalScreen
import com.example.ui.GoalUiState
import com.example.ui.GoalViewModel
import com.example.ui.GoalViewModelFactory
import com.example.ui.components.MinimalistChart
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanSecondary
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.OrangeFlame
import com.example.ui.theme.TechMutedText
import com.example.ui.theme.CrispWhite
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Room database & Repository setup
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = GoalRepository(database.goalDao)
        val factory = GoalViewModelFactory(repository)
        val viewModel = androidx.lifecycle.ViewModelProvider(this, factory)[GoalViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        GoalTrackerApp(viewModel = viewModel)
                    }
                }
            }
        }
    }
}

@Composable
fun GoalTrackerApp(viewModel: GoalViewModel) {
    val screenState by viewModel.currentScreen.collectAsStateWithLifecycle()
    val allGoalsProgress by viewModel.allGoalsProgress.collectAsStateWithLifecycle()
    val activeDashboard by viewModel.activeDashboardState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = screenState,
        transitionSpec = {
            fadeIn() togetherWith fadeOut()
        },
        label = "ScreenTransition"
    ) { currentScreen ->
        when (currentScreen) {
            is GoalScreen.MainList -> {
                MainListScreen(
                    goals = allGoalsProgress,
                    onSelectGoal = { goalId -> viewModel.selectGoal(goalId) },
                    onCreateGoalClick = { viewModel.navigateToCreateGoal() }
                )
            }
            is GoalScreen.CreateGoal -> {
                CreateGoalScreen(
                    onCreate = { title, amount, targetDate ->
                        viewModel.startGoal(title, amount, targetDate)
                    },
                    onBack = { viewModel.navigateToMainList() }
                )
            }
            is GoalScreen.Dashboard -> {
                if (activeDashboard is GoalUiState.Dashboard) {
                    val dashboard = activeDashboard as GoalUiState.Dashboard
                    DashboardScreen(
                        dashboard = dashboard,
                        onLogProgress = { value, date ->
                            viewModel.logProgress(dashboard.goal.id, value, date)
                        },
                        onDeleteLog = { date ->
                            viewModel.deleteLog(dashboard.goal.id, date)
                        },
                        onDeleteGoal = {
                            viewModel.deleteGoal(dashboard.goal.id)
                        },
                        onBack = { viewModel.navigateToMainList() }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
            }
        }
    }
}

// 1. MAIN SCREEN: SCROLLING CARD LIST OF ALL GOALS WITH FAB
@Composable
fun MainListScreen(
    goals: List<GoalProgressItem>,
    onSelectGoal: (Long) -> Unit,
    onCreateGoalClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(bottom = 4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 24.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ElectricCyan)
                    )
                    Text(
                        text = "МОИ ЦЕЛИ",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 4.sp,
                            fontSize = 24.sp
                        ),
                        color = ElectricCyan
                    )
                }
                Text(
                    text = "Нажмите на проект для просмотра графика и внесения прогресса",
                    style = MaterialTheme.typography.bodySmall,
                    color = TechMutedText,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )
            }

            if (goals.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 32.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, Color(0xFF22252F))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = TechMutedText,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "Список целей пуст",
                                style = MaterialTheme.typography.titleMedium,
                                color = CrispWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Нажмите кнопку '+' внизу экрана, чтобы создать первую цель.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TechMutedText,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(
                    items = goals,
                    key = { it.goal.id }
                ) { item ->
                    GoalListRow(
                        item = item,
                        onClick = { onSelectGoal(item.goal.id) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(84.dp))
            }
        }

        // Circular "+" floating action button at the bottom right corner
        LargeFloatingActionButton(
            onClick = onCreateGoalClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            shape = CircleShape,
            containerColor = ElectricCyan,
            contentColor = Color(0xFF003B33)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Добавить цель",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

// SINGLE CELL IN GOALS LIST SCROLL
@Composable
fun GoalListRow(
    item: GoalProgressItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF22252F))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tasteful vertically-stretching cyberpunk neon indicator on the left edge
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(ElectricCyan, ElectricCyanSecondary)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.goal.title.uppercase(),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        ),
                        color = CrispWhite,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = String.format(Locale.US, "%.1f%%", item.progressPercentage * 100),
                        style = MaterialTheme.typography.titleMedium,
                        color = ElectricCyan,
                        fontWeight = FontWeight.ExtraBold
                    )
                }

                // Visual bar tracking progress
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1E2126))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction = item.progressPercentage.coerceIn(0f, 1f))
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(ElectricCyanSecondary, ElectricCyan)
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Накоплено: ${String.format(Locale.US, "%,.0f", item.currentProgress)} / ${String.format(Locale.US, "%,.0f", item.goal.targetAmount)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TechMutedText
                    )
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = ElectricCyan.copy(alpha = 0.8f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

// 2. CREATE NEW GOAL SCREEN
@Composable
fun CreateGoalScreen(
    onCreate: (String, Double, Long?) -> Unit,
    onBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var targetAmountStr by remember { mutableStateOf("") }
    var durationPreset by remember { mutableIntStateOf(0) } // 0: Без срока, 1: 30 дней, 2: 90 дней, 3: 180 дней, 4: Своя дата
    var customTargetDate by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val russianLocale = Locale("ru")
    val dateFormatText = remember { SimpleDateFormat("dd MMMM yyyy", russianLocale) }

    val isFormValid = title.isNotBlank() && (targetAmountStr.toDoubleOrNull() ?: 0.0) > 0.0

    val computedTargetDate = remember(durationPreset, customTargetDate) {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        when (durationPreset) {
            0 -> null
            1 -> {
                calendar.add(Calendar.DAY_OF_YEAR, 30)
                calendar.timeInMillis
            }
            2 -> {
                calendar.add(Calendar.DAY_OF_YEAR, 90)
                calendar.timeInMillis
            }
            3 -> {
                calendar.add(Calendar.DAY_OF_YEAR, 180)
                calendar.timeInMillis
            }
            4 -> customTargetDate
            else -> null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Назад",
                    tint = CrispWhite
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "НОВАЯ ЦЕЛЬ",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                ),
                color = ElectricCyan
            )
        }

        Text(
            text = "Задайте параметры проекта и отслеживайте накопление в трекере",
            style = MaterialTheme.typography.bodyMedium,
            color = TechMutedText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Form Fields Container
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, Color(0xFF22252F))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Title Field
                Text(
                    text = "ОПИСАНИЕ ЦЕЛИ",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = ElectricCyan
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Например: Новый ноутбук", color = TechMutedText.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF22252F),
                        focusedLabelColor = ElectricCyan,
                        cursorColor = ElectricCyan
                    ),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Target Amount Field
                Text(
                    text = "ЦЕЛЕВАЯ СУММА (ЦИФРАМИ)",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = ElectricCyan
                )

                OutlinedTextField(
                    value = targetAmountStr,
                    onValueChange = { targetAmountStr = it },
                    placeholder = { Text("150000", color = TechMutedText.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = Color(0xFF22252F),
                        focusedLabelColor = ElectricCyan,
                        cursorColor = ElectricCyan
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Deadline Settings
                Text(
                    text = "ФИНАЛЬНЫЙ СРОК",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                    color = ElectricCyan
                )

                // Presets Layout
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetButton(
                            text = "Без срока",
                            isSelected = durationPreset == 0,
                            onClick = { durationPreset = 0 },
                            modifier = Modifier.weight(1f)
                        )
                        PresetButton(
                            text = "30 дней",
                            isSelected = durationPreset == 1,
                            onClick = { durationPreset = 1 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PresetButton(
                            text = "90 дней",
                            isSelected = durationPreset == 2,
                            onClick = { durationPreset = 2 },
                            modifier = Modifier.weight(1f)
                        )
                        PresetButton(
                            text = "180 дней",
                            isSelected = durationPreset == 3,
                            onClick = { durationPreset = 3 },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    PresetButton(
                        text = if (customTargetDate != null) "Срок: ${dateFormatText.format(Date(customTargetDate!!))}" else "Выбрать свою дату",
                        isSelected = durationPreset == 4,
                        onClick = {
                            durationPreset = 4
                            showDatePicker = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                computedTargetDate?.let { date ->
                    Text(
                        text = "Будет достигнуто до: ${dateFormatText.format(Date(date))}",
                        color = ElectricCyanSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                } ?: Text(
                    text = "Срок достижения: без ограничений по времени",
                    color = TechMutedText,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Action launch button
        Button(
            onClick = {
                val sum = targetAmountStr.toDoubleOrNull() ?: 0.0
                if (sum > 0.0) {
                    onCreate(title, sum, computedTargetDate)
                }
            },
            enabled = isFormValid,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = Color(0xFF003B33),
                disabledContainerColor = Color(0xFF1E2126),
                disabledContentColor = TechMutedText.copy(alpha = 0.5f)
            )
        ) {
            Text(
                "УСТАНОВИТЬ ЦЕЛЬ",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                fontSize = 15.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Material 3 Custom Date Picker Dialogue
    if (showDatePicker) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    customTargetDate = dateState.selectedDateMillis?.let { utcMillis ->
                        val localOffset = TimeZone.getDefault().getOffset(utcMillis)
                        utcMillis - localOffset
                    }
                    showDatePicker = false
                }) {
                    Text("OK", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Отмена", color = TechMutedText)
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(
                state = dateState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = ElectricCyan,
                    headlineContentColor = ElectricCyan,
                    todayContentColor = ElectricCyan,
                    selectedDayContainerColor = ElectricCyan,
                    selectedDayContentColor = Color.Black
                )
            )
        }
    }
}

// 3. DASHBOARD DETAIL VIEW SCREEN
@Composable
fun DashboardScreen(
    dashboard: GoalUiState.Dashboard,
    onLogProgress: (Double, Long) -> Unit,
    onDeleteLog: (Long) -> Unit,
    onDeleteGoal: () -> Unit,
    onBack: () -> Unit
) {
    var loggedValueStr by remember { mutableStateOf("") }
    var showArchivedAlert by remember { mutableStateOf(false) }
    var showDatePickerForLog by remember { mutableStateOf(false) }

    val russianLocale = Locale("ru")
    val dateFormatText = remember { SimpleDateFormat("dd MMMM yyyy", russianLocale) }

    val progressValuePercent = (dashboard.progressPercentage * 100).coerceAtMost(200f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App top operations with Back Navigation
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Мои цели",
                            tint = CrispWhite
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(width = 6.dp, height = 18.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(ElectricCyan)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "ДЕТАЛИ ЦЕЛИ",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        ),
                        color = ElectricCyan
                    )
                }

                IconButton(
                    onClick = { showArchivedAlert = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Сбросить цель",
                        tint = OrangeFlame
                    )
                }
            }
        }

        // 1. Goal description Hero Card (Without ruble symbols)
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF22252F))
            ) {
                // Sleek, futuristic horizontal edge gradient glowing line on top
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(ElectricCyan, ElectricCyanSecondary)
                            )
                        )
                )

                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "АКТИВНЫЙ ПРОЕКТ",
                        color = TechMutedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = dashboard.goal.title.uppercase(),
                        color = CrispWhite,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Progress track
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f%%", progressValuePercent),
                            color = ElectricCyan,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "${String.format(Locale.US, "%,.0f", dashboard.currentProgress)} / ${String.format(Locale.US, "%,.0f", dashboard.goal.targetAmount)}",
                            color = CrispWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Thick solid neon progress bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E2126))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (dashboard.progressPercentage).coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(ElectricCyanSecondary, ElectricCyan)
                                    )
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Technical stats block (Without currency symbols)
                    HorizontalDivider(color = Color(0xFF22252F))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("НАЧАЛО", color = TechMutedText, fontSize = 9.sp)
                            Text(
                                dateFormatText.format(Date(dashboard.goal.startDateMillis)),
                                color = CrispWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("ФИНАЛ", color = TechMutedText, fontSize = 9.sp)
                            Text(
                                dashboard.goal.targetDateMillis?.let { dateFormatText.format(Date(it)) } ?: "Без срока",
                                color = CrispWhite,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("ЕЖЕДНЕВНЫЙ ТЕМП", color = TechMutedText, fontSize = 9.sp)
                            val rateFormatted = if (dashboard.dailyRate > 0) String.format(Locale.US, "%,.1f", dashboard.dailyRate) else "0"
                            Text(
                                "$rateFormatted в день",
                                color = ElectricCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("ПРОГНОЗ ДОСТИЖЕНИЯ", color = TechMutedText, fontSize = 9.sp)
                            val projectedText = when {
                                dashboard.currentProgress >= dashboard.goal.targetAmount -> "Цель достигнута! 🎉"
                                dashboard.projectedCompletionDate == null -> "Недостаточный темп"
                                else -> {
                                    val formatted = dateFormatText.format(Date(dashboard.projectedCompletionDate))
                                    val isDelayed = dashboard.goal.targetDateMillis?.let { dashboard.projectedCompletionDate > it } ?: false
                                    if (isDelayed) "$formatted (с опозданием) ⚠️" else formatted
                                }
                            }
                            Text(
                                projectedText,
                                color = if (dashboard.currentProgress >= dashboard.goal.targetAmount) ElectricCyan else if (dashboard.projectedCompletionDate == null || (dashboard.goal.targetDateMillis?.let { dashboard.projectedCompletionDate > it } ?: false)) OrangeFlame else ElectricCyan,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    dashboard.daysRemaining?.let { days ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (days > 0) "Осталось: $days дней до дедлайна" else "Срок достижения истек",
                            color = if (days > 0) ElectricCyanSecondary else OrangeFlame,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 2. Interactive Charts Component Segment
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF22252F))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "ГРАФИК НАКОПЛЕНИЯ",
                        color = TechMutedText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Custom Canvas chart (No currency symbols inside)
                    MinimalistChart(
                        goal = dashboard.goal,
                        logs = dashboard.logs,
                        chartType = 1,
                        dailyRate = dashboard.dailyRate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(380.dp)
                    )
                }
            }
        }

        // 3. Dual-mode daily accumulation progress logger
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, Color(0xFF22252F))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "ВНЕСЕНИЕ ПРОГРЕССА СУММЫ",
                        color = TechMutedText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )

                    // Unified sum input
                    OutlinedTextField(
                        value = loggedValueStr,
                        onValueChange = { loggedValueStr = it },
                        placeholder = { Text("Накопленная сумма (всего на дату)", fontSize = 13.sp, color = TechMutedText.copy(alpha = 0.5f)) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ElectricCyan,
                            unfocusedBorderColor = Color(0xFF22252F),
                            unfocusedLabelColor = TechMutedText
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // Two entry mechanisms: Log for today or Log for other date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1. ADD TODAY PROGRESS (Fast save)
                        Button(
                            onClick = {
                                val value = loggedValueStr.toDoubleOrNull()
                                if (value != null && value >= 0) {
                                    // Today's date passed directly
                                    onLogProgress(value, System.currentTimeMillis())
                                    loggedValueStr = ""
                                }
                            },
                            enabled = loggedValueStr.toDoubleOrNull() != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = Color(0xFF003B33),
                                disabledContainerColor = Color(0xFF1E2126),
                                disabledContentColor = TechMutedText.copy(alpha = 0.5f)
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "На сегодня",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1
                            )
                        }

                        // 2. CHOOSE DIFFERENT DATE
                        OutlinedButton(
                            onClick = { showDatePickerForLog = true },
                            enabled = loggedValueStr.toDoubleOrNull() != null,
                            border = BorderStroke(1.dp, if (loggedValueStr.toDoubleOrNull() != null) ElectricCyan else Color(0xFF22252F)),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = ElectricCyan,
                                disabledContentColor = TechMutedText.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Иная дата...",
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // 4. Log History panel
        item {
            Text(
                text = "ИСТОРИЯ НАКОПЛЕНИЙ",
                color = TechMutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            )
        }

        if (dashboard.logs.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, Color(0xFF22252F))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Нет записанных данных.\nВнесите первую сумму выше на сегодня или на любую дату!",
                            color = TechMutedText,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = dashboard.logs.sortedByDescending { it.dateMillis },
                key = { it.id }
            ) { log ->
                HistoryRow(
                    log = log,
                    dateFormatText = dateFormatText,
                    onDelete = { onDeleteLog(log.dateMillis) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(48.dp))
        }
    }

    // Goal Delete Dialog
    if (showArchivedAlert) {
        AlertDialog(
            onDismissRequest = { showArchivedAlert = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Удаление цели", color = OrangeFlame, fontWeight = FontWeight.Bold) },
            text = { Text("Вы действительно хотите удалить текущую цель? Весь прогресс и история будут стёрты навсегда.", color = CrispWhite) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteGoal()
                        showArchivedAlert = false
                    }
                ) {
                    Text("Удалить", color = OrangeFlame, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchivedAlert = false }) {
                    Text("Отмена", color = TechMutedText)
                }
            }
        )
    }

    // Material 3 custom pick date dialogue
    if (showDatePickerForLog) {
        val dateState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePickerForLog = false },
            confirmButton = {
                TextButton(onClick = {
                    val dateMillis = dateState.selectedDateMillis?.let { utcMillis ->
                        val localOffset = TimeZone.getDefault().getOffset(utcMillis)
                        utcMillis - localOffset
                    } ?: System.currentTimeMillis()
                    val value = loggedValueStr.toDoubleOrNull()
                    if (value != null) {
                        onLogProgress(value, dateMillis)
                        loggedValueStr = ""
                    }
                    showDatePickerForLog = false
                }) {
                    Text("Выбрать", color = ElectricCyan)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePickerForLog = false }) {
                    Text("Отмена", color = TechMutedText)
                }
            }
        ) {
            DatePicker(
                state = dateState,
                colors = DatePickerDefaults.colors(
                    titleContentColor = ElectricCyan,
                    headlineContentColor = ElectricCyan,
                    todayContentColor = ElectricCyan,
                    selectedDayContainerColor = ElectricCyan,
                    selectedDayContentColor = Color.Black
                )
            )
        }
    }
}

@Composable
fun SegmentTab(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) Color(0xFF22252F) else Color.Transparent)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            color = if (isSelected) ElectricCyan else TechMutedText,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
fun HistoryRow(
    log: ProgressLog,
    dateFormatText: SimpleDateFormat,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFF1E2126))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = dateFormatText.format(Date(log.dateMillis)),
                    color = CrispWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Накоплено на дату",
                    color = TechMutedText,
                    fontSize = 10.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = String.format(Locale.US, "%,.0f", log.accumulatedValue),
                    color = ElectricCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Удалить запись",
                        tint = OrangeFlame.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PresetButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (isSelected) ElectricCyan else Color(0xFF22252F)
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (isSelected) Color(0xFF0C3835) else Color.Transparent,
            contentColor = if (isSelected) ElectricCyan else CrispWhite
        ),
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

