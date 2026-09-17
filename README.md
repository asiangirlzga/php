# PHP Server App (Android)

This project runs a real PHP interpreter as a background process on the
device (`php -S 127.0.0.1:8080 -t <webroot>`) and displays the result in
an in-app WebView. It's the same technique apps like KSWEB / Palapa Web
Server / PHP Server use.

## The one thing you must add yourself: the `php` binary

I can't ship a precompiled PHP-for-Android binary in this response, so
you need to get one and drop it into the project. Options, easiest first:

### Option A — Reuse an existing PHP-for-Android build (fastest)
Open-source projects already publish static PHP-CLI builds for Android:
- **php-android** (swankjesse / and various forks on GitHub) — search
  GitHub for "php-android static binary" — several maintained forks
  publish `.so`/binary artifacts per ABI (arm64-v8a, armeabi-v7a, x86_64).
- **Termux's `php` package** — you can pull the compiled binary out of
  a Termux install (`pkg install php`, then the binary is under
  `$PREFIX/bin/php`), then repackage it for use outside Termux. This is
  the most reliable path if a prebuilt release isn't available for your
  target ABI.

### Option B — Cross-compile PHP yourself with the Android NDK
1. Download PHP source (php.net) and the Android NDK.
2. Configure a static build with a minimal set of extensions
   (`--disable-all`, then enable only what you need — cli SAPI, pdo,
   mbstring, json, etc.) targeting `aarch64-linux-android`,
   `armv7a-linux-androideabi`, and `x86_64-linux-android`.
3. This is the most control but takes real effort (expect a few hours
   of fighting `./configure` flags). There are step-by-step community
   guides for "compile PHP for Android NDK" you can follow.

### Placing the binary
Once you have a `php` (or `php-cgi`) executable for each ABI:

1. Rename each one to **`libphp.so`** (Android's packaging system only
   extracts native libraries matching `lib*.so`, and only files placed
   this way land in a directory Android marks executable at runtime —
   `context.applicationInfo.nativeLibraryDir`).
2. Place them here:
   ```
   app/src/main/jniLibs/arm64-v8a/libphp.so
   app/src/main/jniLibs/armeabi-v7a/libphp.so
   app/src/main/jniLibs/x86_64/libphp.so
   ```
3. Build. `PhpServer.kt` will find and exec whichever one matches the
   device's ABI automatically.

> Note: Since Android 10 (API 29), apps can only execute binaries from
> their own `nativeLibraryDir` — this jniLibs trick is the standard
> workaround for shipping a non-Java executable inside an APK.

## Where your PHP code goes

Put your `.php` files in `app/src/main/assets/www/` (an `index.php`
sample is already there). On first launch, `PhpServer.kt` copies that
folder into the app's private storage (`context.filesDir/www`) because
PHP needs to read/execute from a real filesystem path, not from inside
the APK.

## Building locally

Open this folder in Android Studio (Iguana or newer), let Gradle sync,
and run on a device/emulator matching one of the ABIs you provided a
binary for. Or from the command line (with a system `gradle` install,
since no wrapper jar is committed):

```bash
gradle assembleDebug
```

## Building via GitHub Actions

A workflow is included at `.github/workflows/build-apk.yml`. Push this
project to a GitHub repo and it will:

1. Set up JDK 17, Gradle, and the Android SDK on a GitHub-hosted runner.
2. Run `gradle assembleDebug`.
3. Upload the resulting `.apk` as a workflow artifact named
   **`app-debug-apk`** — download it from the run's **Summary** page
   under "Artifacts".

**Important:** the workflow builds whatever is in the repo, including
the `libphp.so` files — so before pushing, replace the placeholder
`PUT_libphp.so_HERE.txt` files in `app/src/main/jniLibs/<abi>/` with
real binaries and commit them (see the section above). If you skip
this, the APK will build fine but the app will crash at runtime with
"php binary not found."

Two optional upgrades to the workflow once you're comfortable with it:
- **Commit a Gradle wrapper** (`gradle wrapper` run locally, then commit
  `gradlew`, `gradlew.bat`, and `gradle/wrapper/`) so the workflow (and
  anyone cloning the repo) doesn't depend on a system Gradle version —
  swap the build step to `./gradlew assembleDebug`.
- **Sign a release build** instead of just `assembleDebug`, using
  `secrets` for your keystore, if you want a release-ready APK rather
  than a debug one.

## How it works, in short

- `MainActivity` starts `PhpServer` on a background thread.
- `PhpServer.start()` launches `php -S 127.0.0.1:8080 -t <webroot>` via
  `ProcessBuilder`.
- Once the server responds to a health-check request, the WebView loads
  `http://127.0.0.1:8080/`.
- The server is bound to `127.0.0.1` only — it's not reachable from
  outside the device, which is what you want for a self-contained app.

## Common pitfalls

- **"php binary not found"**: your `libphp.so` isn't in `jniLibs/<ABI>/`
  for the ABI of the device/emulator you're testing on.
- **Blank WebView / connection refused**: check Logcat for tag
  `PhpServer` — it logs the exact command and streams php's own stdout.
- **PHP extensions missing** (e.g. `mysqli`, `curl`): the binary you use
  must have been compiled with those extensions built in (static PHP
  builds for mobile typically bundle a fixed extension set).
- **Ships an executable in your APK**: if you plan to publish to Google
  Play, be aware bundling a general-purpose interpreter executable can
  draw extra scrutiny in review — this pattern is normally fine for
  personal/internal apps, testing tools, or offline local-first apps.
