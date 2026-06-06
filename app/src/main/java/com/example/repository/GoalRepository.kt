package com.example.repository

import com.example.database.Goal
import com.example.database.GoalDao
import com.example.database.ProgressLog
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import java.util.TimeZone

class GoalRepository(private val goalDao: GoalDao) {

    val allGoalsFlow: Flow<List<Goal>> = goalDao.getAllGoalsFlow()

    val allProgressLogsFlow: Flow<List<ProgressLog>> = goalDao.getAllProgressLogsFlow()

    val activeGoalFlow: Flow<Goal?> = goalDao.getActiveGoalFlow()

    fun getGoalFlow(goalId: Long): Flow<Goal?> = goalDao.getGoalFlow(goalId)

    suspend fun getGoalById(goalId: Long): Goal? = goalDao.getGoalById(goalId)

    fun getProgressLogsFlow(goalId: Long): Flow<List<ProgressLog>> =
        goalDao.getProgressLogsFlow(goalId)

    suspend fun getActiveGoal(): Goal? = goalDao.getActiveGoal()

    suspend fun createGoal(title: String, targetAmount: Double, startDateMillis: Long, targetDateMillis: Long?): Long {
        val newGoal = Goal(
            title = title,
            targetAmount = targetAmount,
            startDateMillis = startDateMillis,
            targetDateMillis = targetDateMillis
        )
        return goalDao.insertGoal(newGoal)
    }

    suspend fun updateGoal(goal: Goal) {
        goalDao.updateGoal(goal)
    }

    suspend fun deleteGoal(goalId: Long) {
        goalDao.deleteGoal(goalId)
    }

    suspend fun logProgress(goalId: Long, dateMillis: Long, value: Double) {
        val normalizedDate = normalizeToMidnight(dateMillis)
        val log = ProgressLog(
            goalId = goalId,
            dateMillis = normalizedDate,
            accumulatedValue = value
        )
        goalDao.insertProgressLog(log)
    }

    suspend fun deleteProgressLog(goalId: Long, dateMillis: Long) {
        val normalizedDate = normalizeToMidnight(dateMillis)
        goalDao.deleteProgressLog(goalId, normalizedDate)
    }

    fun normalizeToMidnight(timestamp: Long): Long {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.timeInMillis = timestamp
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
