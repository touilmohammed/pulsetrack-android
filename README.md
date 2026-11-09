# PulseTrack Android

Portage natif (Kotlin + Jetpack Compose) de la PWA [PulseTrack](https://github.com/touilmohammed/pulsetrack), un journal sportif hors ligne d'abord. TP du cours Android, M2 Informatique, Università di Corsica (Jour 6, 9 novembre 2026).

Même API, même serveur, même format de données, même URL que la PWA : <https://pulsetrackisco.netlify.app>. Comme pour la PWA, chaque appareil a son propre journal sur le serveur, rangé par identifiant d'appareil (en-tête `X-Device-Id`, sans compte utilisateur) : l'app ne voit pas les séances d'un navigateur, et inversement. Un lien du site s'ouvre dans l'app.

| | |
|---|---|
| Paquet | `corsica.universita.pulsetrack` |
| Version | 1.0 (versionCode 10) |
| Android | 8.0 (API 26) à 17 (API 37) |
| Fonctionnalité native | suivi de course GPS, écran éteint, en foreground service |

---

## 1. Construire et lancer

### Prérequis

- Android Studio (récent), qui fournit le JDK (`jbr`) et le SDK Android 37.
- Un émulateur **avec Google Play** (pour FCM et la position), ou un téléphone en débogage USB.

### Version debug

Ouvrir le dossier dans Android Studio, puis **Run ▶**. En ligne de commande :

```bash
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # sur l'appareil connecté
```

### Firebase (rappels push et analytics)

Le fichier `app/google-services.json` du projet Firebase est **hors du dépôt** (dépôt public). Sans lui, l'app se compile et fonctionne. Seules deux choses sont désactivées, et l'app le dit :

- les rappels : les Réglages indiquent « indisponibles » ;
- l'analytics : la question du consentement n'est pas posée.

Pour les activer, déposer le `google-services.json` du projet dans `app/`.

### Tester contre une autre version du serveur

L'URL de l'API est fixée à la compilation. Pour viser une branche Netlify avant sa mise en production :

```bash
./gradlew assembleDebug -Ppulsetrack.apiBaseUrl=https://<branche>--pulsetrackisco.netlify.app/
```

### Version release (signée, R8)

La clé de signature n'est **jamais** dans le dépôt : `*.jks` et `keystore.properties` sont dans `.gitignore`. Créer `keystore.properties` à la racine :

```properties
storeFile=C:/chemin/vers/pulsetrack-release.jks
storePassword=…
keyAlias=pulsetrack
keyPassword=…
```

```bash
./gradlew assembleRelease      # app/build/outputs/apk/release/app-release.apk (≈ 3 Mo, contre 17 Mo en debug)
apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
adb uninstall corsica.universita.pulsetrack   # la debug est signée par une autre clé
adb install app/build/outputs/apk/release/app-release.apk
```

Sans `keystore.properties`, le build release passe quand même, mais l'APK n'est pas signé.

**La clé se garde** (sauvegardée hors du PC, avec son mot de passe) : une app publiée ne peut être mise à jour qu'avec la même clé.

### Tests

```bash
./gradlew testDebugUnitTest            # 24 tests JVM
./gradlew connectedDebugAndroidTest    # 3 tests de migration Room, sur l'appareil
```

| Suite | Ce qu'elle vérifie |
|---|---|
| `SessionValidationTest` (7) | les règles du formulaire, identiques à la PWA (durée, distance, virgule décimale, note ≤ 500) |
| `SessionMappingTest` (4) | entité Room ↔ modèle, position et photo comprises |
| `ApiMappingTest` (4) | modèle ↔ JSON de l'API (dates ISO, version, position, jamais la photo) |
| `RunTrackerTest` (4) | le calcul de la course : filtres de précision et de vitesse, durée arrondie |
| `AppLinksTest` (5) | les liens acceptés, et tous ceux refusés (autre hôte, `http`, identifiant suspect) |
| `MigrationTest` (3) | les migrations 1→2, 2→3 et 1→3 sur de vraies bases, sans perte de données |

---

## 2. De la PWA à l'app

Chaque brique de la PWA a son équivalent Android. Ce qui ne change pas : l'API, les données du serveur, l'URL. Ce qui change : l'arbitre, qui n'est plus le navigateur mais l'OS.

| Dans la PWA | Dans l'app | Où |
|---|---|---|
| Écrans HTML + JS | Compose + ViewModel | `ui/` |
| Service Worker, stratégie de cache | Repository, source de vérité unique | `data/SessionsRepository.kt` |
| IndexedDB | Room (base v3, migrations testées) | `data/local/` |
| localStorage | DataStore Preferences | `data/local/Settings.kt` |
| Background Sync + outbox | WorkManager + table `outbox` | `work/SyncWorker.kt` |
| Push API + Service Worker | FCM + `FirebaseMessagingService` | `notif/` |
| Permissions du navigateur | Permissions runtime (`PermissionFlow`) | `ui/permissions/` |
| `manifest.json` | `AndroidManifest.xml` | `app/src/main/` |
| L'URL | App Link vérifié | `links/AppLinks.kt` + `assetlinks.json` côté PWA |

Côté serveur, le dépôt de la PWA a reçu deux ajouts pour l'app :

- la route `POST/DELETE /api/push/fcm` : les rappels sont envoyés par FCM HTTP v1, avec un JWT RS256 signé par le compte de service ;
- le fichier `/.well-known/assetlinks.json`.

---

## 3. Architecture

```
┌──────────── UI (Compose) ─────────────┐   écrans sans état métier, état hoisté,
│ Journal · Détail · Formulaire ·       │   rememberSaveable, Navigation 3
│ Suivi GPS · Réglages                  │
└──────────────┬────────────────────────┘
               │ StateFlow (collectAsStateWithLifecycle)
┌──────────────▼────────────────────────┐   survit à la rotation ; brouillon dans
│ ViewModels                            │   SavedStateHandle (survit à la mort du process)
└──────────────┬────────────────────────┘
               │ suspend / Flow
┌──────────────▼────────────────────────┐   source de vérité unique : Room.
│ SessionsRepository · RemindersRepo    │   L'écran ne lit jamais le réseau.
└───────┬──────────────┬────────────────┘
        │              │
┌───────▼──────┐ ┌─────▼───────────────┐  ┌──────────────────────────────┐
│ Room         │ │ Retrofit + OkHttp   │  │ Services de l'OS             │
│ sessions     │ │ /api/sessions       │  │ WorkManager (sync, push)     │
│ outbox       │ │ /api/push/fcm       │  │ FCM (rappels, app tuée)      │
│ DataStore    │ │                     │  │ Fused Location (GPS)         │
└──────────────┘ └─────────────────────┘  │ Foreground service (course)  │
                                          └──────────────────────────────┘
```

**D'où viennent les données :**
- L'écran observe Room (`Flow`). Il affiche donc toujours quelque chose, en mode avion compris.
- Une écriture va d'abord dans Room et dans l'outbox, **dans la même transaction**. WorkManager l'envoie quand le réseau revient.
- Au lancement, `refresh()` récupère les séances du serveur. Il ne remplace jamais une séance modifiée localement et pas encore envoyée.

```
app/src/main/java/corsica/universita/pulsetrack/
├── PulseTrackApp.kt        Application : conteneur de dépendances (AppContainer), canaux, sync au lancement
├── MainActivity.kt         seule Activity : splash, intents reçus (rappel, suivi, App Link, partage)
├── data/                   modèle, validation, repositories, position, photos, réseau
│   ├── local/              Room (entités, DAO, base, migrations) et DataStore
│   └── remote/             Retrofit : API des séances et des rappels
├── work/                   SyncWorker (outbox), PushRegistrationWorker
├── notif/                  canaux, notifications, service FCM
├── tracking/               RunTracker (calcul) et TrackingService (foreground service GPS)
├── analytics/              Analytics : consentement et plan de marquage
├── links/                  AppLinks : lecture des liens du site
└── ui/                     écrans Compose, ViewModels, navigation, thème, PermissionFlow
```

---

## 4. Le socle : chaque point du cours

| Point du cours | Dans PulseTrack |
|---|---|
| **UI Compose** : état hoisté, `rememberSaveable`, navigation | 5 écrans ; Navigation 3 avec clés `@Serializable`, la pile survit à la rotation et à la mort du process |
| **ViewModel** : `StateFlow`, `viewModelScope` | un ViewModel par écran ; brouillon du formulaire dans `SavedStateHandle` ; `rememberViewModelStoreNavEntryDecorator` : un formulaire fermé ne garde pas son brouillon |
| **Repository** : source unique, hors ligne par construction | `SessionsRepository` : tout passe par Room, le réseau ne fait que synchroniser |
| **Room + Flow, migrations, `schemas/`** | base v3, `MIGRATION_1_2` (outbox), `MIGRATION_2_3` (position, photo) ; schémas commités dans `app/schemas/` ; jamais de `fallbackToDestructiveMigration` |
| **DataStore** | identifiant d'appareil, réglages des rappels, jeton FCM, consentement analytics |
| **Retrofit hors du main thread** | fonctions `suspend` (Retrofit change de thread lui-même) ; OkHttp avec cache de 10 Mo et délais |
| **Cycle de vie, rotation, mort du process** | rien ne se perd, testé avec `am kill` ; position et photo gardées dans le brouillon |
| **Arrière-plan : outbox WorkManager** | `enqueueUniqueWork("sync", APPEND_OR_REPLACE)`, contrainte réseau, backoff exponentiel 30 s ; `Idempotency-Key` par opération ; `If-Match` → conflit 412 signalé |
| **Doze : « en attente » affiché** | la barre « n séances en attente d'envoi » vient de Room (`pendingCount`), pas de WorkManager |
| **Notifications** : canal, `POST_NOTIFICATIONS`, `PendingIntent` | 3 canaux (rappels, synchro, suivi) ; permission demandée à l'activation des rappels ; le clic ouvre « Nouvelle séance » (`FLAG_IMMUTABLE`) |
| **FCM, app fermée** | messages « data » : l'app est réveillée et construit la notification elle-même ; jeton enregistré auprès du serveur (`PushRegistrationWorker`) |
| **Une permission** : au bon moment, rationale, refus définitif → réglages, l'app marche sans | la position, demandée au toucher de « Ajouter ma position » ou de « Démarrer la course » ; machine à états `PermissionFlow` (NotAsked, Granted, DeniedOnce, DeniedForever), revérifiée au retour des Réglages (`ON_RESUME`) ; position approximative acceptée pour une séance, refusée pour mesurer une course |
| **Manifest** : `exported` explicite, permissions = l'usage, icône adaptive, splash | chaque composant a son `exported` ; **pas** de permission `CAMERA` (l'appareil photo système fait la prise, `TakePicture`) ni de lecture des fichiers (`PickVisualMedia`) ; `AD_ID` retirée ; icône adaptive ; splash `core-splashscreen` |
| **App Link** : `autoVerify`, `assetlinks.json` → `verified` | `https://pulsetrackisco.netlify.app/?session=<id>` ouvre le détail ; `/#session-form` ouvre une nouvelle séance ; empreintes debug **et** release publiées par la PWA |
| **Intents** : partager, ouvrir, recevoir | **partager** une séance (`ACTION_SEND`, avec son lien) ; **ouvrir** sa position sur OpenStreetMap (`ACTION_VIEW`) ; **recevoir** du texte d'une autre app (cible `SEND text/plain` → nouvelle séance, note préremplie) et les liens du site |
| **Signature** : sa clé, gardée ; `versionCode + 1` | clé RSA 4096 hors du dépôt ; R8 + réduction des ressources en release ; `versionCode` augmenté à chaque étape (1 → 10) |

