# QueueLess+

QueueLess+ is an Android app for managing virtual queues, food orders, order status updates, notifications, reviews, and admin queue operations. The app uses Firebase Authentication and Cloud Firestore for real-time data, with a minimal Material UI inspired by soft botanical habit-tracker screens.

## Current Status

- Builds successfully with `./gradlew assembleDebug`.
- UI uses a soft sage, blush, lavender, and white palette.
- Order history loads the current user's orders and sorts them newest first.
- Admin order status updates are synced back to the order document.
- Dashboard notification shortcut opens the notification center.
- Admin and user accounts now land on separate dashboards.
- Joining a queue opens the user's live queue status screen.

## Features

- Email/password registration and login.
- User dashboard with active queue list, queue search, and filters.
- Separate admin dashboard for queue operations, menu management, analytics, and admin logout.
- Join active queues and view live position, users ahead, wait time, and queue notices.
- Place, update, and track food orders linked to a queue entry.
- Order history with review submission.
- Notification center for queue and order updates.
- Profile screen with favorites and recent orders.
- Admin queue creation, queue pause/resume, broadcasts, queue management, QR sharing, and menu management.
- QR scan flow for marking orders ready.
- Firestore security rules and indexes included.

## Tech Stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | XML layouts, ViewBinding, Material Components |
| Auth | Firebase Authentication |
| Database | Cloud Firestore |
| Messaging | Firebase Cloud Messaging |
| Async | Kotlin Coroutines |
| QR | ZXing Android Embedded |
| Min SDK | 24 |
| Target SDK | 34 |

## Project Structure

```text
QueueLessPlus/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/queueless/plus/
│       │   ├── activities/     # Activity screens and navigation
│       │   ├── adapters/       # RecyclerView adapters
│       │   ├── models/         # Firestore data models
│       │   └── utils/          # Auth, Firestore, session, notifications, helpers
│       └── res/
│           ├── drawable/       # Icons, food images, and shared UI backgrounds
│           ├── layout/         # Activity and item XML layouts
│           ├── values/         # Colors, dimensions, themes, strings
│           └── xml/            # Android backup/data extraction config
├── firestore.rules
├── firestore.indexes.json
├── firebase.json
├── build.gradle
├── settings.gradle
└── gradlew.bat
```

## Important Files

- `FirestoreRepository.kt`: central Firestore read/write API used by activities.
- `SessionManager.kt`: stores user id, name, role, favorites, recent orders, and theme preference.
- `DashboardActivity.kt`: main user queue browser and shortcuts.
- `AdminPanelActivity.kt`: role-specific admin dashboard for queues, menu, analytics, and QR sharing.
- `OrderActivity.kt`: menu, cart, payment selection, and order creation/update.
- `OrderHistoryActivity.kt`: current user's order history and review entry point.
- `ManageQueueActivity.kt`: admin queue entry and order status management.
- `colors.xml` and `themes.xml`: shared minimal UI palette and Material component styling.

## Setup

1. Open the project in Android Studio.
2. Create or select a Firebase project.
3. Add an Android app with package name:

```text
com.queueless.plus
```

4. Download `google-services.json` from Firebase and place it in:

```text
app/google-services.json
```

5. Enable Firebase services:

- Authentication: Email/Password
- Cloud Firestore
- Cloud Messaging
- Storage, if profile image upload is used

6. Sync Gradle and run the app on an emulator or device.

## Firebase Deploy

Install and log in to Firebase CLI:

```bash
npm install -g firebase-tools
firebase login
```

Deploy Firestore rules and indexes:

```bash
firebase deploy --only firestore:rules,firestore:indexes
```

The indexes file includes composite indexes for queue entries, notifications, and orders.

## Build

From the project root:

```bash
./gradlew assembleDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
```

## Firestore Collections

### `users/{userId}`

```text
userId: String
name: String
email: String
role: "user" | "admin"
fcmToken: String
loyaltyPoints: Number
avatarUrl: String
```

### `queues/{queueId}`

```text
queueId: String
queueName: String
description: String
location: String
avgServiceTime: Number
createdBy: String
isActive: Boolean
isPaused: Boolean
pauseReason: String
broadcastMessage: String
currentCount: Number
```

### `queueEntries/{entryId}`

```text
entryId: String
userId: String
queueId: String
userName: String
timestamp: Timestamp
status: "waiting" | "completed" | "left"
notified: Boolean
orderId: String
orderDetails: String
orderStatus: "waiting" | "preparing" | "ready" | "completed"
```

### `orders/{orderId}`

```text
orderId: String
userId: String
queueId: String
items: String
total: Number
paymentMethod: String
status: "Placed" | "Preparing" | "Ready" | "Completed"
timestamp: Number
```

### Other Collections

- `menu`: admin-managed menu items.
- `notifications`: user notifications.
- `reviews`: queue reviews.
- `chat`: support chat messages.

## Admin Setup

New users register with role `user`. To make an admin:

1. Open Firebase Console.
2. Go to Firestore.
3. Open `users/{userId}`.
4. Set `role` to `admin`.

Admin-only features are protected in the app and in Firestore rules.

## Role-Based Navigation

- Users land on `DashboardActivity`.
- Admins land on `AdminPanelActivity`.
- Admins can preview the user dashboard from the admin dashboard, but normal users cannot open admin tools.
- Admin queue management lives in `ManageQueueActivity`, where each order can move through Waiting, Preparing, Ready, and Completed.

## Order Flow

1. User joins a queue.
2. Queue entry is created in `queueEntries`.
3. User is taken to `UserStatusActivity` to see live position and wait time.
4. User can place an order from the status screen.
5. Order is stored in `orders`.
6. The order id is attached to the queue entry.
7. Admin updates order status from `ManageQueueActivity` or QR scan.
8. Status is synced to both `queueEntries.orderStatus` and `orders.status`.
9. User sees completed and active orders in `OrderHistoryActivity`.

## Notes

- `getOrdersForUser()` fetches by `userId` and sorts locally, so order history works even before indexes are deployed.
- Deploy the included indexes for better production performance.
- The dashboard order count is scoped to the logged-in user.
- Admin analytics still uses the global order list and requires admin access.
- Queue join writes a queue entry first, then updates the queue count. The included Firestore rule allows logged-in users to update only `currentCount`.

## License

Created for academic use. Free to modify for learning and project work.
