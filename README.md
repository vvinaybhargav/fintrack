# Household Finance Tracker

Native Android app (Kotlin + Jetpack Compose) for a married couple to track
income, expenses, and savings/investments across a shared "Joint" bucket and
two "Personal" buckets, synced live via Firebase Firestore (free Spark tier).

## Why this stack

**Plain Gradle Android build**, not Expo/EAS. Reasons:
- EAS builds either need a paid tier / cloud credits for Android, or a full
  local Android SDK+NDK toolchain baked into CI — slower and flakier.
- A plain `gradle assembleDebug` on `ubuntu-latest` with the Android SDK
  action is fast (~3–5 min), needs zero paid services, and produces a
  directly installable **debug APK** with no signing step required.
- Firebase is wired in via `FirebaseOptions` built at runtime from values
  the user types into the in-app Settings screen — this avoids needing a
  `google-services.json` secret in CI entirely, which is what usually makes
  Firebase+CI Android builds fragile.

## Repo file tree

```
household_finance_tracker/
├── .github/workflows/build-apk.yml
├── .gitignore
├── README.md
├── firestore.rules
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── res/
        │   ├── values/strings.xml
        │   ├── values/themes.xml
        │   └── drawable/ic_launcher.xml
        └── java/com/household/finance/
            ├── MainActivity.kt
            ├── data/
            │   ├── Models.kt
            │   ├── AppSettings.kt
            │   └── FinanceRepository.kt
            ├── logic/
            │   ├── Calculations.kt
            │   ├── SmartAddParser.kt
            │   └── InsightsCoach.kt
            └── ui/
                ├── AppViewModel.kt
                ├── PinLockScreen.kt
                ├── DashboardScreen.kt
                ├── AddEntryScreen.kt
                ├── EntriesScreen.kt
                └── SettingsScreen.kt
```

All file contents already exist in this folder — this README only documents
setup and usage.

## 1. Push to GitHub and build the APK

```bash
cd household_finance_tracker
git init
git add .
git commit -m "Household finance tracker"
gh repo create household-finance-tracker --private --source=. --remote=origin
git push -u origin main
```

(No `gh` CLI? Create an empty repo on github.com, then `git remote add origin <url>` and `git push -u origin main`.)

Every push triggers `.github/workflows/build-apk.yml`, which:
1. Checks out the code.
2. Installs JDK 17 and the Android SDK.
3. Provisions Gradle 8.7 and runs `gradle assembleDebug`.
4. Uploads `app-debug.apk` as a workflow artifact named
   **household-finance-debug-apk**.

### One-time note on updating the APK
Every build is now signed with a fixed debug key committed at [`app/debug.keystore`](app/debug.keystore) (not a random per-CI-run key), so future downloads install as an **update** over the existing app — no uninstall needed, your PIN/local settings stay put. If you already have a copy installed from before this was added, you'll need to uninstall it **once** — after that, every new download from the `latest` release updates in place.

## 2. Download and install the APK

1. On GitHub, open the repo → **Actions** tab → the latest **Build APK** run.
2. Scroll to **Artifacts** → download `household-finance-debug-apk` (a zip
   containing `app-debug.apk`).
3. Transfer the APK to each phone (email, Drive, USB — your choice).
4. On the phone: Settings → allow "Install unknown apps" for the app you use
   to open the file, then tap the APK to install.
5. Do this on **both** phones.

The default unlock PIN is **1234** — change it immediately in Settings.

## 3. Firebase setup (free Spark plan, no card required)

1. Go to https://console.firebase.google.com → **Add project** → name it
   (e.g. "household-finance") → disable Google Analytics (not needed) →
   **Create project**.
2. In the project, click the **Android icon** ("Add app") to register an app:
   - Android package name: `com.household.finance` (must match exactly).
   - Nickname: anything.
   - You can skip downloading `google-services.json` — this app does **not**
     use it; instead click through to finish app registration.
3. Left sidebar → **Build → Firestore Database** → **Create database** →
   choose a location close to you → start in **Production mode**.
4. Go to **Firestore Database → Rules** tab, replace the contents with the
   rules from [`firestore.rules`](firestore.rules) in this repo, then
   **Publish**.
5. Get the config values: **Project settings (gear icon) → General** → scroll
   to "Your apps" → if there's no **Web app** yet, click **Add app → Web**
   (`</>` icon), register it (nickname only, no hosting needed) → it will
   show a `firebaseConfig` object like:
   ```js
   const firebaseConfig = {
     apiKey: "AIza...",
     authDomain: "household-finance-xxxx.firebaseapp.com",
     projectId: "household-finance-xxxx",
     storageBucket: "household-finance-xxxx.appspot.com",
     messagingSenderId: "123456789",
     appId: "1:123456789:web:abcdef123456"
   };
   ```