---

## 5. La fonctionnalité native : le suivi de course GPS

« Suivi GPS » mesure une course en direct : chrono, distance, allure. Elle continue **écran éteint, téléphone dans la poche, app en arrière-plan**. À l'arrivée, le formulaire se remplit tout seul (type, date, durée, distance, point de départ).

**Ce que l'OS accorde, et pourquoi la PWA ne pouvait pas :**
- Un navigateur suspend une page dès que l'écran s'éteint ou que l'onglet passe en arrière-plan. `watchPosition` s'arrête, et aucune API web ne permet de continuer.
- Android accorde un **foreground service de type `location`** (`TrackingService`). Le process reste vivant et reçoit une position toutes les 2 s (Fused Location, haute précision).
- En échange, une **notification permanente** dit à l'utilisateur que le GPS tourne. Elle porte un bouton « Arrêter ».

**Ce qui est négocié avec l'OS :**
- **Le type de service** est déclaré dans le manifest et exigé depuis Android 14 (`FOREGROUND_SERVICE_LOCATION`).
- **Le démarrage** n'est possible que depuis l'écran, app au premier plan : Android 12 interdit de lancer un foreground service depuis l'arrière-plan.
- **La permission de position précise :** une position approximative (± 2 km) ne permet pas de mesurer une distance, et l'écran l'explique.
- **`START_NOT_STICKY` :** si l'OS tue le service, une course interrompue ne reprend pas toute seule avec des données fausses.

