# Akshara for Android

**Privacy: this app is offline-only.** It has no internet permission, so Android itself blocks it from connecting anywhere. Nothing you type, write or photograph leaves your device.

Five-language library (German, English, Arabic, Chinese, Khmer) with script history, Egyptian hieroglyphs and a camera scanner, packaged as an Android app.

GitHub builds the APK for you, so your phone only needs Termux to send the code and fetch the result.

## What is inside

```
akshara-android/
├── .github/workflows/build-apk.yml   GitHub builds the APK on every push
├── app/src/main/assets/www/index.html   the whole Akshara app (works offline)
├── app/src/main/java/org/akshara/app/MainActivity.java   WebView + camera
├── app/src/main/AndroidManifest.xml
└── app/build.gradle, build.gradle, settings.gradle
```

## 1. Set up Termux (once)

Install Termux from **F-Droid** (the Play Store version is outdated). Then:

```bash
pkg update && pkg upgrade -y
pkg install git gh unzip -y
termux-setup-storage          # tap Allow, so Termux can see Downloads
git config --global user.name  "Your Name"
git config --global user.email "you@example.com"
gh auth login                 # GitHub.com → HTTPS → Login with a web browser
```

## 2. Unpack the project

Download `akshara-android.zip` to your phone's Downloads folder, then:

```bash
cd ~
cp ~/storage/downloads/akshara-android.zip .
unzip akshara-android.zip
cd akshara-android
```

## 3. Push to GitHub (this starts the build)

```bash
git init -b main
git add .
git commit -m "Akshara 1.0"
gh repo create akshara --public --source=. --push
```

A public repository gets unlimited free GitHub Actions minutes. Use `--private` if you prefer; private repos have a monthly free allowance.

## 4. Watch the build and get the APK

```bash
gh run watch                       # pick the run; takes about 3–6 minutes
gh run download --name akshara-apk # saves app-debug.apk here
cp app-debug.apk ~/storage/downloads/
```

Open **Files → Downloads → app-debug.apk** and tap it. Android will ask you to allow installs from this source the first time.

You can also do this in a browser: your repo → **Actions** → the latest run → **Artifacts** → `akshara-apk` (it downloads as a zip containing the APK).

## 5. Publish a release (optional)

```bash
git tag v1.0
git push origin v1.0
```

The APK then appears on the repo's **Releases** page with a permanent download link you can share.

## Updating the app (safe full sync)

Download the new `akshara-android.zip`, then replace everything except the git history:

```bash
cd ~/downloads
rm -rf akshara-new && mkdir akshara-new && cd akshara-new
unzip -q ~/storage/downloads/akshara-android.zip
cd ~/downloads/akshara-android
find . -mindepth 1 -maxdepth 1 ! -name .git -exec rm -rf {} +
cp -r ~/downloads/akshara-new/akshara-android/. .
git add -A && git commit -m "Update Akshara" && git push
gh run watch
rm -f app-debug.apk && gh run download --name akshara-apk
cp app-debug.apk ~/storage/downloads/
```

## The camera scanner in the app

- **Take photo** opens the phone camera; **Choose photo** opens the gallery; **Live camera** shows a viewfinder. Android asks for camera permission the first time.
- **Compare by eye** works fully offline: your photo above the Pallava, Pyu, Brahmi, Grantha, Khmer, Thai or Egyptian sign charts, with zoom.
- **Read with Claude** needs internet and your own Anthropic API key (create one at console.anthropic.com). The key is stored only on your phone, and each scan is billed to your API account. Inside claude.ai the same button uses your Claude account instead, with no key.

## The Pallava keyboard (works in every app)

Version 3 adds **Akshara Pallava**, a real Android keyboard.

1. Open Akshara → **Pallava** tab → **Enable keyboard**.
   (Or: Settings → System → Languages & input → On-screen keyboard → Manage keyboards.)
2. Switch on **Akshara Pallava**. Android shows a standard warning that keyboards can read what you type; this keyboard has no internet access of its own and stores nothing.
3. In any text box, tap the keyboard switch icon (or the 🌐 key) and choose **Akshara Pallava**.

Layout: page 1 has ka–ma in the traditional five-by-five order; the **ya…** key opens page 2 with ya–ha, ḷa, the ten vowels, virāma, ṃ, ḥ and daṇḍas. Vowel keys add a vowel sign after a consonant and a full vowel letter elsewhere. 🌐 switches back to your normal keyboard.

The keyboard types standard Grantha Unicode. Inside Akshara it displays in the Pallava font; other apps show Grantha letters (or boxes if they have no Grantha font).

## Offline notes

Everything except Claude reading works offline: lessons, progress, charts, quiz, name writer. Most Android phones include fonts for Khmer, Arabic, Chinese, Thai, Brahmi and Egyptian hieroglyphs. If any characters show as boxes, open the app once with internet so it can load its web fonts.

## Building inside Termux instead

Possible, but not recommended: Termux has no official Android SDK, and community setups break often. GitHub Actions is the reliable route and costs nothing for public repos.

## Before sharing publicly

- This is a debug-signed APK, fine for personal use and sharing with friends. The Play Store needs a release-signed build.
- The app contains your uploaded reference charts and photos. Get permission from their publishers or replace them with openly licensed images first.
