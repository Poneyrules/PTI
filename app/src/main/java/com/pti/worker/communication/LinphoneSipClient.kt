package com.pti.worker.communication

import android.content.Context
import android.util.Log
import com.pti.worker.data.db.entities.SettingsEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.Factory

/** Keeps the SIP registration alive while PTI is armed and places voice alerts through liblinphone. */
class LinphoneSipClient(context: Context) {
    private val appContext = context.applicationContext
    private var core: Core? = null
    private var account: Account? = null
    private var configuredSettings: SipAccountSettings? = null

    suspend fun connect(settings: SettingsEntity): Result<Unit> = withContext(Dispatchers.Main) {
        runCatching {
            require(settings.sipUsername.isNotBlank()) { "Identifiant SIP manquant" }
            require(settings.sipDomain.isNotBlank()) { "Domaine SIP manquant" }
            require(settings.sipServer.isNotBlank()) { "Serveur SIP manquant" }
            val identity = "sip:${settings.sipUsername}@${settings.sipDomain}"
            val accountSettings = SipAccountSettings(
                settings.sipUsername, settings.sipPassword, settings.sipDomain, settings.sipServer
            )
            if (configuredSettings == accountSettings && core != null) return@runCatching

            disconnectInternal()
            val factory = Factory.instance()
            val sipCore = factory.createCore(null, null, appContext)
            val auth = factory.createAuthInfo(
                settings.sipUsername, null, settings.sipPassword, null, null, settings.sipDomain
            )
            sipCore.addAuthInfo(auth)
            val params = sipCore.createAccountParams()
            val identityAddress = factory.createAddress(identity)
                ?: error("Identité SIP invalide")
            val serverAddress = factory.createAddress(normalizeServer(settings.sipServer))
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
        }.onFailure { Log.e(TAG, "Configuration SIP impossible", it) }
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
                sipCore.stop()
            }
        }.onFailure { Log.w(TAG, "Arrêt SIP incomplet", it) }
        core = null
        account = null
        configuredSettings = null
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