**Le calcul** (`RunTracker`, testé) :
- les mesures de précision > 30 m sont écartées ;
- un saut à plus de 12 m/s, impossible en courant, est ignoré ;
- la durée est arrondie à la minute supérieure.

Vérifié sur l'émulateur avec un parcours simulé de 467 m, écran éteint (Doze) : 0,47 km mesurés.

---

## 6. La mesure : trois événements d'analytics

Firebase Analytics, dans le même projet Firebase que FCM. Le code est dans `analytics/Analytics.kt`.

**Consentement d'abord :**
- La collecte est **coupée dans le manifest** (`firebase_analytics_collection_enabled=false`). Rien ne part avant le « Oui » de la carte affichée dans le journal.
- L'utilisateur peut changer d'avis dans **Réglages**. Son choix est gardé dans DataStore et transmis au SDK (`setAnalyticsCollectionEnabled`).
- Ni note, ni position, ni identité dans les événements. Ni identifiant publicitaire non plus : `AD_ID` et les permissions Privacy Sandbox sont retirées.

| Question | Événement | Paramètres | Posé où |
|---|---|---|---|
| ✨ **Activation** : crée-t-on une séance ? | `session_created` | `source` (manual / run), `offline` (0 / 1) | à l'enregistrement d'une nouvelle séance |
| 🔁 **Rétention** : un rappel fait-il revenir ? | `reminder_opened` | — | au clic sur la notification de rappel |
| 🚀 **Le natif** : le suivi sert-il, réussit-il ? | `run_tracked` | `result` (ok / denied / approximate), `distance_km`, `duration_min` | à l'arrêt d'une course, ou au refus de la permission |

