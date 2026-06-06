package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.database.Goal
import com.example.database.ProgressLog
import com.example.repository.GoalRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GoalProgressItem(
    val goal: Goal,
    val currentProgress: Double,
    val progressPercentage: Float
)

sealed interface GoalScreen {
    object MainList : GoalScreen
    object CreateGoal : GoalScreen
    data class Dashboard(val goalId: Long) : GoalScreen
}

sealed interface GoalUiState {
    object Loading : GoalUiState
    data class Dashboard(
        val goal: Goal,
        val logs: List<ProgressLog>,
        val currentProgress: Double,
        val progressPercentage: Float,
        val daysRemaining: Int?, // For target end date
        val dailyRate: Double,
        val projectedCompletionDate: Long?
    ) : GoalUiState
}

class GoalViewModel(private val repository: GoalRepository) : ViewModel() {

    private val _currentScreen = MutableStateFlow<GoalScreen>(GoalScreen.MainList)
    val currentScreen: StateFlow<GoalScreen> = _currentScreen.asStateFlow()

    // Flow representing lists of all goals and their progressive data
    val allGoalsProgress: StateFlow<List<GoalProgressItem>> = combine(
        repository.allGoalsFlow,
        repository.allProgressLogsFlow
    ) { goals, logs ->
        val logMap = logs.groupBy { it.goalId }
        goals.map { goal ->
            val gLogs = logMap[goal.id] ?: emptyList()
            val latestLog = gLogs.maxByOrNull { it.dateMillis }
            val currentProgress = latestLog?.accumulatedValue ?: 0.0
            val percentage = if (goal.targetAmount > 0) {
                (currentProgress / goal.targetAmount).coerceIn(0.0, 2.0).toFloat()
            } else {
                0f
            }
            GoalProgressItem(goal, currentProgress, percentage)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active screen state matching selected detail
    val activeDashboardState: StateFlow<GoalUiState?> = _currentScreen.flatMapLatest { screen ->
        if (screen is GoalScreen.Dashboard) {
            val goalId = screen.goalId
            combine(
                repository.getGoalFlow(goalId),
                repository.getProgressLogsFlow(goalId)
            ) { goal, logs ->
                if (goal == null) {
                    null
                } else {
                    calculateDashboard(goal, logs)
                }
            }
        } else {
            flowOf(null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun calculateDashboard(goal: Goal, logs: List<ProgressLog>): GoalUiState.Dashboard {
        val latestLog = logs.maxByOrNull { it.dateMillis }
        val currentProgress = latestLog?.accumulatedValue ?: 0.0
        val percentage = if (goal.targetAmount > 0) {
            (currentProgress / goal.targetAmount).coerceIn(0.0, 2.0).toFloat()
        } else {
            0f
        }

        // Days remaining
        val nowMidnight = repository.normalizeToMidnight(System.currentTimeMillis())
        val daysRemaining = goal.targetDateMillis?.let { target ->
            val diff = target - nowMidnight
            if (diff <= 0) 0 else (diff / (1000 * 60 * 60 * 24L)).toInt()
        }

        // Current pace / daily rate
        val sortedLogs = logs.sortedBy { it.dateMillis }
        val earliestLog = sortedLogs.firstOrNull()
        
        val dailyRate = if (latestLog != null && earliestLog != null && latestLog.dateMillis > earliestLog.dateMillis) {
            val valueDiff = latestLog.accumulatedValue - earliestLog.accumulatedValue
            val daysDiff = ((latestLog.dateMillis - earliestLog.dateMillis) / (1000 * 60 * 60 * 24L)).toInt().coerceAtLeast(1)
            valueDiff / daysDiff
        } else {
            val startMillis = if (earliestLog != null) {
                kotlin.math.min(goal.startDateMillis, earliestLog.dateMillis)
            } else {
                goal.startDateMillis
            }
            val elapsedMillis = nowMidnight - repository.normalizeToMidnight(startMillis)
            val elapsedDays = (elapsedMillis / (1000 * 60 * 60 * 24L)).toInt().coerceAtLeast(1)
            currentProgress / elapsedDays
        }

        // Project completion
        val projectedCompletionDate = if (dailyRate > 0 && currentProgress < goal.targetAmount) {
            val remainingAmount = goal.targetAmount - currentProgress
            val remainingDays = (remainingAmount / dailyRate).toLong()
            nowMidnight + (remainingDays * 1000 * 60 * 60 * 24L)
        } else if (currentProgress >= goal.targetAmount) {
            latestLog?.dateMillis ?: goal.startDateMillis
        } else {
            null
        }

        return GoalUiState.Dashboard(
            goal = goal,
            logs = logs,
            currentProgress = currentProgress,
            progressPercentage = percentage,
            daysRemaining = daysRemaining,
            dailyRate = dailyRate,
            projectedCompletionDate = projectedCompletionDate
        )
    }

    // Navigation APIs
    fun navigateToMainList() {
        _currentScreen.value = GoalScreen.MainList
    }

    fun navigateToCreateGoal() {
        _currentScreen.value = GoalScreen.CreateGoal
    }

    fun selectGoal(goalId: Long) {
        _currentScreen.value = GoalScreen.Dashboard(goalId)
    }

    // Business Logic APIs
    fun startGoal(title: String, targetAmount: Double, targetDateMillis: Long?) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.createGoal(
                title = title,
                targetAmount = targetAmount,
                startDateMillis = now,
                targetDateMillis = targetDateMillis
            )
            navigateToMainList()
        }
    }

    fun deleteGoal(goalId: Long) {
        viewModelScope.launch {
            repository.deleteGoal(goalId)
            navigateToMainList()
        }
    }

    fun logProgress(goalId: Long, value: Double, dateMillis: Long) {
        viewModelScope.launch {
            repository.logProgress(goalId, dateMillis, value)
        }
    }

    fun deleteLog(goalId: Long, dateMillis: Long) {
        viewModelScope.launch {
            repository.deleteProgressLog(goalId, dateMillis)
        }
    }
}

class GoalViewModelFactory(private val repository: GoalRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(GoalViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return GoalViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
