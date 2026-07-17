package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users WHERE id = :id")
    fun getUserById(id: Int): Flow<User?>

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserByIdSync(id: Int): User?

    @Query("SELECT * FROM users WHERE phone = :phone LIMIT 1")
    suspend fun getUserByPhoneSync(phone: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Update
    suspend fun updateUser(user: User)

    @Query("SELECT * FROM users WHERE id IN (:ids)")
    fun getDriversByIds(ids: List<Int>): Flow<List<User>>

    @Query("SELECT * FROM users WHERE role = 'DRIVER'")
    fun getAllDrivers(): Flow<List<User>>
}

@Dao
interface LoadDao {
    @Query("SELECT * FROM loads ORDER BY createdAt DESC")
    fun getAllLoads(): Flow<List<Load>>

    @Query("SELECT * FROM loads WHERE shipperId = :shipperId ORDER BY createdAt DESC")
    fun getLoadsByShipper(shipperId: Int): Flow<List<Load>>

    @Query("SELECT * FROM loads WHERE assignedDriverId = :driverId ORDER BY createdAt DESC")
    fun getLoadsByDriver(driverId: Int): Flow<List<Load>>

    @Query("SELECT * FROM loads WHERE id = :id")
    fun getLoadById(id: Int): Flow<Load?>

    @Query("SELECT * FROM loads WHERE id = :id")
    suspend fun getLoadByIdSync(id: Int): Load?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoad(load: Load): Long

    @Update
    suspend fun updateLoad(load: Load)

    @Query("SELECT COUNT(*) FROM loads WHERE assignedDriverId = :driverId AND shipperId = :shipperId AND status = 'COMPLETED'")
    suspend fun getCompletedLoadsCountBetween(driverId: Int, shipperId: Int): Int

    @Query("SELECT COUNT(*) FROM loads WHERE shipperId = :shipperId AND status = 'COMPLETED'")
    suspend fun getCompletedLoadsCountForShipper(shipperId: Int): Int

    @Query("SELECT COUNT(*) FROM loads WHERE assignedDriverId = :driverId AND status = 'COMPLETED'")
    suspend fun getCompletedLoadsCountForDriver(driverId: Int): Int
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE loadId = :loadId ORDER BY timestamp ASC")
    fun getChatMessagesForLoad(loadId: Int): Flow<List<ChatMessage>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessage)
}

@Dao
interface CommissionDao {
    @Query("SELECT * FROM commissions WHERE loadId = :loadId LIMIT 1")
    fun getCommissionForLoad(loadId: Int): Flow<CommissionPayment?>

    @Query("SELECT * FROM commissions WHERE loadId = :loadId LIMIT 1")
    suspend fun getCommissionForLoadSync(loadId: Int): CommissionPayment?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCommission(commission: CommissionPayment): Long

    @Update
    suspend fun updateCommission(commission: CommissionPayment)

    @Query("SELECT * FROM commissions WHERE shipperId = :shipperId AND isPaid = 0")
    suspend fun getUnpaidCommissionsForShipper(shipperId: Int): List<CommissionPayment>

    @Query("SELECT * FROM commissions WHERE driverId = :driverId AND isPaid = 0")
    suspend fun getUnpaidCommissionsForDriver(driverId: Int): List<CommissionPayment>
}

@Dao
interface JobDao {
    @Query("SELECT * FROM job_profiles ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<JobProfile>>

    @Query("SELECT * FROM job_profiles WHERE shipperId = :shipperId ORDER BY createdAt DESC")
    fun getJobsByShipper(shipperId: Int): Flow<List<JobProfile>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobProfile): Long

    @Update
    suspend fun updateJob(job: JobProfile)

    @Query("SELECT * FROM job_profiles WHERE id = :id")
    suspend fun getJobByIdSync(id: Int): JobProfile?
}

@Database(
    entities = [User::class, Load::class, ChatMessage::class, CommissionPayment::class, JobProfile::class],
    version = 2, // Bump version to force destructive migration cleanly
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun loadDao(): LoadDao
    abstract fun chatDao(): ChatDao
    abstract fun commissionDao(): CommissionDao
    abstract fun jobDao(): JobDao
}
