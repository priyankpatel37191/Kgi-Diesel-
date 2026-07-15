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
}

@Database(
    entities = [User::class, Load::class, ChatMessage::class, CommissionPayment::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun loadDao(): LoadDao
    abstract fun chatDao(): ChatDao
    abstract fun commissionDao(): CommissionDao
}
