package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val role: String, // "DRIVER" or "SHIPPER"
    val name: String,
    val phone: String,
    // Driver specific fields
    val truckSize: String = "",
    val truckNumber: String = "",
    val rcPath: String = "",
    val dlPath: String = "",
    val aadhaarPath: String = "",
    val permitPath: String = "", // Also acts as Driver Photo path
    val isApproved: Boolean = false // Drivers must be verified/approved
) {
    fun isDriver() = role == "DRIVER"
    fun isShipper() = role == "SHIPPER"
}

@Entity(tableName = "loads")
data class Load(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val shipperId: Int,
    val shipperName: String,
    val shipperPhone: String,
    val pickupLocation: String,
    val dropLocation: String,
    val loadType: String,
    val weightTons: Double,
    val truckSize: String,
    val distanceKm: Double,
    val ratePerKm: Double,
    val ratePerTon: Double,
    val totalFare: Double,
    val status: String, // "POSTED", "ACCEPTED", "ONGOING", "COMPLETED"
    val interestedDriverIdsString: String = "", // Comma-separated list of driver IDs e.g. "2,4,7"
    val assignedDriverId: Int? = null,
    val assignedDriverName: String = "",
    val assignedDriverPhone: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getInterestedDriverIds(): List<Int> {
        if (interestedDriverIdsString.isBlank()) return emptyList()
        return interestedDriverIdsString.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun withInterestFromDriver(driverId: Int): Load {
        val currentIds = getInterestedDriverIds()
        if (currentIds.contains(driverId)) return this
        val newString = if (interestedDriverIdsString.isBlank()) "$driverId" else "$interestedDriverIdsString,$driverId"
        return this.copy(interestedDriverIdsString = newString)
    }
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val loadId: Int,
    val senderId: Int,
    val senderName: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "commissions")
data class CommissionPayment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val driverId: Int,
    val shipperId: Int,
    val loadId: Int,
    val amount: Double,
    val isPaid: Boolean = false,
    val upiIdUsed: String = "9660033436@pthdfc",
    val utrNumber: String = "",
    val payeePhone: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "job_profiles")
data class JobProfile(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val shipperId: Int,
    val shipperName: String,
    val shipperPhone: String,
    val workTitle: String,
    val salaryText: String,
    val location: String,
    val description: String,
    val applicantsString: String = "", // Comma-separated list of driver IDs who applied
    val createdAt: Long = System.currentTimeMillis()
) {
    fun getApplicantIds(): List<Int> {
        if (applicantsString.isBlank()) return emptyList()
        return applicantsString.split(",").mapNotNull { it.toIntOrNull() }
    }

    fun withApplicant(driverId: Int): JobProfile {
        val currentIds = getApplicantIds()
        if (currentIds.contains(driverId)) return this
        val newString = if (applicantsString.isBlank()) "$driverId" else "$applicantsString,$driverId"
        return this.copy(applicantsString = newString)
    }
}
