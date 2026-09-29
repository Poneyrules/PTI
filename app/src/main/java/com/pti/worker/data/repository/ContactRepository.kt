package com.pti.worker.data.repository

import com.pti.worker.data.db.ContactDao
import com.pti.worker.data.db.entities.ContactEntity
import kotlinx.coroutines.flow.Flow

class ContactRepository(private val contactDao: ContactDao) {

    fun getAllContacts(): Flow<List<ContactEntity>> = contactDao.getAllContacts()

    suspend fun getEnabledContacts(): List<ContactEntity> = contactDao.getEnabledContacts()

    suspend fun addContact(contact: ContactEntity): Long = contactDao.insert(contact)

    suspend fun updateContact(contact: ContactEntity) = contactDao.update(contact)

    suspend fun deleteContact(contact: ContactEntity) = contactDao.delete(contact)

    suspend fun getById(id: Long): ContactEntity? = contactDao.getById(id)
}
