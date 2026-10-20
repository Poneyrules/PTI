package com.pti.worker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pti.worker.PtiApplication
import com.pti.worker.data.db.entities.SettingsEntity
import com.pti.worker.settings.SettingsManager
import com.pti.worker.contact.ContactManager
import com.pti.worker.contact.ImportedContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsManager = SettingsManager(
        (application as PtiApplication).database.settingsDao()
    )
    private val contactManager = ContactManager((application as PtiApplication).contactRepository)
    val sipConnectionState = (application as PtiApplication).sipClient.connectionState
    private val _sipImportStatus = MutableStateFlow<String?>(null)
    val sipImportStatus = _sipImportStatus.asStateFlow()

    val settings: StateFlow<SettingsEntity> = settingsManager.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsEntity())

    fun updateSettings(newSettings: SettingsEntity) {
        viewModelScope.launch { settingsManager.updateSettings(newSettings) }
    }

    fun setFallEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setFallDetectionEnabled(enabled) }
    }

    fun setImmobilityEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setImmobilityEnabled(enabled) }
    }

    fun setOrientationEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsManager.setOrientationEnabled(enabled) }
    }

    fun importSipContacts(endpoint: String) {
        viewModelScope.launch {
            _sipImportStatus.value = "Téléchargement en cours…"
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val url = URL(endpoint.trim())
                    require(url.protocol == "https" || url.protocol == "http") { "L'adresse doit commencer par https:// ou http://" }
                    val connection = (url.openConnection() as HttpURLConnection).apply {
                        connectTimeout = 10_000
                        readTimeout = 15_000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/json")
                    }
                    try {
                        check(connection.responseCode in 200..299) { "Erreur HTTP ${connection.responseCode}" }
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        require(body.length <= 2_000_000) { "Réponse JSON trop volumineuse" }
                        parseContacts(body)
                    } finally { connection.disconnect() }
                }
            }
            result.onSuccess { contacts ->
                val importResult = contactManager.addContactsIfMissing(contacts)
                _sipImportStatus.value = if (importResult.added == 0) {
                    "Tous les contacts sont déjà importés ; aucun doublon ajouté."
                } else {
                    "${importResult.added} contact(s) ajouté(s), ${importResult.skipped} déjà présent(s) ignoré(s)."
                }
            }.onFailure { _sipImportStatus.value = "Import impossible : ${it.message ?: "réponse invalide"}" }
        }
    }

    private fun parseContacts(body: String): List<ImportedContact> {
        val json = body.trim()
        val root = if (json.startsWith("[")) {
            JSONArray(json)
        } else {
            val objectRoot = JSONObject(json)
            val array = listOf("contacts", "data", "phonebook", "results", "entries")
                .firstNotNullOfOrNull { key ->
                    objectRoot.optJSONArray(key)
                        ?: objectRoot.optJSONObject(key)?.let { nested ->
                            JSONArray().also { values ->
                                nested.keys().forEach { childKey -> values.put(nested.opt(childKey)) }
                            }
                        }
                }
            array ?: JSONArray().also { values ->
                objectRoot.keys().forEach { key ->
                    objectRoot.optJSONObject(key)?.let(values::put)
                }
            }
        }
        return buildList {
            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val firstName = listOf("firstName", "prenom")
                    .firstNotNullOfOrNull { item.optString(it).trim().takeIf { value -> value.isNotBlank() && value != "null" } }
                    .orEmpty()
                val lastName = listOf("lastName", "nom")
                    .firstNotNullOfOrNull { item.optString(it).trim().takeIf { value -> value.isNotBlank() && value != "null" } }
                    .orEmpty()
                val name = listOf("name", "displayName", "fullName", "label")
                    .firstNotNullOfOrNull { item.optString(it).trim().takeIf { value -> value.isNotBlank() && value != "null" } }
                    .orEmpty().ifBlank { listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ") }
                val address = listOf("sipUri", "sip", "uri", "phoneNumber", "phone", "mobile", "number", "telephone", "tel", "extension", "username", "numero", "poste")
                    .firstNotNullOfOrNull { key -> item.optString(key).trim().takeIf { it.isNotBlank() && it != "null" } }
                if (name.isNotBlank() && address != null) {
                    add(ImportedContact(name, address, item.optInt("priority", 1).coerceAtLeast(1)))
                }
            }
        }.also { require(it.isNotEmpty()) { "Aucun contact reconnu. Chaque contact doit contenir un nom (name/nom) et une adresse (sipUri/uri/phone/number/extension)." } }
    }
}
