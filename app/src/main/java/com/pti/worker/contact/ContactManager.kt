package com.pti.worker.contact

import com.pti.worker.communication.LinphoneSipClient
import com.pti.worker.data.db.entities.ContactEntity
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.data.repository.ContactRepository
import kotlinx.coroutines.flow.Flow

class ContactManager(private val contactRepository: ContactRepository) {

    fun getAllContacts(): Flow<List<ContactEntity>> = contactRepository.getAllContacts()

    suspend fun getEnabledContacts(): List<ContactEntity> = contactRepository.getEnabledContacts()

    /** Contact d'urgence actif de priorité 1, en excluant le compte SIP local. */
    suspend fun getPriority1Contact(settings: SettingsEntity): ContactEntity? =
        getEnabledContacts()
            .asSequence()
            .filter { it.priority == 1 }
            .filterNot {
                settings.sipEnabled && LinphoneSipClient.isOwnAccountDestination(it.phoneNumber, settings)
            }
            .minByOrNull { it.id }

    suspend fun addContact(name: String, phoneNumber: String, priority: Int = 1): Long {
        return contactRepository.addContact(
            ContactEntity(name = name.trim(), phoneNumber = phoneNumber.trim(), priority = priority, isEnabled = true)
        )
    }

    /** Adds only previously unseen addresses, including duplicates within the downloaded list. */
    suspend fun addContactsIfMissing(contacts: List<ImportedContact>): ContactImportResult {
        val knownAddresses = contactRepository.getAllContactsSnapshot()
            .map { normalizeContactAddress(it.phoneNumber) }
            .toMutableSet()
        var added = 0
        var skipped = 0
        contacts.forEach { contact ->
            if (!knownAddresses.add(normalizeContactAddress(contact.address))) {
                skipped++
            } else {
                addContact(contact.name, contact.address, contact.priority)
                added++
            }
        }
        return ContactImportResult(added, skipped)
    }

    suspend fun updateContact(contact: ContactEntity) = contactRepository.updateContact(contact)

    suspend fun deleteContact(contact: ContactEntity) = contactRepository.deleteContact(contact)

    suspend fun setEnabled(contact: ContactEntity, enabled: Boolean) {
        contactRepository.updateContact(contact.copy(isEnabled = enabled))
    }

    private fun normalizeContactAddress(address: String): String {
        var normalized = address.trim().filterNot(Char::isWhitespace).lowercase()
        listOf("sips:", "sip:", "tel:").firstOrNull { prefix -> normalized.startsWith(prefix) }?.let { prefix ->
            normalized = normalized.removePrefix(prefix)
        }
        return normalized
    }
}

data class ImportedContact(val name: String, val address: String, val priority: Int)

data class ContactImportResult(val added: Int, val skipped: Int)
