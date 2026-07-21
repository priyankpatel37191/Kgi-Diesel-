package com.example.ui

import android.app.Application
import android.content.Context
import android.location.Location
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.KgiApplication
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainViewModel(
    application: Application,
    private val repository: AppRepository
) : AndroidViewModel(application) {

    // Language state: "en" for English, "hi" for Hindi
    var language by mutableStateOf("en")
        private set

    fun toggleLanguage() {
        language = if (language == "en") "hi" else "en"
    }

    // Current logged in user ID
    private val _currentUserId = MutableStateFlow<Int?>(null)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val currentUser: StateFlow<User?> = _currentUserId
        .flatMapLatest { id ->
            if (id != null) repository.getUser(id)
            else flowOf(null)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Flag to check if current driver location tracking is enabled
    var gpsEnabled by mutableStateOf(false)
        private set

    // Simulated coordinates of driver (Jaipur area by default)
    var driverLat by mutableStateOf(26.9124)
        private set
    var driverLng by mutableStateOf(75.7873)
        private set

    fun toggleGps() {
        gpsEnabled = !gpsEnabled
        if (gpsEnabled) {
            // Randomize slightly around highway NH-48 Jaipur
            driverLat = 26.9124 + Random.nextDouble(-0.05, 0.05)
            driverLng = 75.7873 + Random.nextDouble(-0.05, 0.05)
        }
    }

    // Navigation state (for simple app state navigation if desired, or backup)
    var currentScreen by mutableStateOf("landing")

    // Active Load lists
    val allLoads: StateFlow<List<Load>> = repository.getAllLoads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All registered drivers (for Shippers to review and approve/reject)
    val allDrivers: StateFlow<List<User>> = repository.getAllDrivers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live chat messages for the currently selected load
    private val _activeLoadIdForChat = MutableStateFlow<Int?>(null)
    val activeChatMessages: StateFlow<List<ChatMessage>> = _activeLoadIdForChat
        .flatMapLatest { loadId ->
            if (loadId != null) repository.getChatMessagesForLoad(loadId)
            else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Real-time tracking animation progress map [LoadId -> Progress 0.0 to 1.0]
    private val _loadTrackingProgress = MutableStateFlow<Map<Int, Double>>(emptyMap())
    val loadTrackingProgress: StateFlow<Map<Int, Double>> = _loadTrackingProgress.asStateFlow()

    // Tracking tracking jobs to cancel if needed
    private val activeTrackingJobs = mutableMapOf<Int, Job>()

    // Configurable commission rate (0.5% - 1.0%), default is 0.8%
    var commissionRate by mutableStateOf(0.008)
    
    // User configured UPI ID for receiving/sending
    var adminUpiId by mutableStateOf("9660033436@pthdfc")

    init {
        // Create demo data if empty
        viewModelScope.launch {
            delay(500) // wait for database to be fully up
            repository.getAllLoads().first().let { currentLoads ->
                if (currentLoads.isEmpty()) {
                    createDemoData()
                }
            }
        }
    }

    private suspend fun createDemoData() {
        // Create a couple of mock shippers and drivers
        val demoShipper = User(
            name = "Rajesh Senders",
            phone = "9876543210",
            role = "SHIPPER",
            isApproved = true
        )
        val shipperId = repository.insertUser(demoShipper).toInt()

        val demoDriver = User(
            name = "Karan Singh",
            phone = "9112233445",
            role = "DRIVER",
            truckSize = "19 Feet",
            truckNumber = "RJ-14-GB-8822",
            rcPath = "demo_rc.png",
            dlPath = "demo_dl.png",
            aadhaarPath = "demo_aadhaar.png",
            permitPath = "demo_permit.png",
            isApproved = true
        )
        val driverId = repository.insertUser(demoDriver).toInt()

        // Create sample loads
        val load1 = Load(
            shipperId = shipperId,
            shipperName = "Rajesh Senders",
            shipperPhone = "9876543210",
            pickupLocation = "Jaipur Industrial Area",
            dropLocation = "Delhi Okhla Phase 3",
            loadType = "Auto Parts",
            weightTons = 6.5,
            truckSize = "19 Feet",
            distanceKm = 270.0,
            ratePerKm = 28.0,
            ratePerTon = 100.0,
            totalFare = 270.0 * 28.0 + 6.5 * 100.0, // 7560 + 650 = 8210
            status = "POSTED",
            interestedDriverIdsString = ""
        )
        repository.insertLoad(load1)

        val load2 = Load(
            shipperId = shipperId,
            shipperName = "Rajesh Senders",
            shipperPhone = "9876543210",
            pickupLocation = "Jaipur VKI",
            dropLocation = "Mumbai Kalamboli",
            loadType = "Steel Sheets",
            weightTons = 12.0,
            truckSize = "32 Feet",
            distanceKm = 1150.0,
            ratePerKm = 42.0,
            ratePerTon = 150.0,
            totalFare = 1150.0 * 42.0 + 12.0 * 150.0, // 48300 + 1800 = 50100
            status = "POSTED",
            interestedDriverIdsString = "$driverId" // driver interested
        )
        repository.insertLoad(load2)
    }

    // Phone Normalization Helper
    fun normalizePhone(phone: String): String {
        val digitsOnly = phone.filter { it.isDigit() }
        return if (digitsOnly.length >= 10) {
            digitsOnly.takeLast(10)
        } else {
            digitsOnly
        }
    }

    // Authentication
    fun login(phone: String, role: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            val existing = repository.getUserByPhoneSync(normalized)
            if (existing != null) {
                if (existing.role != role) {
                    onResult(false, "User already registered as ${existing.role}.")
                } else {
                    _currentUserId.value = existing.id
                    currentScreen = if (existing.role == "DRIVER") "driver_home" else "shipper_home"
                    onResult(true, "Welcome back, ${existing.name}!")
                }
            } else {
                onResult(false, "User not found. Please sign up first.")
            }
        }
    }

    fun signupDriver(
        name: String,
        phone: String,
        truckSize: String,
        truckNumber: String,
        rc: String,
        dl: String,
        aadhaar: String,
        permit: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank() || truckNumber.isBlank()) {
                onResult(false, "Please fill in all mandatory fields.")
                return@launch
            }
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            val existing = repository.getUserByPhoneSync(normalized)
            if (existing != null) {
                onResult(false, "Phone number already registered as ${existing.role}.")
                return@launch
            }

            val newUser = User(
                role = "DRIVER",
                name = name,
                phone = normalized,
                truckSize = truckSize,
                truckNumber = truckNumber,
                rcPath = rc.ifBlank { "simulated_rc.jpg" },
                dlPath = dl.ifBlank { "simulated_dl.jpg" },
                aadhaarPath = aadhaar.ifBlank { "simulated_aadhaar.jpg" },
                permitPath = permit.ifBlank { "simulated_permit.jpg" },
                isApproved = true // Automatically approve demo drivers for ease of testing!
            )

            val newId = repository.insertUser(newUser).toInt()
            _currentUserId.value = newId
            currentScreen = "driver_home"
            onResult(true, "Driver profile created successfully!")
        }
    }

    fun signupShipper(
        name: String,
        phone: String,
        onResult: (Boolean, String) -> Unit
    ) {
        viewModelScope.launch {
            if (name.isBlank() || phone.isBlank()) {
                onResult(false, "Please fill in all mandatory fields.")
                return@launch
            }
            val normalized = normalizePhone(phone)
            if (normalized.length != 10) {
                onResult(false, "Invalid phone number. Must be exactly 10 digits.")
                return@launch
            }
            val existing = repository.getUserByPhoneSync(normalized)
            if (existing != null) {
                onResult(false, "Phone number already registered as ${existing.role}.")
                return@launch
            }

            val newUser = User(
                role = "SHIPPER",
                name = name,
                phone = normalized,
                isApproved = true
            )

            val newId = repository.insertUser(newUser).toInt()
            _currentUserId.value = newId
            currentScreen = "shipper_home"
            onResult(true, "Shipper account created successfully!")
        }
    }

    fun logout() {
        _currentUserId.value = null
        currentScreen = "landing"
    }

    // Driver: Express interest in a load
    fun expressInterest(loadId: Int) {
        val driver = currentUser.value ?: return
        if (driver.role != "DRIVER") return

        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            val updated = load.withInterestFromDriver(driver.id)
            repository.updateLoad(updated)
        }
    }

    // Shipper: Post load
    fun postLoad(
        pickup: String,
        drop: String,
        loadType: String,
        weight: Double,
        truckSize: String,
        distance: Double,
        rateKm: Double,
        rateTon: Double,
        onResult: (Boolean, String) -> Unit
    ) {
        val shipper = currentUser.value ?: return
        if (shipper.role != "SHIPPER") return

        viewModelScope.launch {
            if (pickup.isBlank() || drop.isBlank() || loadType.isBlank() || weight <= 0 || distance <= 0) {
                onResult(false, "Please fill all fields with valid inputs.")
                return@launch
            }

            val totalFare = (distance * rateKm) + (weight * rateTon)
            val newLoad = Load(
                shipperId = shipper.id,
                shipperName = shipper.name,
                shipperPhone = shipper.phone,
                pickupLocation = pickup,
                dropLocation = drop,
                loadType = loadType,
                weightTons = weight,
                truckSize = truckSize,
                distanceKm = distance,
                ratePerKm = rateKm,
                ratePerTon = rateTon,
                totalFare = totalFare,
                status = "POSTED"
            )

            repository.insertLoad(newLoad)
            onResult(true, "Load posted successfully!")
        }
    }

    // Check if commission is required/paid before proceeding
    // Rules:
    // First trip between a given driver-shipper pair: commission-free
    // Second trip onward: commission is mandatory before proceeding.
    // If unpaid, blocks progress.
    suspend fun isCommissionRequiredAndUnpaid(driverId: Int, shipperId: Int, loadId: Int): Boolean {
        // Query completed loads count between this driver and shipper
        val completedCount = repository.getCompletedLoadsCountBetween(driverId, shipperId)
        
        // If this is the very first completed trip, count is 0, so free!
        // Wait, if completedCount >= 1, it means they already completed 1 trip together.
        // So the second trip onwards requires commission.
        if (completedCount == 0) return false

        // Check if there is an unpaid commission record for this specific load
        val commission = repository.getCommissionForLoadSync(loadId)
        if (commission == null) {
            // Create a pending commission record for this load
            val load = repository.getLoadByIdSync(loadId) ?: return false
            val commissionAmount = load.totalFare * commissionRate
            val newCommission = CommissionPayment(
                driverId = driverId,
                shipperId = shipperId,
                loadId = loadId,
                amount = commissionAmount,
                isPaid = false,
                upiIdUsed = adminUpiId
            )
            repository.insertCommission(newCommission)
            return true
        }
        return !commission.isPaid
    }

    // Pay commission
    fun payCommission(loadId: Int) {
        viewModelScope.launch {
            val commission = repository.getCommissionForLoadSync(loadId)
            if (commission != null) {
                val updated = commission.copy(isPaid = true)
                repository.updateCommission(updated)
            }
        }
    }

    // Shipper: Accept driver
    fun acceptDriverForLoad(loadId: Int, driverId: Int, onBlock: (Double) -> Unit, onSuccess: () -> Unit) {
        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            val driver = repository.getUserSync(driverId) ?: return@launch

            // Check if second trip onward commission is unpaid!
            val unpaid = isCommissionRequiredAndUnpaid(driverId, load.shipperId, loadId)
            if (unpaid) {
                val commission = repository.getCommissionForLoadSync(loadId)
                val amount = commission?.amount ?: (load.totalFare * commissionRate)
                onBlock(amount)
                return@launch
            }

            // Accept driver
            val updated = load.copy(
                assignedDriverId = driverId,
                assignedDriverName = driver.name,
                assignedDriverPhone = driver.phone,
                status = "ACCEPTED"
            )
            repository.updateLoad(updated)
            onSuccess()

            // Automatically send initial greeting in chat
            repository.insertChatMessage(
                ChatMessage(
                    loadId = loadId,
                    senderId = load.shipperId,
                    senderName = load.shipperName,
                    text = "Hello ${driver.name}, I have accepted your interest for this load. Let's arrange pickup!"
                )
            )
        }
    }

    // Driver: Update trip status (e.g. Start trip, Complete trip)
    fun updateTripStatus(loadId: Int, newStatus: String) {
        viewModelScope.launch {
            val load = repository.getLoadByIdSync(loadId) ?: return@launch
            val updated = load.copy(status = newStatus)
            repository.updateLoad(updated)

            if (newStatus == "ONGOING") {
                startTrackingAnimation(loadId)
            } else if (newStatus == "COMPLETED") {
                stopTrackingAnimation(loadId)

                // When load is completed, generate commissions if shipper/driver completed count >= 2
                val shipperId = load.shipperId
                val shipperCompletedCount = repository.getCompletedLoadsCountForShipper(shipperId)
                if (shipperCompletedCount >= 2) {
                    val commissionAmount = load.totalFare * commissionRate
                    val newCommission = CommissionPayment(
                        driverId = 0,
                        shipperId = shipperId,
                        loadId = loadId,
                        amount = commissionAmount,
                        isPaid = false,
                        upiIdUsed = adminUpiId
                    )
                    repository.insertCommission(newCommission)
                }

                val driverId = load.assignedDriverId
                if (driverId != null) {
                    val driverCompletedCount = repository.getCompletedLoadsCountForDriver(driverId)
                    if (driverCompletedCount >= 2) {
                        val commissionAmount = load.totalFare * commissionRate
                        val newCommission = CommissionPayment(
                            driverId = driverId,
                            shipperId = 0,
                            loadId = loadId,
                            amount = commissionAmount,
                            isPaid = false,
                            upiIdUsed = adminUpiId
                        )
                        repository.insertCommission(newCommission)
                    }
                }
            }
        }
    }

    // Job Posting and Application Lists & Actions
    val allJobs: StateFlow<List<JobProfile>> = repository.getAllJobs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun postJob(
        workTitle: String,
        salaryText: String,
        location: String,
        description: String,
        onResult: (Boolean, String) -> Unit
    ) {
        val shipper = currentUser.value ?: return
        viewModelScope.launch {
            if (workTitle.isBlank() || salaryText.isBlank() || location.isBlank() || description.isBlank()) {
                onResult(false, "Please fill in all fields with valid information.")
                return@launch
            }
            val job = JobProfile(
                shipperId = shipper.id,
                shipperName = shipper.name,
                shipperPhone = shipper.phone,
                workTitle = workTitle,
                salaryText = salaryText,
                location = location,
                description = description
            )
            repository.insertJob(job)
            onResult(true, "Job profile posted successfully!")
        }
    }

    fun updateDriverDocs(dl: String, rc: String, aadhaar: String, permit: String, onComplete: () -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(
                dlPath = dl,
                rcPath = rc,
                aadhaarPath = aadhaar,
                permitPath = permit
            )
            repository.updateUser(updated)
            onComplete()
        }
    }

    fun applyForJob(jobId: Int, onResult: (Boolean, String) -> Unit) {
        val driverSession = currentUser.value ?: return
        viewModelScope.launch {
            val driver = repository.getUserSync(driverSession.id) ?: return@launch
            val job = repository.getJobByIdSync(jobId) ?: return@launch
            // Verify driver has documents uploaded (Aadhaar, DL, and Photo / PermitPath)
            if (driver.aadhaarPath.isBlank() || driver.dlPath.isBlank() || driver.permitPath.isBlank()) {
                onResult(false, "Please complete your profile with Aadhaar, DL, and Photo before applying.")
                return@launch
            }
            val updated = job.withApplicant(driver.id)
            repository.updateJob(updated)
            onResult(true, "Applied successfully! Shipper can now review your documents.")
        }
    }

    suspend fun isShipperBlockedFromPosting(shipperId: Int): Boolean {
        val completedCount = repository.getCompletedLoadsCountForShipper(shipperId)
        if (completedCount < 2) return false
        val unpaid = repository.getUnpaidCommissionsForShipper(shipperId)
        return unpaid.isNotEmpty()
    }

    suspend fun isDriverBlockedFromApplying(driverId: Int): Boolean {
        val completedCount = repository.getCompletedLoadsCountForDriver(driverId)
        if (completedCount < 2) return false
        val unpaid = repository.getUnpaidCommissionsForDriver(driverId)
        return unpaid.isNotEmpty()
    }

    fun submitShipperCommissionPayment(shipperId: Int, utr: String, phone: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (utr.trim().isBlank() || phone.trim().isBlank()) {
                onResult(false, "Please enter both UTR/Transaction ID and mobile number.")
                return@launch
            }
            val unpaid = repository.getUnpaidCommissionsForShipper(shipperId)
            if (unpaid.isEmpty()) {
                onResult(true, "No pending commissions to pay!")
                return@launch
            }
            unpaid.forEach { comm ->
                val updated = comm.copy(isPaid = true, utrNumber = utr, payeePhone = phone)
                repository.updateCommission(updated)
            }
            onResult(true, "Commission payment proof submitted! Access fully restored.")
        }
    }

    fun submitDriverCommissionPayment(driverId: Int, utr: String, phone: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            if (utr.trim().isBlank() || phone.trim().isBlank()) {
                onResult(false, "Please enter both UTR/Transaction ID and mobile number.")
                return@launch
            }
            val unpaid = repository.getUnpaidCommissionsForDriver(driverId)
            if (unpaid.isEmpty()) {
                onResult(true, "No pending commissions to pay!")
                return@launch
            }
            unpaid.forEach { comm ->
                val updated = comm.copy(isPaid = true, utrNumber = utr, payeePhone = phone)
                repository.updateCommission(updated)
            }
            onResult(true, "Commission payment proof submitted! Application and details unlocked.")
        }
    }

    fun approveDriver(driverId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserSync(driverId)
            if (user != null) {
                val updated = user.copy(isApproved = true)
                repository.updateUser(updated)
                onResult(true, "Driver successfully approved!")
            } else {
                onResult(false, "Driver not found.")
            }
        }
    }

    fun rejectDriver(driverId: Int, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val user = repository.getUserSync(driverId)
            if (user != null) {
                val updated = user.copy(isApproved = false)
                repository.updateUser(updated)
                onResult(true, "Driver application rejected.")
            } else {
                onResult(false, "Driver not found.")
            }
        }
    }

    // Real-time progress tracker animation
    private fun startTrackingAnimation(loadId: Int) {
        activeTrackingJobs[loadId]?.cancel()
        val job = viewModelScope.launch {
            var progress = 0.0
            while (progress <= 1.0) {
                val currentMap = _loadTrackingProgress.value.toMutableMap()
                currentMap[loadId] = progress
                _loadTrackingProgress.value = currentMap
                progress += 0.05
                delay(2000) // update every 2 seconds
            }
            // Trip finished moving!
            val currentMap = _loadTrackingProgress.value.toMutableMap()
            currentMap[loadId] = 1.0
            _loadTrackingProgress.value = currentMap
        }
        activeTrackingJobs[loadId] = job
    }

    private fun stopTrackingAnimation(loadId: Int) {
        activeTrackingJobs[loadId]?.cancel()
        activeTrackingJobs.remove(loadId)
        val currentMap = _loadTrackingProgress.value.toMutableMap()
        currentMap.remove(loadId)
        _loadTrackingProgress.value = currentMap
    }

    // Active Chat Selection
    fun selectActiveChatLoadId(loadId: Int?) {
        _activeLoadIdForChat.value = loadId
    }

    // In-app Chat: Send message
    fun sendChatMessage(loadId: Int, text: String) {
        val user = currentUser.value ?: return
        if (text.isBlank()) return

        viewModelScope.launch {
            val msg = ChatMessage(
                loadId = loadId,
                senderId = user.id,
                senderName = user.name,
                text = text.trim()
            )
            repository.insertChatMessage(msg)
        }
    }

    fun getDriversByIds(ids: List<Int>): Flow<List<User>> = repository.getDriversByIds(ids)

    // Nearby Service categories
    val serviceCategories = listOf(
        ServiceCategory("garages", "Service Center", "build"),
        ServiceCategory("pumps", "Petrol Pumps", "local_gas_station"),
        ServiceCategory("restaurants", "Hotel Dhabas & Restaurants", "restaurant"),
        ServiceCategory("commercial_repair", "Commercial Vehicle Repair", "settings_suggest"),
        ServiceCategory("workshops", "Workshops", "precision_manufacturing"),
        ServiceCategory("hospitals", "Hospitals & Trauma", "local_hospital")
    )

    data class ServiceCategory(val id: String, val name: String, val iconName: String)
    data class NearbyService(
        val name: String,
        val distanceKm: Double,
        val phone: String,
        val description: String,
        val latOffset: Double,
        val lngOffset: Double
    )

    // Nearby services: REAL data via free OpenStreetMap Nominatim + Overpass APIs.
    // (No Google API key / billing needed - this replaces the old placeholder/demo data.)
    suspend fun getNearbyServicesReal(
        categoryId: String,
        city: String = "",
        pincode: String = "",
        state: String = "",
        area: String = ""
    ): List<NearbyService> {
        val displayCity = city.ifBlank { "Jaipur" }.trim()
        val displayState = state.ifBlank { "Rajasthan" }.trim()
        val displayArea = area.trim()

        val locationQuery = listOf(displayArea, displayCity, displayState)
            .filter { it.isNotBlank() }
            .joinToString(", ")

        val coords = com.example.data.NearbyPlacesApi.geocode(locationQuery) ?: return emptyList()

        val osmCategoryTag = when (categoryId) {
            "pumps" -> "fuel"
            "restaurants" -> "restaurant"
            "hospitals" -> "hospital"
            "garages", "commercial_repair", "workshops" -> "car_repair"
            else -> "car_repair"
        }

        val places = com.example.data.NearbyPlacesApi.fetchNearby(coords.first, coords.second, osmCategoryTag)

        return places.map { place ->
            NearbyService(
                name = place.name,
                distanceKm = Math.round(place.distanceKm * 10) / 10.0,
                phone = place.phone,
                description = place.address.ifBlank { "Near $displayArea, $displayCity, $displayState".trim(',', ' ') },
                latOffset = place.lat,
                lngOffset = place.lon
            )
        }.sortedBy { it.distanceKm }
    }
}

class MainViewModelFactory(
    private val application: Application,
    private val repository: AppRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
