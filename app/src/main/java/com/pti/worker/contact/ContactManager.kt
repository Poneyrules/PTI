package com.pti.worker.contact

import com.pti.worker.data.db.entities.ContactEntity
import com.pti.worker.data.repository.ContactRepository
import kotlinx.coroutines.flow.Flow

class ContactManager(private val contactRepository: ContactRepository) {

    fun getAllContacts(): Flow<List<ContactEntity>> = contactRepository.getAllContacts()

    suspend fun getEnabledContacts(): List<ContactEntity> = contactRepository.getEnabledContacts()

    /** Contact d'urgence actif de priorité 1. */
    suspend fun getPriority1Contact(): ContactEntity? =
        getEnabledContacts().filter { it.priority == 1 }.minByOrNull { it.id }

    suspend fun addContact(name: String, phoneNumber: String, priority: Int = 1): Long {
        return contactRepository.addContact(
            ContactEntity(name = name.trim(), phoneNumber = phoneNumber.trim(), priority = priority, isEnabled = true)
        )
    }

    suspend fun updateContact(contact: ContactEntity) = contactRepository.updateContact(contact)

    suspend fun deleteContact(contact: ContactEntity) = contactRepository.deleteContact(contact)

    suspend fun setEnabled(contact: ContactEntity, enabled: Boolean) {
        contactRepository.updateContact(contact.copy(isEnabled = enabled))
    }
}
