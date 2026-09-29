package com.pti.worker.data.repository

import com.pti.worker.data.db.EventDao
import com.pti.worker.data.db.entities.EventEntity
import kotlinx.coroutines.flow.Flow

class EventRepository(private val eventDao: EventDao) {

    fun getAllEvents(): Flow<List<EventEntity>> = eventDao.getAllEvents()

    fun getRecentEvents(limit: Int = 100): Flow<List<EventEntity>> =
        eventDao.getRecentEvents(limit)

    suspend fun logEvent(
        type: String,
        message: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        accuracy: Float? = null,
        altitude: Double? = null,
        extra: String? = null
    ): Long {
        val event = EventEntity(
            type = type,
            message = message,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            altitude = altitude,
            extra = extra
        )
        return eventDao.insert(event)
    }

    suspend fun clearAll() = eventDao.clearAll()
}