**Les écrans sont nommés à la main** (`screen_view` : journal, session_detail, session_form, settings, tracking). L'app n'a qu'une seule Activity, donc le suivi automatique ne verrait que « MainActivity ». Il est coupé.

**Hors ligne**, les événements attendent sur le téléphone, comme l'outbox.

Vérification en direct :

```bash
adb shell setprop debug.firebase.analytics.app corsica.universita.pulsetrack
```

puis console Firebase → Analytics → **DebugView**.

---

## 7. Les choix, en une phrase chacun

- **La source de vérité, c'est Room.** L'écran ne lit jamais le réseau. Il marche donc pareil en mode avion, et le serveur n'est qu'une copie à synchroniser.
- **Room ou DataStore ?** Room pour les séances : des listes, des requêtes, des migrations. DataStore pour quelques réglages clé-valeur.
- **Quand part l'envoi ?** Tout de suite si le réseau est là, sinon quand il revient. WorkManager décide, avec la contrainte « réseau validé », même si l'app a été tuée entre-temps.
- **Pourquoi une outbox plutôt qu'un drapeau `synced` ?** Elle garde l'ordre des opérations et les suppressions. Son identifiant d'opération sert de clé d'idempotence : un envoi rejoué n'est pas compté deux fois.
- **Quelle permission, et pourquoi ?**
  - la position : seulement quand on la demande, puisque la séance marche sans ;
  - les notifications : seulement à l'activation des rappels ;
  - aucune permission caméra ni fichiers : l'OS fait la prise et le choix de la photo.
- **Push « data » plutôt que « notification » :** l'app construit elle-même la notification, sur son canal, avec un clic qui ouvre « Nouvelle séance ». Ça marche même quand elle est tuée.
- **La photo reste sur le téléphone.** Elle est réduite à 1280 px, redressée selon l'EXIF et rangée dans `filesDir`. Seul son chemin est dans Room, et elle n'est jamais envoyée au serveur.
- **Un lien externe est une entrée non fiable.** Seuls l'hôte du site, `https` et un identifiant au bon format sont acceptés. Tout le reste est ignoré.

---

## 8. Les échecs, et comment ils ont été réparés

| Ce qui a cassé | Cause | Réparation |
|---|---|---|
| Une séance ajoutée n'apparaissait pas | la `LazyColumn` gardait sa position, la nouvelle séance était au-dessus | défilement vers le haut quand la plus récente change |
| Bouton « Enregistrer » inaccessible sur petit écran | caché par le clavier | action « Enregistrer » ajoutée dans la barre du haut |
| Import depuis la galerie toujours en échec | `inJustDecodeBounds` renvoie toujours `null` (seules les dimensions sont lues) : le code le prenait pour une erreur | test sur les dimensions lues, pas sur le bitmap |
| Fichiers temporaires laissés par une photo annulée | l'URI de capture était créé avant la prise | `discardCapture` à l'annulation et à la fermeture du formulaire |
| Position ajoutée toute seule après un passage dans les Réglages | l'intention « localiser quand accordé » survivait au refus | remise à zéro au refus |
| Rappels FCM refusés par Google | clé privée du compte de service collée sans ses lignes `BEGIN/END` | le serveur reconstruit la clé PEM, quelle que soit sa forme (5 formes testées) |
| App « hors ligne » sur l'émulateur, réseau pourtant présent | DNS de l'émulateur périmé | lancement avec `-dns-server 8.8.8.8,1.1.1.1` |
| Une course comptée deux fois dans l'analytics | « Arrêter » touché deux fois depuis la notification | événement envoyé seulement si la course tournait encore |
| `keytool` plante en affichant les empreintes | bug de la traduction française du JDK | `keytool -J-Duser.language=en` |
| Le splash n'affiche pas l'icône lancé par `adb` | Android 13+ : icône réservée aux lancements depuis le lanceur | démo lancée depuis l'icône |