6. In the app on **both phones**: open **Settings** tab → "Firebase
   (Firestore) Sync" → enter `apiKey`, `appId`, `projectId`,
   `storageBucket`, `messagingSenderId` exactly as shown above → **Save
   Firebase Config**. Both phones must enter the **same** values — that's
   what makes them share one workspace.
7. The Settings screen shows "Status: connected" once configured correctly.
   Add an entry on one phone; it should appear on the other within seconds
   (both need internet, or it'll sync once back online — offline
   persistence is enabled).

### Security note
This app has no login system by design (shared PIN only), so the Firestore
rules in `firestore.rules` allow open read/write to the single
`workspaces/household/*` path — anyone who obtains your exact Firebase
config values could read/write that data. Keep the config values private
(don't post screenshots of Settings), same as you'd protect a shared PIN.

## 4. OpenAI key (optional)

Settings → "OpenAI (optional)" → paste your API key → Save. This enables:
- Smart Add parsing text like "22k EMI" or "paid 50k parents health
  insurance annual" into structured fields via `gpt-4o-mini`.
- An AI-generated plain-English monthly summary on the Dashboard.

Without a key, both features still work using built-in rule-based logic
(regex/keyword parsing for Smart Add, arithmetic summary text, and
threshold-based nudges) — nothing is blocked by a missing key.

**Set a low monthly spending cap on this key** in your OpenAI dashboard
(https://platform.openai.com/settings/organization/limits) since it's typed
into a mobile app.

## Data model reference

Every entry has: person (Me/Wife), type (Income/Expense/Savings), bucket
(Joint/Personal-Me/Personal-Wife), category, amount, frequency
(Monthly/Annual — annual is divided by 12 everywhere), optional note.

Seed figures matching the requirements (enter these via Smart Add or the
quick-tap form after first install):

| Person | Item | Amount | Frequency | Type | Bucket |
|---|---|---|---|---|---|
| Me | Salary | ₹1,20,000 | Monthly | Income | Joint |
| Wife | Salary | ₹1,40,000 | Monthly | Income | Joint |
| Me | EMI | ₹22,000 | Monthly | Expense | Joint |
| Me | EMI | ₹15,300 | Monthly | Expense | Joint |
| Me | Health Insurance (parents) | ₹55,000 | Annual | Expense | Personal-Me |
| Me | Health Insurance (self+wife) | ₹15,000 | Annual | Expense | Joint |
| Me | Car Insurance | ₹40,000 | Annual | Expense | Joint |
| Me | LIC | ₹40,000 | Annual | Savings | Personal-Me |
| Me | Parents' health (personal) | ₹50,000 | Annual | Expense | Personal-Me |
| Wife | EMI | ₹27,500 | Monthly | Expense | Joint |
| Wife | Music Classes | ₹4,500 | Monthly | Expense | Personal-Wife |
| Wife | Music Classes | ₹1,500 | Monthly | Expense | Personal-Wife |
| Wife | RD/FD | ₹20,000 | Monthly | Savings | Joint |
| Wife | LIC | ₹35,000 | Annual | Savings | Personal-Wife |
| Wife | PPF | ₹50,000 | Annual | Savings | Personal-Wife |

## Notes on how requirements map to the code

- **Isolated data layer**: `data/FinanceRepository.kt` defines the
  `FinanceRepository` interface; `FirestoreFinanceRepository` is the only
  Firestore-aware implementation. UI/ViewModel code never touches
  `FirebaseFirestore` directly.
- **Offline persistence**: enabled via `PersistentCacheSettings` in
  `FirestoreFinanceRepository.configure()`.
- **Real-time sync**: `addSnapshotListener` on the shared
  `workspaces/household/entries` collection.
- **Emergency fund target** = 6 × monthly household expenses
  (`Calculations.emergencyFundTarget`).
- **Policy status tags**: Active / Paid-up / Near-maturity, derived from an
  optional maturity year you can add to LIC/RD/FD/PPF/SIP entries
  (`Calculations.policyStatus`).
- **Insights coach**: rule-based by default (`Calculations.budgetNudges`,
  category vs. its own rolling average, ≥30% flagged), with an optional AI
  summary button (`logic/InsightsCoach.kt`) when a key is set.
