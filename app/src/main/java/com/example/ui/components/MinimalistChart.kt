package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.database.Goal
import com.example.database.ProgressLog
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanSecondary
import com.example.ui.theme.MonolithSurfaceVariant
import com.example.ui.theme.TechMutedText
import com.example.ui.theme.CrispWhite
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MinimalistChart(
    goal: Goal,
    logs: List<ProgressLog>,
    chartType: Int, // 0: Only logged data, 1: Forecast, 2: Perspective / Full View
    dailyRate: Double,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current

    // timeframe state: 0: 1 week, 1: 2 weeks, 2: 1 month, 3: 3 months, 4: 6 months, 5: All (Весь срок)
    var selectedTimeframe by remember { mutableStateOf(5) }

    // Calendar & Calculations
    val calendar = Calendar.getInstance(TimeZone.getDefault())
    fun normalizeToMidnight(time: Long): Long {
        calendar.timeInMillis = time
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    val startMidnight = remember(goal.startDateMillis, logs) {
        val earliestLogDate = logs.minOfOrNull { it.dateMillis } ?: goal.startDateMillis
        normalizeToMidnight(kotlin.math.min(goal.startDateMillis, earliestLogDate))
    }
    val todayMidnight = normalizeToMidnight(System.currentTimeMillis())

    // Compute dynamic timeline range limits based on both chartType and selectedTimeframe
    val chartTimelineRange = remember(startMidnight, todayMidnight, goal.targetDateMillis, selectedTimeframe, chartType, logs) {
        val step = 24 * 60 * 60 * 1000L
        
        // Find reference data end point for "Only logged data" (chartType == 0)
        val dataMax = logs.maxOfOrNull { it.dateMillis } ?: todayMidnight
        val activeEnd = kotlin.math.max(dataMax, todayMidnight)
        
        // Base end point if no limits are applied
        val absoluteEnd = goal.targetDateMillis?.let { normalizeToMidnight(it) } 
            ?: (activeEnd + 30 * step)  // default 30 days ahead if no goal date is defined
            
        val numDays = when (selectedTimeframe) {
            0 -> 7
            1 -> 14
            2 -> 30
            3 -> 90
            4 -> 180
            else -> -1 // all
        }
        
        val calculatedStart: Long
        val calculatedEnd: Long
        
        if (numDays > 0) {
            val timeframeMillis = numDays * step
            
            if (chartType == 0) {
                // "Only logged data": show history preceding the latest active point
                calculatedEnd = activeEnd
                calculatedStart = kotlin.math.max(startMidnight, activeEnd - timeframeMillis)
            } else {
                // "Forecast" or "Full scale": center nicely around todayMidnight to see history + forecast
                val historyRatio = 0.70
                val historyDays = (numDays * historyRatio).toLong()
                
                val preferredStart = todayMidnight - historyDays * step
                calculatedStart = kotlin.math.max(startMidnight, preferredStart)
                calculatedEnd = calculatedStart + timeframeMillis
            }
        } else {
            // Full span (до дедлайна / вся шкала)
            calculatedStart = startMidnight
            calculatedEnd = if (chartType == 0) activeEnd else absoluteEnd
        }
        
        Pair(calculatedStart, calculatedEnd)
    }
    
    val currentStart = chartTimelineRange.first
    val currentEnd = chartTimelineRange.second

    // Days lists
    val daysList = remember(currentStart, currentEnd) {
        val days = mutableListOf<Long>()
        var curr = currentStart
        val step = 24 * 60 * 60 * 1000L
        val limit = 365 * 2 // Bounded to 2 years

        while (curr <= currentEnd && days.size < limit) {
            days.add(curr)
            curr += step
        }
        if (days.isEmpty()) {
            days.add(currentStart)
        }
        days
    }

    // Map of explicitly keyed values
    val logMap = remember(logs) {
        logs.associateBy { it.dateMillis }
    }

    // Computed series representing (date, value, isForecast)
    val series = remember(daysList, logMap, chartType, dailyRate, goal.targetAmount) {
        var lastValue = 0.0
        daysList.map { msec ->
            val log = logMap[msec]
            val isFuture = msec > todayMidnight

            val value = when {
                log != null -> {
                    lastValue = log.accumulatedValue
                    lastValue
                }
                isFuture -> {
                    if (chartType == 1) {
                        // Forecast
                        val daysAhead = ((msec - todayMidnight) / (24 * 60 * 60 * 1000L)).toDouble()
                        val currentAcc = logMap[todayMidnight]?.accumulatedValue
                            ?: logs.filter { it.dateMillis <= todayMidnight }
                                .maxByOrNull { it.dateMillis }?.accumulatedValue ?: 0.0
                        (currentAcc + (dailyRate * daysAhead)).coerceAtLeast(0.0)
                    } else if (chartType == 2) {
                        // Empty/Flat for full view
                        Double.NaN
                    } else {
                        lastValue
                    }
                }
                else -> {
                    // Past day with no logs - carry forward last recorded value
                    val precedingLog = logs.filter { it.dateMillis <= msec }
                        .maxByOrNull { it.dateMillis }
                    lastValue = precedingLog?.accumulatedValue ?: 0.0
                    lastValue
                }
            }
            ChartPoint(msec, value, isFuture && chartType == 1)
        }
    }

    val projectedEndValue = remember(todayMidnight, currentEnd, logs, dailyRate) {
        val currentProgress = logs.maxByOrNull { it.dateMillis }?.accumulatedValue ?: 0.0
        if (currentEnd > todayMidnight) {
            val daysAhead = ((currentEnd - todayMidnight) / (24 * 60 * 60 * 24 * 1000L)).toDouble()
            currentProgress + (dailyRate * daysAhead)
        } else {
            logs.filter { it.dateMillis <= currentEnd }
                .maxByOrNull { it.dateMillis }?.accumulatedValue ?: currentProgress
        }
    }

    // Compute dynamic, timeframe-specific Y limits safely
    val maxVal = remember(goal.targetAmount, series, selectedTimeframe, chartType, projectedEndValue) {
        val maxSeriesValue = series.filter { !it.value.isNaN() }.maxOfOrNull { it.value } ?: 0.0
        val baseMax = if (selectedTimeframe == 5 || chartType == 2) {
            kotlin.math.max(goal.targetAmount, maxSeriesValue)
        } else {
            kotlin.math.max(maxSeriesValue, projectedEndValue)
        }
        if (baseMax > 0.0) baseMax * 1.05 else 100.0
    }
    val minVal = remember { 0.0 }

    val adjustedMaxVal = remember(maxVal, minVal) {
        if (maxVal <= minVal) minVal + 10.0 else maxVal
    }
    val adjustedMinVal = 0.0

    // Benchmark pacing and projections
    val currentProgress = remember(logs) {
        logs.maxByOrNull { it.dateMillis }?.accumulatedValue ?: 0.0
    }
    val daysRemainingForCalculations = remember(goal.targetDateMillis, todayMidnight) {
        goal.targetDateMillis?.let { target ->
            val diff = target - todayMidnight
            val days = (diff / (1000 * 60 * 60 * 24L)).toInt()
            if (days <= 0) 1 else days
        } ?: 1
    }
    val requiredRateToFinishOnTime = remember(goal.targetAmount, currentProgress, daysRemainingForCalculations) {
        val remaining = goal.targetAmount - currentProgress
        if (remaining > 0) remaining / daysRemainingForCalculations.toDouble() else 0.0
    }
    val projectedFinishText = remember(dailyRate, currentProgress, goal.targetAmount, todayMidnight) {
        if (currentProgress >= goal.targetAmount) {
            "Выполнено!"
        } else if (dailyRate > 0.0) {
            val remainingValue = goal.targetAmount - currentProgress
            val remainingDays = kotlin.math.ceil(remainingValue / dailyRate).toLong()
            val targetTime = todayMidnight + remainingDays * 24 * 60 * 60 * 1000L
            val format = SimpleDateFormat("dd MMMM yyyy", Locale("ru"))
            format.format(Date(targetTime))
        } else {
            "Не будет достигнута"
        }
    }

    // Benchmark/uniform path trajectory calculations
    val startVal = remember(logs) {
        logs.minByOrNull { it.dateMillis }?.accumulatedValue ?: 0.0
    }
    val targetNormalized = remember(goal.targetDateMillis) {
        goal.targetDateMillis?.let { normalizeToMidnight(it) } ?: (todayMidnight + 180 * 24 * 60 * 60 * 1000L)
    }

    // Touch selection tracking
    var activeIdx by remember(chartType, logs) { mutableStateOf<Int?>(null) }

    val dateFormat = remember { SimpleDateFormat("dd.MM", Locale.getDefault()) }

    Column(modifier = modifier) {
        // Technical Pacing & Forecast Metrics Dashboard Panel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .background(Color(0xFF0F1115), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF1E2126), RoundedCornerShape(8.dp))
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Column 1: Required Tempo (Coral Red)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ЦЕЛЕВОЙ ТЕМП",
                    color = Color(0xFFFF5E5E),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "+%,.1f / дн", requiredRateToFinishOnTime),
                    color = CrispWhite,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Vertical divider
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF22252F)))
            Spacer(modifier = Modifier.width(6.dp))

            // Column 2: Actual pace (Electric Cyan)
            Column(modifier = Modifier.weight(1.1f)) {
                Text(
                    text = "ТЕКУЩИЙ ТЕМП",
                    color = ElectricCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = String.format(Locale.US, "+%,.1f / дн", dailyRate),
                    color = CrispWhite,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Vertical divider
            Box(modifier = Modifier.width(1.dp).height(24.dp).background(Color(0xFF22252F)))
            Spacer(modifier = Modifier.width(6.dp))

            // Column 3: Format completion date
            Column(modifier = Modifier.weight(1.4f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "РАСЧЕТ ФИНАЛА",
                    color = TechMutedText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = projectedFinishText,
                    color = if (dailyRate > 0 && currentProgress < goal.targetAmount) ElectricCyanSecondary else TechMutedText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Selection Detail Hover info card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 4.dp),
        ) {
            if (activeIdx != null && activeIdx!! < series.size) {
                val pt = series[activeIdx!!]
                if (!pt.value.isNaN()) {
                    val dateStr = dateFormat.format(Date(pt.dateMillis))
                    val typeStr = if (pt.isForecast) " (Прогноз)" else ""
                    val valueStr = String.format(Locale.US, "%,.2f", pt.value)
                    val percentStr = String.format(Locale.US, "%.1f%%", (pt.value / goal.targetAmount) * 100)

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Дата: $dateStr$typeStr",
                            color = TechMutedText,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "$valueStr / ${String.format(Locale.US, "%,.0f", goal.targetAmount)} ($percentStr)",
                            color = ElectricCyan,
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                        )
                    }
                }
            } else {
                Text(
                    text = "🎯 Зажмите график для изучения контрольных точек",
                    color = TechMutedText.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }

        // Horizontal timeframe zoom/control buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val options = listOf(
                "1 нед",
                "2 нед",
                "1 мес",
                "3 мес",
                "6 мес",
                "Весь срок"
            )
            options.forEachIndexed { index, text ->
                val isSelected = selectedTimeframe == index
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) ElectricCyan.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) ElectricCyan else Color(0xFF1E2126),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { selectedTimeframe = index }
                        .padding(vertical = 5.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = text,
                        color = if (isSelected) ElectricCyan else TechMutedText,
                        fontSize = 10.5.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }

        // The Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .pointerInput(series) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat()
                            val paddingLeft = with(density) { 50.dp.toPx() }
                            val paddingRight = with(density) { 16.dp.toPx() }
                            val drawWidth = width - paddingLeft - paddingRight
                            if (drawWidth > 0 && series.isNotEmpty()) {
                                val touchX = offset.x - paddingLeft
                                val prog = (touchX / drawWidth).coerceIn(0f, 1f)
                                val idx = (prog * (series.size - 1))
                                    .toInt()
                                    .coerceIn(0, series.size - 1)
                                activeIdx = idx
                            }
                        },
                        onDragEnd = { activeIdx = null },
                        onDragCancel = { activeIdx = null },
                        onDrag = { change, _ ->
                            val width = size.width.toFloat()
                            val paddingLeft = with(density) { 50.dp.toPx() }
                            val paddingRight = with(density) { 16.dp.toPx() }
                            val drawWidth = width - paddingLeft - paddingRight
                            if (drawWidth > 0 && series.isNotEmpty()) {
                                val touchX = change.position.x - paddingLeft
                                val prog = (touchX / drawWidth).coerceIn(0f, 1f)
                                val idx = (prog * (series.size - 1))
                                    .toInt()
                                    .coerceIn(0, series.size - 1)
                                activeIdx = idx
                            }
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val paddingLeft = 50.dp.toPx()
                val paddingRight = 16.dp.toPx()
                val paddingTop = 16.dp.toPx()
                val paddingBottom = 24.dp.toPx()

                val drawWidth = size.width - paddingLeft - paddingRight
                val drawHeight = size.height - paddingTop - paddingBottom

                if (drawWidth <= 0 || drawHeight <= 0 || series.isEmpty()) return@Canvas

                // Helpers to compute coordinates
                fun getX(index: Int): Float {
                    return paddingLeft + (index.toFloat() / (series.size - 1)) * drawWidth
                }

                fun getY(value: Double): Float {
                    val range = adjustedMaxVal - adjustedMinVal
                    val ratio = if (range > 0.0) {
                        ((value - adjustedMinVal) / range).coerceIn(0.0, 1.0)
                    } else {
                        0.5
                    }
                    return paddingTop + ((1.0 - ratio) * drawHeight).toFloat()
                }

                // 1. Draw Grid lines and Y axis scales
                val gridLinesCount = 4
                for (i in 0..gridLinesCount) {
                    val ratio = i.toFloat() / gridLinesCount
                    val y = paddingTop + ratio * drawHeight

                    // Draw grid dashed line
                    drawLine(
                        color = Color(0xFF22252F),
                        start = Offset(paddingLeft, y),
                        end = Offset(size.width - paddingRight, y),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    )
                }

                // 1.5. Draw Forecast Vertical stems
                series.forEachIndexed { idx, pt ->
                    if (!pt.value.isNaN() && pt.isForecast) {
                        val x = getX(idx)
                        val yStart = paddingTop + drawHeight
                        val yEnd = getY(pt.value)
                        
                        // Scale the frequency based on density
                        val stepFilter = when {
                            series.size > 140 -> idx % 4 == 0
                            series.size > 70 -> idx % 2 == 0
                            else -> true
                        }
                        
                        if (stepFilter) {
                            drawLine(
                                color = ElectricCyanSecondary.copy(alpha = 0.08f),
                                start = Offset(x, yStart),
                                end = Offset(x, yEnd),
                                strokeWidth = if (series.size > 90) 1.dp.toPx() else 1.5.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)
                            )
                        }
                    }
                }

                // 1.8. Draw Uniform target Benchmark Pace trajectory line (тонкая красная полоса равномерного темпа)
                val benchmarkPath = Path()
                var isBenchFirst = true
                series.forEachIndexed { idx, pt ->
                    val msec = pt.dateMillis
                    val totalDuration = targetNormalized - startMidnight
                    val targetVal = if (totalDuration > 0) {
                        val progressRatio = ((msec - startMidnight).toDouble() / totalDuration.toDouble()).coerceIn(0.0, 1.0)
                        startVal + progressRatio * (goal.targetAmount - startVal)
                    } else {
                        goal.targetAmount
                    }
                    val x = getX(idx)
                    val y = getY(targetVal)
                    if (isBenchFirst) {
                        benchmarkPath.moveTo(x, y)
                        isBenchFirst = false
                    } else {
                        benchmarkPath.lineTo(x, y)
                    }
                }
                drawPath(
                    path = benchmarkPath,
                    color = Color(0xFFFF5E5E), // Vivid tech red
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 6f), 0f)
                    )
                )

                // 2. Draw Target amount horizontal guideline in deep accent (if visible within scale limits)
                if (goal.targetAmount <= adjustedMaxVal) {
                    val targetY = getY(goal.targetAmount)
                    drawLine(
                        color = ElectricCyanSecondary.copy(alpha = 0.4f),
                        start = Offset(paddingLeft, targetY),
                        end = Offset(size.width - paddingRight, targetY),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 5f), 0f)
                    )
                }

                // 3. Draw Actual Segmented Columns (Pillars from 0 to value)
                val stepWidth = drawWidth / (series.size - 1).coerceAtLeast(1)
                val barGapFraction = 0.30f // 70% bar width, 30% spacing gap
                val barWidth = (stepWidth * (1f - barGapFraction)).coerceIn(1.5.dp.toPx(), 24.dp.toPx())
                
                series.forEachIndexed { idx, pt ->
                    if (!pt.isForecast && !pt.value.isNaN()) {
                        val cx = getX(idx)
                        val cy = getY(pt.value)
                        val baseLineY = paddingTop + drawHeight
                        val barHeight = baseLineY - cy
                        
                        if (barHeight > 0f) {
                            drawRoundRect(
                                color = ElectricCyan,
                                topLeft = Offset(cx - barWidth / 2f, cy),
                                size = Size(barWidth, barHeight),
                                cornerRadius = CornerRadius((barWidth * 0.25f).coerceAtMost(6.dp.toPx()))
                            )
                        }
                    }
                }

                var prevOffset: Offset? = null

                val actualPath = Path()
                val forecastPath = Path()

                series.forEachIndexed { idx, pt ->
                    if (!pt.value.isNaN()) {
                        val x = getX(idx)
                        val y = getY(pt.value)
                        val pointOffset = Offset(x, y)

                        if (idx == 0) {
                            actualPath.moveTo(x, y)
                        } else {
                            val previous = series[idx - 1]
                            if (previous.value.isNaN()) {
                                // Jump transition
                                if (pt.isForecast) {
                                    forecastPath.moveTo(x, y)
                                } else {
                                    actualPath.moveTo(x, y)
                                }
                            } else {
                                if (pt.isForecast) {
                                    if (!previous.isForecast) {
                                        // Transition point from actual to forecast
                                        forecastPath.moveTo(prevOffset!!.x, prevOffset!!.y)
                                    }
                                    forecastPath.lineTo(x, y)
                                } else {
                                    actualPath.lineTo(x, y)
                                }
                            }
                        }
                        prevOffset = pointOffset
                    }
                }

                // We do NOT draw the actualPath line because we are fully rendering the actual section with solid polished pillars.
                // This keeps the presentation neat and avoids jagged zigzag overlaps on rounded bars.

                // Draw dashed forecast line
                drawPath(
                    path = forecastPath,
                    color = ElectricCyanSecondary,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 10f), 0f)
                    )
                )

                // 4. Draw active interactive cursor (reticle line & circle point)
                activeIdx?.let { idx ->
                    if (idx < series.size) {
                        val pt = series[idx]
                        if (!pt.value.isNaN()) {
                            val cx = getX(idx)
                            val cy = getY(pt.value)

                            // Vertical rule
                            drawLine(
                                color = TechMutedText.copy(alpha = 0.4f),
                                start = Offset(cx, paddingTop),
                                end = Offset(cx, paddingTop + drawHeight),
                                strokeWidth = 1.dp.toPx()
                            )

                            // Highlight core balance point on the curve
                            drawCircle(
                                color = MonolithSurfaceVariant,
                                radius = 7.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = if (pt.isForecast) ElectricCyanSecondary else ElectricCyan,
                                radius = 4.dp.toPx(),
                                center = Offset(cx, cy)
                            )
                        }
                    }
                }

                // 5. Draw border ticks / visual alignment lines
                drawLine(
                    color = Color(0xFF22252F),
                    start = Offset(paddingLeft, paddingTop),
                    end = Offset(paddingLeft, paddingTop + drawHeight),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = Color(0xFF22252F),
                    start = Offset(paddingLeft, paddingTop + drawHeight),
                    end = Offset(size.width - paddingRight, paddingTop + drawHeight),
                    strokeWidth = 1.5.dp.toPx()
                )
            }

            // HTML-like overlays for Y scales & X labels
            val yLabels = listOf(adjustedMaxVal, (adjustedMaxVal + adjustedMinVal) / 2.0, adjustedMinVal)
            yLabels.forEachIndexed { idx, value ->
                Box(
                    modifier = Modifier
                        .padding(start = 4.dp)
                        .offset(
                            y = with(density) {
                                (16 + (idx.toFloat() / 2) * 128).dp
                            }
                        )
                ) {
                    Text(
                        text = String.format(Locale.US, "%,.0f", value),
                        color = TechMutedText,
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            // Timeline boundary labels at the bottom of the graph with precise horizontal layout positioning
            if (series.isNotEmpty()) {
                val startLabel = dateFormat.format(Date(series.first().dateMillis))
                val endLabel = dateFormat.format(Date(series.last().dateMillis))

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(androidx.compose.ui.Alignment.BottomCenter)
                        .padding(start = 50.dp, end = 16.dp, bottom = 4.dp)
                        .height(20.dp)
                ) {
                    val containerWidth = maxWidth

                    // Start Label (left aligned)
                    Text(
                        text = startLabel,
                        color = TechMutedText,
                        fontSize = 10.sp,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterStart)
                    )

                    // "Сегодня" marker dynamically positioned according to its real coordinate
                    val todayIdx = series.indexOfFirst { it.dateMillis == todayMidnight }
                    if (todayIdx in 1 until series.size - 1) {
                        val ratio = todayIdx.toFloat() / (series.size - 1)
                        // Only show if it's sufficiently inside the timeline box to avoid collisions with bounds
                        if (ratio in 0.15f..0.85f) {
                            Text(
                                text = "Сегодня",
                                color = ElectricCyan.copy(alpha = 0.9f),
                                fontSize = 10.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.CenterStart)
                                    .offset(x = containerWidth * ratio - 20.dp)
                            )
                        }
                    }

                    // End Label (right aligned)
                    Text(
                        text = endLabel,
                        color = TechMutedText,
                        fontSize = 10.sp,
                        modifier = Modifier.align(androidx.compose.ui.Alignment.CenterEnd)
                    )
                }
            }
        }
    }
}

data class ChartPoint(
    val dateMillis: Long,
    val value: Double,
    val isForecast: Boolean
)
