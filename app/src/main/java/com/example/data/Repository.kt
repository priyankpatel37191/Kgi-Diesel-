package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val userDao: UserDao,
    private val loadDao: LoadDao,
    private val chatDao: ChatDao,
    private val commissionDao: CommissionDao
) {
    fun getUser(id: Int): Flow<User?> = userDao.getUserById(id)
    suspend fun getUserSync(id: Int): User? = userDao.getUserByIdSync(id)
    suspend fun getUserByPhoneSync(phone: String): User? = userDao.getUserByPhoneSync(phone)
    suspend fun insertUser(user: User): Long = userDao.insertUser(user)
    suspend fun updateUser(user: User) = userDao.updateUser(user)
    fun getDriversByIds(ids: List<Int>): Flow<List<User>> = userDao.getDriversByIds(ids)

    fun getAllLoads(): Flow<List<Load>> = loadDao.getAllLoads()
    fun getLoadsByShipper(shipperId: Int): Flow<List<Load>> = loadDao.getLoadsByShipper(shipperId)
    fun getLoadsByDriver(driverId: Int): Flow<List<Load>> = loadDao.getLoadsByDriver(driverId)
    fun getLoadById(id: Int): Flow<Load?> = loadDao.getLoadById(id)
    suspend fun getLoadByIdSync(id: Int): Load? = loadDao.getLoadByIdSync(id)
    suspend fun insertLoad(load: Load): Long = loadDao.insertLoad(load)
    suspend fun updateLoad(load: Load) = loadDao.updateLoad(load)
    suspend fun getCompletedLoadsCountBetween(driverId: Int, shipperId: Int): Int =
        loadDao.getCompletedLoadsCountBetween(driverId, shipperId)

    fun getChatMessagesForLoad(loadId: Int): Flow<List<ChatMessage>> = chatDao.getChatMessagesForLoad(loadId)
    suspend fun insertChatMessage(message: ChatMessage) = chatDao.insertChatMessage(message)

    fun getCommissionForLoad(loadId: Int): Flow<CommissionPayment?> = commissionDao.getCommissionForLoad(loadId)
    suspend fun getCommissionForLoadSync(loadId: Int): CommissionPayment? = commissionDao.getCommissionForLoadSync(loadId)
    suspend fun insertCommission(commission: CommissionPayment): Long = commissionDao.insertCommission(commission)
    suspend fun updateCommission(commission: CommissionPayment) = commissionDao.updateCommission(commission)
}
