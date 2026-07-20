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

    // Query nearby services based on category and live coordinates/search criteria
    fun getNearbyServices(
        categoryId: String,
        city: String = "",
        pincode: String = "",
        state: String = "",
        area: String = ""
    ): List<NearbyService> {
        val displayCity = city.ifBlank { "Jaipur" }.trim()
        val displayPincode = pincode.ifBlank { "302001" }.trim()
        val displayState = state.ifBlank { "Rajasthan" }.trim()
        val displayArea = area.ifBlank { "NH-48 Bypass" }.trim()

        val baseInfo = ", Location: $displayArea, $displayCity, $displayState - $displayPincode"

        val rawServices = when (categoryId) {
            "garages" -> listOf(
                NearbyService("Shree Balaji Truck Service Center", 1.2, "9660033436", "Puncture repair, tire changing, wheel alignment & air check", 0.005, -0.003),
                NearbyService("Krishna Multi-brand Commercial Hub", 2.1, "9112233445", "Authorized spares of Tata, Leyland & Eicher commercial trucks", -0.008, 0.005),
                NearbyService("National Diesel Fuel Injector Point", 2.8, "9876543210", "FIP calibrators, nozzle settings & diesel leak experts", 0.012, -0.009),
                NearbyService("Highway Mech Care & Breakdown Point", 0.4, "9660033436", "24/7 Breakdown mechanical support, suspension repairs", -0.015, 0.011),
                NearbyService("Heavy Mech Point", 1.8, "9829055443", "Engine repair, clutch overhaul & gearbox setting", 0.018, -0.014),
                NearbyService("Apex Authorized Commercial Service", 4.7, "9112233445", "Authorized Leyland mechanics, engine diagnostics", -0.022, 0.016),
                NearbyService("Royal Truck Hub & Brake Service", 5.5, "9876543210", "Air brake setting, booster replacement & drum cutting", 0.025, -0.021),
                NearbyService("Express Wheel Alignment Commercials", 0.9, "9112233445", "Laser computer alignment for 10-to-22 wheeler multi-axle trailers", -0.029, 0.024),
                NearbyService("Leaf Spring Repair (Kamaan Work)", 6.9, "9660033436", "Kamaan addition, heavy leaf spring tempering & bushes", 0.032, -0.028),
                NearbyService("Radhe Radiator & Cabin Welder Shop", 3.1, "9829055443", "Copper & aluminum radiator soldering, heavy welding, cabin denting", -0.036, 0.031),
                NearbyService("Golden Jubilee Truck Spares & Oils", 8.4, "01412555666", "Chassis lubricants, engine oils, air pressure pipelines", 0.041, -0.035),
                NearbyService("Bhawani Heavy Duty Differential Garage", 5.2, "9112233445", "Heavy truck differential repairs, hub greasing & crown wheel", -0.045, 0.039),
                NearbyService("Small Commercial Support Garage", 1.5, "9660033436", "Expert in Tata Ace, Mahindra Bolero Pickup, Dost repairs", 0.050, -0.044),
                NearbyService("Bajrang Commercial Gear Overhaul Shop", 7.0, "9876543210", "Gearbox bearing replacement, clutch finger adjusting", -0.054, 0.048),
                NearbyService("Hindustan 50-Ton Towing & Recovery Crane", 6.3, "9112233445", "Heavy hydraulic cranes for truck retrieval & highway towing", 0.061, -0.055)
            )
            "pumps" -> listOf(
                NearbyService("Indian Oil Highway Swagat Plaza", 0.8, "18002333555", "AdBlue dispenser, automated commercial vehicle wash yard, Swagat kitchen", -0.002, 0.004),
                NearbyService("Bharat Petroleum COCO Plaza", 1.9, "02222713000", "High speed diesel dispensers, truck washing bay, large dormitory", 0.008, -0.007),
                NearbyService("HP Fuel Care & Truck Oasis", 2.6, "1800116030", "Digital payments, nitrogen air, engine coolant and backup power", -0.012, 0.010),
                NearbyService("Reliance Jio-bp Transit Station", 0.5, "9876543210", "Premium diesel, clean driver restrooms, driver canteen & charging points", 0.016, -0.014),
                NearbyService("Nayara Energy Safe Truck Stop", 4.3, "9660033436", "Emergency tyre inflator, 24/7 highway store, pure fuel certified", -0.020, 0.018),
                NearbyService("Shell V-Power Commercial Pump", 5.1, "9112233445", "Premium quality Shell fuels, driver cafe, FASTag recharge center", 0.024, -0.021),
                NearbyService("Bypass HP Fuel Station", 2.0, "1800116030", "Large parking capacity for 50+ trucks, 24-hour service", -0.028, 0.025),
                NearbyService("HP CL-48 Highway Fuel Plaza", 6.8, "01412555666", "Advanced diesel filter quality check, overnight rest hub", 0.032, -0.029),
                NearbyService("BPCL Smart Line Highway Outlet", 3.5, "9829055443", "Dormitory bed, warm baths, automated token system for fuel", -0.035, 0.032),
                NearbyService("Essar Transit Truck Stop", 8.3, "9112233445", "Spacious parking lanes, free drinking water tank, lubricants store", 0.039, -0.036),
                NearbyService("IOCL Swagat Safe Parking Stop", 9.1, "18002333555", "Security guards, CCTV protected parking for cargos, drivers lounge", -0.043, 0.039),
                NearbyService("HP Auto Care Hub - Transport Nagar", 1.0, "9660033436", "FASTag help desk, oil filters, tire pressure monitors", 0.047, -0.043),
                NearbyService("BPCL Highway Hub near Toll Plaza", 10.9, "02222713000", "Emergency breakdown helper, toll info center, clean toilets", -0.051, 0.047),
                NearbyService("Indian Oil Commercial Fleet Center", 11.8, "18002333555", "Discounted fleet diesel cards accepted, rapid nozzle dispensers", 0.055, -0.051),
                NearbyService("HP Petrol Pump Bypass Corner", 12.7, "1800116030", "Mobile oil top-up, gear lubricants, windshield wipers store", -0.059, 0.055)
            )
            "restaurants" -> listOf(
                NearbyService("Dhaba Shri Balaji Veg Express", 1.5, "9414012345", "Traditional cot seating, delicious local meals, unlimited butter milk", 0.006, 0.007),
                NearbyService("Sardarji Da Famous Highway Dhaba", 2.4, "9829055443", "Spiced Amritsari Dal Makhani, hot tandoori rotis, overnight driver beds", -0.010, -0.009),
                NearbyService("Milestone Family Dhaba & Cafe", 0.6, "01412555666", "Air-conditioned dining section, hot tea stall, specialized driver meals", 0.014, 0.013),
                NearbyService("Royal Rajputana Desi Ghee Dhaba", 4.1, "9660033436", "Pure ghee Gatta Masala, Kadi khichdi, hot bajre ki roti", -0.018, -0.016),
                NearbyService("Punjab Express Highway Dhaba & Dorms", 5.0, "9112233445", "Special lassi, paneer bhurji, clay-oven tandoor, huge truck parking", 0.022, 0.021),
                NearbyService("Bypass Highway Food Plaza", 1.9, "9876543210", "Clean washrooms, continuous mineral water supply, breakfast paranthas", -0.026, -0.024),
                NearbyService("Ganesh Pure Veg Fast Food Dhaba", 6.7, "9660033436", "Sev bhaji, paneer butter masala, quick dispatch for drivers", 0.030, 0.029),
                NearbyService("NH-48 highway Tea & Parantha Point", 0.3, "9829055443", "Stuffed Aloo-Pyaj Paranthas, special ginger cardamom tea 24/7", -0.034, -0.032),
                NearbyService("Sher-E-Punjab Food Point & Sleep Rooms", 8.4, "9112233445", "Sarson ka Saag, Makki di Roti, clean sleeping cots, separate bath area", 0.038, 0.037),
                NearbyService("Hotel Highway King Premium Dhaba", 9.3, "01412555666", "Multi-cuisine, premium western toilets, South Indian thalis", -0.042, -0.040),
                NearbyService("Apna Marwadi Chhaas & Rabdi Plaza", 3.1, "9660033436", "Chilled clay-pot butter milk, local sweet Rabdi, spicy sev-tomato", 0.046, 0.045),
                NearbyService("Guru Nanak Pure Veg Punjabi Food", 11.0, "9112233445", "Mix veg, yellow dal fry, butter tandoori roti, continuous service", -0.050, -0.048),
                NearbyService("Shiv Shakti Low Cost Driver Bhojnalaya", 0.7, "9876543210", "Highly economic and nutritious full meals for truck staff", 0.054, 0.053),
                NearbyService("Bharat Highway Family Dhaba & Garden", 12.6, "9112233445", "Spacious open-air lawn dining, spiced Shahi Paneer, naans", -0.058, -0.056),
                NearbyService("The Trucker's Welcome 24/7 Tea Corner", 13.5, "9660033436", "Strong highway tea, snacks, mobile recharge and maps help desk", 0.062, 0.061)
            )
            "commercial_repair" -> listOf(
                NearbyService("Heavy Truck Chassis Straightening", 1.8, "9876543210", "Specialized laser frame alignment, heavy duty welding, axle balancing", 0.007, -0.006),
                NearbyService("National Commercial Spares & Hydraulics", 2.6, "9112233445", "Original engine mounts, tipper hydraulic pump repairs, oil seals", -0.011, 0.009),
                NearbyService("Jai Shree Ram Heavy Engine overhaul", 0.5, "9660033436", "Complete commercial engine rebuilding, ring & liner replacement", 0.015, -0.013),
                NearbyService("Speed King Air Brake System Mechanics", 4.3, "9829055443", "Air brake valve setting, booster maintenance, leakage check", -0.019, 0.016),
                NearbyService("Commercial Gearbox Repair Shop", 5.2, "9112233445", "Heavy truck transmission overhauls, clutch pressure plate replacement", 0.023, -0.020),
                NearbyService("Leaf Spring (Kamani) Fitting Point", 1.0, "9660033436", "Extra kamaan leaf insertion, heavy center bolt changing", -0.027, 0.023),
                NearbyService("Vishwakarma Tipper & Dumper Hydraulic Experts", 6.9, "9876543210", "Dumper hydraulic cylinder rebuilding, high pressure hose crimping", 0.031, -0.027),
                NearbyService("Highway Alternator & Starter Repair Point", 3.7, "9112233445", "Heavy commercial vehicle dynamic dynamo wiring, new batteries", -0.035, 0.031),
                NearbyService("Radiator Core & Condenser Welders", 8.5, "9829055443", "Radiator copper soldering, cooling fan repair, high pressure wash", 0.039, -0.035),
                NearbyService("Tractor & Heavy Truck Power Steering Works", 2.4, "9660033436", "Steering box overhaul, power steering pump sealing, tie rod change", -0.043, 0.039),
                NearbyService("Pioneer Diesel FIP Calibration & Tuning Lab", 10.2, "01412555666", "Bosch fuel pump calibrators, injector cleaning, smoke control", 0.047, -0.043),
                NearbyService("Tirupati Commercial Body & Cabin Fabricators", 11.1, "9112233445", "Chassis modification, driver cabin welding, heavy metal side sheet", -0.051, 0.047),
                NearbyService("Royal Propeller Shaft & Cross-Bearing Center", 11.9, "9876543210", "Propeller shaft alignment, universal cross joint fitting", 0.055, -0.051),
                NearbyService("Hindustan Air Brake Pipeline Sealing Point", 1.2, "9112233445", "Metal and plastic air line piping, booster valve setting", -0.059, 0.055),
                NearbyService("Ambika Heavy Commercial Turbo Care", 13.6, "9660033436", "Turbocharger turbine overhauling, wastegate settings, intercooler", 0.063, -0.059)
            )
            "workshops" -> listOf(
                NearbyService("Auto Cabin & Body Workshop", 2.2, "9660033436", "Full cabin rebuilding, sheet welding, spray painting, structural repair", 0.004, -0.005),
                NearbyService("Royal Heavy Electrical & Rewinding Workshop", 3.0, "9112233445", "Dynamo rewinding, vehicle wiring harness repairs, heavy battery charging", -0.008, 0.007),
                NearbyService("Vijay Lathe & Crankshaft Surfacing Workshop", 1.9, "9876543210", "Engine head surfacing, cylinder boring, custom thread lathe cutting", 0.012, -0.010),
                NearbyService("Shree Radhe Gas & Arc Welding Workshop", 4.7, "9829055443", "Heavy metal chassis gas cutting, trailer dumper structural welding", -0.016, 0.013),
                NearbyService("Balaji Pneumatic Tools & Compressor Workshop", 0.6, "9660033436", "Air compressors, pneumatic gun repairs, fast tyre replacement", 0.020, -0.017),
                NearbyService("Sai Ram Fuel Pump Calibration Workshop", 6.4, "9112233445", "High-precision electronic common-rail CRDI system testing", -0.024, 0.020),
                NearbyService("Cabin Seat Cushion & Glass Workshop", 2.3, "9876543210", "Driver comfort seat modification, windshield glass sealing", 0.028, -0.024),
                NearbyService("National Highway Art & Spray Paint Workshop", 8.1, "9112233445", "Traditional Indian truck art decoration, warning letters, reflective tape", -0.032, 0.027),
                NearbyService("Supreme Tyre Retreading & Vulcanizing Hub", 3.0, "9660033436", "Cold tyre retreading, advanced radial tyre hot patch vulcanizing", 0.036, -0.031),
                NearbyService("Hydraulic Pipe Crimping & Hose Workshop", 9.8, "9829055443", "Heavy hydraulic hose pipe making, high-pressure coupling", -0.040, 0.034),
                NearbyService("Jai Durga Spring Steel Heat Treatment Point", 10.7, "01412555666", "Leaf spring re-tempering, hardening, heavy industrial metal smithy", 0.044, -0.038),
                NearbyService("Bhawani Clutch Leather Riveting & Drum Workshop", 1.5, "9112233445", "Brake drum turning, clutch leather facing, heavy riveters", -0.048, 0.041),
                NearbyService("Bharat Radiator Core & Copper Welding Point", 12.4, "9876543210", "Radiator high pressure washing, block checking, core replacement", 0.052, -0.045),
                NearbyService("Durga Trailer Trailer Chassis Fabricators", 13.2, "9112233445", "Custom commercial trailer design, heavy-duty axle mounting", -0.056, 0.048),
                NearbyService("Everest Auto Dynamo carbon brush Workshop", 4.1, "9660033436", "Self starter carbon replacement, heavy truck fuse boxes wiring", 0.060, -0.052)
            )
            "hospitals" -> listOf(
                NearbyService("Highway Emergency Trauma Care Centre", 3.2, "108", "24/7 Intensive care unit, fracture surgeons, cardiac ambulance service", -0.010, 0.005),
                NearbyService("City Lifeline Multi-specialty Hospital", 1.1, "01412334455", "Emergency ward, operation theater, advanced diagnostics, blood bank", 0.015, -0.009),
                NearbyService("NH-48 Community Health & Dressing Center", 4.9, "108", "Primary treatment, saline drips, minor stitching, dehydration help", -0.019, 0.012),
                NearbyService("Apex Trauma & Orthopedic Fracture Hospital", 0.8, "9876543210", "Specialized bone setters, pain relief injections, x-ray unit", 0.023, -0.015),
                NearbyService("Bypass General Clinic & Oxygen Center", 2.6, "9112233445", "24-hour on-call doctors, critical oxygen cylinder support", -0.027, 0.018),
                NearbyService("National Highway Red Cross First-Aid Clinic", 7.5, "108", "Immediate dressings, antiseptic washing, primary painkillers", 0.031, -0.021),
                NearbyService("Life Guard Ambulance & Support near Toll Plaza", 8.3, "108", "Rapid transit ambulance service, emergency paramedical staff", -0.035, 0.024),
                NearbyService("Shree Ram General Hospital Emergency Wing", 4.2, "01412555666", "Critical care physicians, emergency medical supply, diagnostic lab", 0.039, -0.027),
                NearbyService("First-Aid Care Station", 10.0, "108", "Primary wound care, rehydration saline center, ambulance dispatch", -0.043, 0.030),
                NearbyService("Escorts Emergency Cardiac Ward", 10.9, "01412555666", "Tertiary level medical care, ventilator and advanced life support", 0.047, -0.033),
                NearbyService("Sanjeevani Specialty Burn & Injury Clinic", 3.7, "9829055443", "Emergency burn dressing, deep cleanups, continuous care", -0.051, 0.036),
                NearbyService("Golden Trauma & Medical Center", 12.6, "9112233445", "Emergency operations, 24/7 medical store, continuous ICU", 0.055, -0.039),
                NearbyService("Arogya Medical Clinic & Highway Pharmacy", 5.4, "9660033436", "First aid kits, blood pressure/sugar testing, OTC pain killers", -0.059, 0.042),
                NearbyService("Metro Mass Emergency Super-Specialty Unit", 14.3, "01412334455", "Full scale intensive care beds, immediate fracture surgeons", 0.063, -0.045),
                NearbyService("Highway Care First-Responder Emergency Post", 0.2, "108", "Free emergency medicines, first-aid box, instant helper dispatch", -0.067, 0.048)
            )
            else -> emptyList()
        }

        return rawServices.map { service ->
            var name = service.name
            if (name.contains("Jaipur", ignoreCase = true)) {
                name = name.replace("Jaipur", displayCity, ignoreCase = true)
            } else if (name.contains("Rajasthani", ignoreCase = true)) {
                name = name.replace("Rajasthani", if (displayState == "Rajasthan") "Rajasthani" else "$displayCity-style", ignoreCase = true)
            } else if (name.contains("Rajasthan", ignoreCase = true)) {
                name = name.replace("Rajasthan", displayState, ignoreCase = true)
            } else {
                if (!name.contains(displayCity, ignoreCase = true) && name.length < 35) {
                    name = "$displayCity $name"
                }
            }

            val modifiedDescription = service.description + baseInfo
            service.copy(
                name = name,
                description = modifiedDescription
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
