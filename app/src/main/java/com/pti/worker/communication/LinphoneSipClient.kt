package com.pti.worker.communication

import android.content.Context
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState

/** Keeps the SIP registration alive while PTI is armed and places voice alerts through liblinphone. */
class LinphoneSipClient(context: Context) {
    private val appContext = context.applicationContext
    private var core: Core? = null
    private var account: Account? = null
    private var configuredSettings: SipAccountSettings? = null
    private val _connectionState = MutableStateFlow(SipConnectionState())
    val connectionState = _connectionState.asStateFlow()
    private var coreListener: CoreListenerStub? = null

    fun setDisabled() {
        _connectionState.value = SipConnectionState(SipConnectionStatus.DISABLED, "SIP désactivé")
    }

    suspend fun connect(settings: SettingsEntity): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            require(settings.sipUsername.isNotBlank()) { "Identifiant SIP manquant" }
            require(settings.sipDomain.isNotBlank()) { "Domaine SIP manquant" }
            val server = settings.sipServer.ifBlank { settings.sipDomain }
            val identity = "sip:${settings.sipUsername}@${settings.sipDomain}"
            val accountSettings = SipAccountSettings(
                settings.sipUsername, settings.sipPassword, settings.sipDomain, server
            )
            val currentState = _connectionState.value.status
            if (configuredSettings == accountSettings && core != null &&
                currentState != SipConnectionStatus.FAILED && currentState != SipConnectionStatus.DISCONNECTED
            ) return@runCatching

            disconnectInternal()
            _connectionState.value = SipConnectionState(SipConnectionStatus.CONNECTING, "Connexion au serveur SIP…")
            val factory = Factory.instance()
            val sipCore = factory.createCore(null, null, appContext)
            val listener = object : CoreListenerStub() {
                override fun onAccountRegistrationStateChanged(
                    core: Core,
                    account: Account,
                    state: RegistrationState,
                    message: String
                ) {
                    _connectionState.value = when (state) {
                        RegistrationState.Ok -> SipConnectionState(SipConnectionStatus.CONNECTED, "Connecté au serveur SIP")
                        RegistrationState.Progress, RegistrationState.Refreshing ->
                            SipConnectionState(SipConnectionStatus.CONNECTING, "Enregistrement SIP en cours…")
                        RegistrationState.Failed -> SipConnectionState(
                            SipConnectionStatus.FAILED,
                            message.ifBlank { "Échec de l'enregistrement SIP" }
                        )
                        RegistrationState.Cleared -> SipConnectionState(SipConnectionStatus.DISCONNECTED, "Compte SIP déconnecté")
                        RegistrationState.None -> SipConnectionState(SipConnectionStatus.CONNECTING, "Initialisation du compte SIP…")
                    }
                }
            }
            sipCore.addListener(listener)
            coreListener = listener
            val auth = factory.createAuthInfo(
                settings.sipUsername, null, settings.sipPassword, null, null, settings.sipDomain
            )
            sipCore.addAuthInfo(auth)
            val params = sipCore.createAccountParams()
            val identityAddress = factory.createAddress(identity)
                ?: error("Identité SIP invalide")
            val serverAddress = factory.createAddress(normalizeServer(server))
                ?: error("Adresse du serveur SIP invalide")
            check(params.setIdentityAddress(identityAddress) == 0) { "Identité SIP invalide" }
            check(params.setServerAddress(serverAddress) == 0) { "Serveur SIP invalide" }
            params.setRegisterEnabled(true)
            val sipAccount = sipCore.createAccount(params)
            check(sipCore.addAccount(sipAccount) == 0) { "Impossible d'ajouter le compte SIP" }
            sipCore.defaultAccount = sipAccount
            sipCore.start()
            core = sipCore
            account = sipAccount
            configuredSettings = accountSettings
        }.onFailure {
            _connectionState.value = SipConnectionState(
                SipConnectionStatus.FAILED,
                it.message ?: "Configuration SIP impossible"
            )
            Log.e(TAG, "Configuration SIP impossible", it)
        }
    }

    suspend fun call(destination: String, settings: SettingsEntity): Result<Unit> = withContext(Dispatchers.Main) {
        connect(settings).mapCatching {
            val sipCore = core ?: error("Le moteur SIP n'est pas démarré")
            val address = if (destination.trim().startsWith("sip:", true) || destination.trim().startsWith("sips:", true)) {
                destination.trim()
            } else {
                val user = destination.trim().removePrefix("tel:").trim()
                "sip:$user@${settings.sipDomain}"
            }
            check(sipCore.invite(address) != null) { "Échec de lancement de l'appel SIP vers $address" }
        }
    }

    suspend fun disconnect() = withContext(Dispatchers.Main) { disconnectInternal() }

    private fun disconnectInternal() {
        runCatching {
            core?.let { sipCore ->
                account?.let(sipCore::removeAccount)
                coreListener?.let(sipCore::removeListener)
                sipCore.stop()
            }
        }.onFailure { Log.w(TAG, "Arrêt SIP incomplet", it) }
        core = null
        account = null
        configuredSettings = null
        coreListener = null
        setDisabled()
    }

    private fun normalizeServer(server: String): String {
        val value = server.trim()
        return if (value.startsWith("sip:", true) || value.startsWith("sips:", true)) value else "sip:$value"
    }

    private data class SipAccountSettings(
        val username: String,
        val password: String,
        val domain: String,
        val server: String
    )

    companion object { private const val TAG = "LinphoneSipClient" }
}

enum class SipConnectionStatus { DISABLED, DISCONNECTED, CONNECTING, CONNECTED, FAILED }

data class SipConnectionState(
    val status: SipConnectionStatus = SipConnectionStatus.DISABLED,
    val message: String = "SIP désactivé"
)