---

## 9. La démo : checklist

Commandes pour l'émulateur projeté ; `PKG=corsica.universita.pulsetrack`.

| ☐ | Point | Comment le montrer |
|---|---|---|
| ☐ | **Mode avion** : l'app s'ouvre, affiche ses données, dit ce qui manque | bandeau « Mode hors ligne », séance créée → « 1 séance en attente d'envoi » |
| ☐ | **Retour du réseau** : l'action faite hors ligne part toute seule | app fermée, mode avion coupé → notification silencieuse « Séances synchronisées » ; dans l'app, le badge passe à « Synchronisée » |
| ☐ | **Doze** : « en attente » affiché, l'envoi attend l'OS | hors ligne, une séance créée ; app en arrière-plan ; `adb shell dumpsys battery unplug` puis `adb shell dumpsys deviceidle force-idle` ; réseau rétabli : la séance **reste en attente** (en Doze, l'app n'a pas le réseau) ; `adb shell dumpsys deviceidle unforce` : elle part dans les secondes qui suivent. Une app **au premier plan** n'est pas soumise à Doze : la mettre en arrière-plan d'abord |
| ☐ | **Mise à jour** par-dessus l'ancienne version | installer la v0.2 (première base Room), créer des séances, puis `adb install -r` de la v1.0 : migrations 1→2→3, rien n'est perdu |
| ☐ | **Rotation, mort du process** | formulaire à moitié rempli, puis rotation ; puis `adb shell am kill $PKG` en arrière-plan et retour : le brouillon est là |
| ☐ | **Notification, app tuée** rappels activés dans les Réglages de l'app ; app balayée hors des apps récentes (process tué) ; `powershell -ExecutionPolicy Bypass -File scripts\rappel-test.ps1` (envoie le rappel de test à l'identifiant de l'app) ; Play Services redémarre le process, la notification arrive, le clic ouvre « Nouvelle séance » |
| ☐ | **Permission refusée deux fois** | position refusée deux fois → « Ouvrir les réglages » ; la séance s'enregistre sans position |
| ☐ | **App Link** | `adb shell pm get-app-links $PKG` → `verified` ; un lien de séance touché dans Messages ouvre l'app |
| ☐ | **Fonctionnalité native** | Suivi GPS, puis écran éteint ; parcours simulé (Extended controls → Location → Routes) ; « Arrêter » depuis la notification ; formulaire prérempli |
| ☐ | **Analytics** | DebugView : `session_created`, `reminder_opened`, `run_tracked` en direct |
| ☐ | **Dépôt Git** | tags `v0.1` à `v1.0`, un par étape |

Utiles pendant la démo :

```bash
adb shell dumpsys battery unplug             # Doze n'arrive jamais sur secteur
adb shell dumpsys deviceidle force-idle      # Doze forcé : la synchro attend, « en attente » reste affiché
adb shell dumpsys jobscheduler | grep -A12 "pulsetrack/androidx.work"   # contraintes du travail « sync »
adb shell dumpsys deviceidle unforce
adb shell dumpsys battery reset
adb shell pm verify-app-links --re-verify $PKG
adb shell am start -a android.intent.action.VIEW -d "https://pulsetrackisco.netlify.app/#session-form"
adb logcat -s PulseTrackGps PulseTrackFcm PulseTrackHttp
```

---

## 10. Les étapes du projet

| Tag | Étape |
|---|---|
| `v0.1` | écrans Compose et couches (ViewModel, Repository) |
| `v0.2` | Room et DataStore, migrations testées |
| `v0.3` | synchronisation : outbox WorkManager, idempotence, conflits |
| `v0.4` | position et photo, `PermissionFlow` |
| `v0.5` | notifications, rappels FCM (côté serveur : route `/api/push/fcm`) |
| `v0.6` | suivi de course GPS en foreground service |
| `v0.7` | analytics avec consentement |
| `v0.8` | App Link vérifié, signature release, R8 |
| `v0.9` | splash screen |
| `v1.0` | cible de partage (Intents : recevoir), README |

**L'IA dans ce projet :** elle a servi d'assistant, conformément à la règle du cours. Pour le socle, chaque ligne a été relue, testée sur l'émulateur et doit pouvoir être expliquée. Pour la fonctionnalité native, elle a servi de terrain d'exploration.
