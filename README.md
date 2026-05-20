# QueueLess+ – Smart Virtual Queue Management System

A full-stack Android application that eliminates physical waiting lines by allowing users to join and manage queues digitally with real-time updates, admin QR sharing, dark mode support, a refreshed scrollable admin UI, and a modern blue accent palette.

---

## Project Structure

```
.
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/queueless/plus/
│       │   ├── activities/
│       │   │   ├── AdminAnalyticsActivity.kt
│       │   │   ├── AdminMenuActivity.kt
│       │   │   ├── AdminPanelActivity.kt
│       │   │   ├── ChatActivity.kt
│       │   │   ├── CreateQueueActivity.kt
│       │   │   ├── DashboardActivity.kt
│       │   │   ├── FavoritesRecentActivity.kt
│       │   │   ├── LoginActivity.kt
│       │   │   ├── ManageQueueActivity.kt
│       │   │   ├── NotificationCenterActivity.kt
│       │   │   ├── OrderActivity.kt
│       │   │   ├── OrderHistoryActivity.kt
│       │   │   ├── ProfileActivity.kt
│       │   │   ├── QRScanActivity.kt
│       │   │   ├── QueueDetailActivity.kt
│       │   │   ├── RegisterActivity.kt
│       │   │   ├── SplashActivity.kt
│       │   │   └── UserStatusActivity.kt
│       │   ├── adapters/
│       │   │   ├── AdminMenuAdapter.kt
│       │   │   ├── AdminQueueAdapter.kt
│       │   │   ├── CartAdapter.kt
│       │   │   ├── ChatAdapter.kt
│       │   │   ├── ManageEntryAdapter.kt
│       │   │   ├── MenuAdapter.kt
│       │   │   ├── NotificationAdapter.kt
│       │   │   ├── OrderHistoryAdapter.kt
│       │   │   ├── QueueAdapter.kt
│       │   │   └── QueueEntryAdapter.kt
│       │   ├── models/
│       │   │   ├── AppNotification.kt
│       │   │   ├── CartItem.kt
│       │   │   ├── ChatMessage.kt
│       │   │   ├── MenuItem.kt
│       │   │   ├── Orders.kt
│       │   │   ├── Queue.kt
│       │   │   ├── QueueEntry.kt
│       │   │   ├── Review.kt
│       │   │   └── User.kt
│       │   └── utils/
│       │       ├── AuthManager.kt
│       │       ├── Extensions.kt
│       │       ├── FCMService.kt
│       │       ├── FirestoreRepository.kt
│       │       ├── HuggingFaceClient.kt
│       │       ├── NotificationReceiver.kt
│       │       ├── NotificationScheduler.kt
│       │       ├── SecurityGuards.kt
│       │       ├── SessionManager.kt
│       │       └── ThemeUtils.kt
│       └── res/
│           ├── layout/
│           ├── values/
│           ├── drawable/
│           └── xml/
├── firestore.rules
├── firestore.indexes.json
├── firebase.json
├── build.gradle
├── settings.gradle
├── gradle.properties
├── gradlew.bat
└── local.properties
```

---

## Technologies

| Layer | Technology |
|---|---|
| Language | Kotlin |
| IDE | Android Studio |
| UI | XML + Material Design 3 |
| Auth | Firebase Authentication |
| Database | Firebase Cloud Firestore |
| Push | Firebase Cloud Messaging (FCM) |
| QR Scanning | ZXing Android Embedded |
| Architecture | Activity-based Android app with Firebase backend |
| Async | Kotlin Coroutines + Flow |

---

## Setup Instructions

### 1. Firebase Project Setup

1. Go to [Firebase Console](https://console.firebase.google.com/)
2. Create a new project named **QueueLessPlus**
3. Add an Android app with package name `com.queueless.plus`
4. Download `google-services.json` and place it in the `app/` directory
5. Enable the following services:
   - **Authentication** → Email/Password sign-in
   - **Firestore Database** → Start in production mode
   - **Cloud Messaging** → No extra setup needed

### 2. Deploy Firestore Rules & Indexes

```bash
# Install Firebase CLI
npm install -g firebase-tools

# Login
firebase login

# Initialize (select your project)
firebase init firestore

# Deploy rules and indexes
firebase deploy --only firestore:rules,firestore:indexes
```

### 3. Open in Android Studio

1. Open Android Studio
2. Select **File → Open** and choose the `QueueLessPlus/` folder
3. Let Gradle sync complete
4. Connect a device or start an emulator (API 24+)
5. Click **Run**

---

## Firestore Database Structure

### `users` collection
```
users/{userId}
  ├── userId: String
  ├── name: String
  ├── email: String
  ├── role: "user" | "admin"
  └── fcmToken: String
```

### `queues` collection
```
queues/{queueId}
  ├── queueId: String
  ├── queueName: String
  ├── description: String
  ├── location: String
  ├── avgServiceTime: Int  (minutes)
  ├── createdBy: String    (admin userId)
  ├── isActive: Boolean
  └── currentCount: Int
```

### `queueEntries` collection
```
queueEntries/{entryId}
  ├── entryId: String
  ├── userId: String
  ├── queueId: String
  ├── userName: String
  ├── timestamp: Timestamp
  ├── status: "waiting" | "completed" | "left"
  └── notified: Boolean
```

---

## Core Algorithms

### 1. Queue Position
```kotlin
// Sorted by timestamp (FIFO). Position = index + 1
val entries = getWaitingEntries(queueId)   // sorted ASC by timestamp
val position = entries.indexOfFirst { it.userId == userId } + 1
```

### 2. Estimated Wait Time
```kotlin
// waitTime = usersAhead × avgServiceTime
val usersAhead = position - 1
val waitMinutes = usersAhead * queue.avgServiceTime
```

### 3. Notification Trigger
```kotlin
// Notify when position ≤ 2
if (position <= 2 && !entry.notified) {
    // Show banner + mark notified in Firestore
    FirestoreRepository.markEntryNotified(entry.entryId)
}
```

---

## Setting an Admin User

Admins are not self-registered. After a user signs up, update their role in Firestore:

```
Firebase Console → Firestore → users → {userId} → role = "admin"
```

Alternatively, use the Firebase Admin SDK or a Cloud Function to automate role assignment.

---

## QR Code Format

QR codes encode a URL-style payload:
```
queueless://join?queueId=<QUEUE_ID>
```

Generate QR codes using any QR generator library. The `QRScanActivity` parses this format and navigates directly to the queue detail screen.

---

## Implemented Enhancements

- Dark mode toggle now available in the admin panel and persisted through user session.
- Admin queue cards can generate QR codes for queue sharing.
- Admin QR dialog now includes a Share button to send queue invites via text.
- QR codes use the `queueless://join?queueId=<QUEUE_ID>` payload format.
- Firebase-driven admin queue management and queue editing remain intact.

---

## License

This project is created for academic purposes. Free to use and modify.
