package com.pti.worker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pti.worker.PtiApplication
import com.pti.worker.contact.ContactManager
import com.pti.worker.data.db.entities.ContactEntity
import com.pti.worker.data.repository.ContactRepository
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.communication.LinphoneSipClient
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ContactsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as PtiApplication

    private val contactManager = ContactManager(
        ContactRepository(app.database.contactDao())
    )

    val contacts: StateFlow<List<ContactEntity>> = contactManager.getAllContacts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<SettingsEntity> = app.settingsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    fun addContact(name: String, phone: String, priority: Int = 1) {
        viewModelScope.launch { contactManager.addContact(name, phone, priority) }
    }

    fun updateContact(contact: ContactEntity) {
        viewModelScope.launch { contactManager.updateContact(contact) }
    }

    fun deleteContact(contact: ContactEntity) {
        viewModelScope.launch { contactManager.deleteContact(contact) }
    }

    fun toggleEnabled(contact: ContactEntity) {
        if (LinphoneSipClient.isOwnAccountDestination(contact.phoneNumber, settings.value)) return
        viewModelScope.launch { contactManager.setEnabled(contact, !contact.isEnabled) }
    }
}
