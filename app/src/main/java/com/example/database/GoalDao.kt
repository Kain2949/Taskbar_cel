package com.example.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals WHERE isArchived = 0 ORDER BY id DESC")
    fun getAllGoalsFlow(): Flow<List<Goal>>

    @Query("SELECT * FROM goals WHERE isArchived = 0 LIMIT 1")
    fun getActiveGoalFlow(): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE isArchived = 0 LIMIT 1")
    suspend fun getActiveGoal(): Goal?

    @Query("SELECT * FROM goals WHERE id = :goalId")
    fun getGoalFlow(goalId: Long): Flow<Goal?>

    @Query("SELECT * FROM goals WHERE id = :goalId")
    suspend fun getGoalById(goalId: Long): Goal?

    @Query("SELECT * FROM progress_logs ORDER BY dateMillis ASC")
    fun getAllProgressLogsFlow(): Flow<List<ProgressLog>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: Goal): Long

    @Update
    suspend fun updateGoal(goal: Goal)

    @Query("UPDATE goals SET isArchived = 1 WHERE id = :goalId")
    suspend fun archiveGoal(goalId: Long)

    @Query("DELETE FROM goals WHERE id = :goalId")
    suspend fun deleteGoal(goalId: Long)

    @Query("SELECT * FROM progress_logs WHERE goalId = :goalId ORDER BY dateMillis ASC")
    fun getProgressLogsFlow(goalId: Long): Flow<List<ProgressLog>>

    @Query("SELECT * FROM progress_logs WHERE goalId = :goalId ORDER BY dateMillis ASC")
    suspend fun getProgressLogs(goalId: Long): List<ProgressLog>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgressLog(log: ProgressLog)

    @Query("DELETE FROM progress_logs WHERE goalId = :goalId AND dateMillis = :dateMillis")
    suspend fun deleteProgressLog(goalId: Long, dateMillis: Long)
}
