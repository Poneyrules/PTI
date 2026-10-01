# PTI Travailleur Isolé – Application Android

Application de **Protection du Travailleur Isolé (PTI)** pour smartphone Android.

## Fonctionnalités

- Activation / désactivation du mode PTI
- Bouton SOS manuel avec confirmation
- Mode hors couverture : choix du niveau (-1/-2) et d'une durée (0–60 min), puis SMS au contact d'urgence prioritaire
- Détection de chute (accéléromètre + gyroscope)
- Détection d'immobilité prolongée
- Détection de perte de verticalité
- Localisation GPS (Fused Location Provider)
- Envoi SMS réel vers contacts d'urgence
- Fonctionnement en arrière-plan (Foreground Service)
- Journal local des événements (Room)
- Paramètres configurables
- Restauration possible après redémarrage

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
  ├── AlertManager → SmsCommunicationManager
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
- Tous les seuils sont configurables
- Service START_STICKY + notification permanente
