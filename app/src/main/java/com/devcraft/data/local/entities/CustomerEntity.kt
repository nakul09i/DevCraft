package com.devcraft.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "customers")
data class CustomerEntity(
    @PrimaryKey val customerId: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String? = null,
    val address: String? = null,
    val notes: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Cached geocode of [address]. Nullable; absence is a normal state.
    val latitude: Double? = null,
    val longitude: Double? = null,
    val formattedAddress: String? = null,
    val placeId: String? = null,
    val locationSource: String? = null,
    val locationUpdatedAt: Long? = null
)
