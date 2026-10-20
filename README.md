# PTI Travailleur Isolé – Application Android

Application de **Protection du Travailleur Isolé (PTI)** pour smartphone Android.

## Fonctionnalités

- Activation / désactivation du mode PTI
- Bouton SOS manuel avec confirmation
- Signalement de couverture : choix du niveau (-1/-2) et d'une durée (0–60 min), transmis par SMS ou SIP MESSAGE selon le mode choisi
- Détection de chute (accéléromètre + gyroscope)
- Détection d'immobilité prolongée
- Détection de perte de verticalité
- Localisation GPS (Fused Location Provider)
- Envoi d'alertes textuelles aux contacts d'urgence par SMS ou SIP MESSAGE selon le mode choisi
- Fonctionnement en arrière-plan (Foreground Service)
- Journal local des événements (Room)
- Paramètres configurables
- Restauration possible après redémarrage
- Appels vocaux d'alerte via Linphone/SIP lorsque l'option SIP est activée
- Import de contacts depuis une URL JSON
- Indicateur d'état de l'enregistrement du compte SIP

## Prérequis

- Android Studio Hedgehog ou plus récent
- JDK 17
- Android SDK 34
- Min SDK 26 (Android 8.0)

## Compilation

```bash
./gradlew assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`

```bash
./gradlew assembleRelease
./gradlew test
```

## Architecture

```
UI (Compose)
  ↓
ViewModels
  ↓
PtiManager + PtiStateMachine
  ├── FallDetectionManager
  ├── ImmobilityDetectionManager
  ├── OrientationManager
  ├── PtiLocationManager
  ├── AlertManager → SmsCommunicationManager / LinphoneSipClient
  ├── ContactManager
  ├── SettingsManager
  └── EventRepository → Room
```

## Permissions

- Localisation précise + arrière-plan
- SMS
- Notifications
- Activité physique
- Vibration
- Boot completed

## Notes

- Fonctionne sans serveur en version 1
- SMS via SmsManager natif
- SIP nécessite une connexion IP (Wi-Fi ou données mobiles), un compte SIP et un réseau joignable ; cela ne fonctionne pas hors de toute connectivité
- Le mode SIP envoie les alertes textuelles et le signalement de couverture en SIP MESSAGE ; les comptes destinataires et le serveur doivent accepter cette fonctionnalité. Les appels SIP d'alerte utilisent le haut-parleur.
- Le JSON distant peut être une liste ou un objet avec `contacts`/`data`; chaque entrée utilise `name` et l'un de `sipUri`, `sip`, `uri`, `phoneNumber`, `phone` ou `mobile`. Les entrées importées sont ajoutées aux contacts existants.
- Le trafic HTTP en clair est autorisé pour prendre en charge les répertoires locaux ; préférer HTTPS lorsque le serveur le permet.
- La bibliothèque liblinphone est distribuée sous GPLv3 ; vérifier les obligations de licence avant toute distribution de l'application.
- Tous les seuils sont configurables
- Service START_STICKY + notification permanente
