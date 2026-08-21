# Shipping a RideVibe beta build

Distribution runs through **Firebase App Distribution**. The Gradle side is already
wired up. This guide covers the parts that need your credentials or console access.

Run every command below from the **repo root** in **Git Bash** (not PowerShell —
the paths and pipes here assume a POSIX shell).

- Part 1 — one-time setup, roughly an hour
- Part 2 — shipping each build, about five minutes
- Part 3 — what your testers do
- Part 4 — troubleshooting
- Part 5 — known limitations of this beta

---

# Part 1 — One-time setup

## Step 1. Create the upload keystore

`keytool` is not on your PATH, but Android Studio bundles it. Set a shortcut for
this session so the rest of the commands stay readable:

```bash
export KEYTOOL="/c/Program Files/Android/Android Studio/jbr/bin/keytool.exe"
```

Verify it works — this should print a version, not "command not found":

```bash
"$KEYTOOL" -help 2>&1 | head -3
```

Now generate the keystore:

```bash
"$KEYTOOL" -genkeypair -v -keystore ridevibe-upload.jks -alias ridevibe-upload -keyalg RSA -keysize 2048 -validity 10000
```

**This is where your password goes — place one.** keytool will prompt you:

```
Enter keystore password:
```

> **Type your password and press Enter. Nothing will appear on screen** — no
> asterisks, no moving cursor. That is normal terminal behaviour for password
> input, not a frozen prompt. Type it carefully and blind.

Then it asks you to confirm:

```
Re-enter new password:
```

Then five identity questions. These end up in the certificate. They don't affect
functionality, but use real values:

```
What is your first and last name?            ->  Alfred <surname>
What is the name of your organizational unit? ->  Engineering
What is the name of your organization?        ->  RideVibe
What is the name of your City or Locality?    ->  <your city>
What is the name of your State or Province?   ->  <your province>
What is the two-letter country code?          ->  PH
```

It shows a summary and asks `Is CN=..., OU=... correct?` — type **`yes`** and Enter.

Finally:

```
Enter key password for <ridevibe-upload>
        (RETURN if same as keystore password):
```

> **Just press Enter** to reuse the same password. The rest of this guide assumes
> you did. (If you set a different one here, it goes in `keyPassword` in Step 2
> while the first password goes in `storePassword`.)

Confirm the file exists at the repo root:

```bash
ls -la ridevibe-upload.jks
```

> **Back this file up somewhere offline right now**, along with the password in
> your password manager. If you lose either, you can never ship an update to this
> app — every tester would have to uninstall and reinstall from scratch.

## Step 2. Put the password where Gradle can read it

Create your private config file from the template:

```bash
cp keystore.properties.template keystore.properties
```

Open `keystore.properties` in Android Studio and fill in the two password lines.
It should end up looking exactly like this, with your actual password in place of
`YourPasswordHere`:

```properties
storeFile=../ridevibe-upload.jks
storePassword=YourPasswordHere
keyAlias=ridevibe-upload
keyPassword=YourPasswordHere
```

Formatting rules that trip people up:

- **No quotes** around the password. `storePassword="hunter2"` makes the quotes
  part of the password.
- **No spaces** around the `=`.
- **No trailing spaces** after the password — they're invisible and they break it.
- If your password contains a **backslash `\`**, double it: `pa\\ss`. This is a
  Java `.properties` file and a single backslash is an escape character. Other
  symbols (`!@#$%^&*:;,.?`) are fine as-is.
- `storeFile=../ridevibe-upload.jks` is correct as written — that path is resolved
  relative to the `app/` module, so `../` points at the repo root where Step 1 put
  the file. Leave it alone unless you moved the `.jks`.

This file is gitignored along with `*.jks`, so neither will ever be committed.

## Step 3. Verify the signing actually works

```bash
./gradlew clean assembleRelease
```

Then check what came out:

```bash
ls app/build/outputs/apk/release/
```

**You want to see `app-release.apk`.** If you still see
`app-release-unsigned.apk`, the keystore wasn't picked up — go to Part 4.

## Step 4. Register the fingerprints with Firebase

Skipping this is the single most common way a beta build fails. Google Sign-In
keys off your signing certificate, so login works in debug and breaks in the
tester build with a useless "error 10".

Print the fingerprints (it will prompt for your keystore password again):

```bash
"$KEYTOOL" -list -v -keystore ridevibe-upload.jks -alias ridevibe-upload
```

Look for this block in the output:

```
Certificate fingerprints:
         SHA1: A1:B2:C3:...
         SHA256: D4:E5:F6:...
```

Now, in the Firebase console:

1. Go to **Project settings** (gear icon, top left) → **General** tab.
2. Scroll to **Your apps** and select the `com.ridevibe.app` Android app.
3. Click **Add fingerprint**. Paste the **SHA-1** value. Save.
4. Click **Add fingerprint** again. Paste the **SHA-256** value. Save.
5. Click **Download google-services.json**.
6. Replace the existing file:

```bash
cp ~/Downloads/google-services.json app/google-services.json
```

## Step 5. Register the key hash with Facebook

Facebook wants a base64 SHA-1, not the colon-separated hex from Step 4. This
command produces it (it will prompt for your keystore password):

```bash
"$KEYTOOL" -exportcert -alias ridevibe-upload -keystore ridevibe-upload.jks | openssl sha1 -binary | openssl base64
```

The output is a single 28-character line ending in `=`, like `rXhT9k...Ac=`.

Then at [developers.facebook.com](https://developers.facebook.com) → your app →
**Settings** → **Basic** → scroll to the **Android** section → paste it into
**Key Hashes** → **Save Changes**.

## Step 6. Install and authenticate the Firebase CLI

You have npm 10.9.2, so:

```bash
npm install -g firebase-tools
```

Then log in — this opens a browser window:

```bash
firebase login
```

Confirm it worked:

```bash
firebase projects:list
```

Your RideVibe project should appear in the list.

## Step 7. Create the tester group

In the Firebase console → **App Distribution** (left sidebar, under *Release &
Monitor*) → **Testers & Groups** tab → **Add group**.

> Name the group so that its **alias is exactly `beta`**. That string is what
> `app/build.gradle.kts` uploads to. If the alias differs, uploads succeed but no
> tester receives anything.

Add your testers' email addresses to that group. Use the address each person
actually signs into their Android device with — see Part 3.

---

# Part 2 — Shipping each build

Three things, every time.

**1. Bump the version.** Open `gradle.properties` and increase `ridevibe.versionCode`
by one. App Distribution rejects a duplicate version code, and testers won't be
offered the update without it.

```properties
ridevibe.versionCode=2
ridevibe.versionName=1.0.0-beta.2
```

**2. Update `release-notes.txt`** at the repo root. Testers see this text in the
App Tester app. Tell them what changed and what you want tested.

**3. Build, smoke-test, upload.**

```bash
./gradlew clean assembleRelease
```

> **Install that APK on a real device and walk the whole flow before uploading**:
> sign in with Google, sign in with Facebook, search a trip, pick seats, check
> out, view the ticket, open the Scan tab. The release build runs R8 with
> hand-written keep rules, and a rule gap only ever shows up in release — never in
> the debug builds you develop against. Ten minutes here saves a broken beta.

Then upload:

```bash
./gradlew appDistributionUploadRelease
```

Testers are notified automatically.

---

# Part 3 — What your testers do

Send them these instructions verbatim.

1. You'll get an email titled *"You've been invited to test RideVibe"*. Open it
   **on your Android phone**, not your computer.
2. Tap **Get started**. This installs the **Firebase App Tester** app.
3. Sign in with **the same email address the invite was sent to**. Signing in with
   a different Google account shows an empty list of builds — this is the number
   one support question.
4. Android will warn about **installing from unknown sources**. Allow it for App
   Tester. This is expected for beta builds distributed outside the Play Store.
5. Tap **RideVibe** → **Download** → **Install**.
6. Future builds appear in the App Tester app automatically.

---

# Part 4 — Troubleshooting

**`keytool: command not found`**
Run the `export KEYTOOL=...` line from Step 1 first. The `export` only lasts for
the current terminal session — set it again in a new terminal.

**Build still produces `app-release-unsigned.apk`**
Gradle didn't find your credentials. Check in order:
- `keystore.properties` is at the **repo root**, not inside `app/`.
- Run `ls ridevibe-upload.jks` — the file is where `storeFile` says it is.
- No quotes, no trailing spaces, no spaces around `=` in `keystore.properties`.
- Look for `RideVibe: no release keystore configured` in the build output — if
  it's there, the file genuinely isn't being read.

**`Keystore was tampered with, or password was incorrect`**
The password in `keystore.properties` doesn't match what you typed at the keytool
prompt. Most likely a typo made while typing blind, or an unescaped backslash.
Verify the password itself is right by running the Step 4 `-list` command and
entering it manually — if that succeeds, the problem is the properties file.

**Google Sign-In fails with `error 10` / `DEVELOPER_ERROR` in the tester build**
The release SHA-1 isn't registered, or you didn't re-download
`google-services.json` after adding it. Redo Step 4 completely, then rebuild.

**Facebook login says `Invalid key hash`**
The error dialog itself contains the exact hash Facebook expected — copy that
value into the Key Hashes field. It's faster than re-deriving it.

**`The requested version code is already in use` on upload**
Bump `ridevibe.versionCode` in `gradle.properties` and rebuild.

**Tester signed in but sees no builds**
They used a different Google account than the one you invited. Either have them
switch accounts in App Tester, or add their actual address to the `beta` group.

**Upload fails with a credentials or permission error**
Run `firebase login --reauth`, then confirm with `firebase projects:list`.

---

# Part 5 — Known limitations of this beta

- **The app runs entirely on mock data.** `USE_MOCK_DATA` is `true` in
  `core-network/src/main/java/com/ridevibe/core/network/di/RepositoryModule.kt`,
  and the base URL in `NetworkModule.kt` is still a placeholder. Profile, wallet,
  support and itinerary have no real implementation at all. This beta validates
  UX and stability, not data correctness — `release-notes.txt` says so up front.
- **There is no backend authentication.** Sign-in is client-side only; no request
  carries an `Authorization` header yet.
- **No in-app feedback SDK.** Testers report by email. Adding
  `firebase-appdistribution` would enable in-app feedback and screenshots, but
  it's a beta-channel SDK that shouldn't ship to production, so it needs its own
  build type.
- **Play Console is still ahead of you.** Before production you'll need an
  internal testing track and a permissions declaration for `READ_CONTACTS`.
