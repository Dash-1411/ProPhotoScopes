# Pro Photo Scopes (Android prototype)

A low-cost Android Studio project for a floating photo-scopes overlay, aimed at Pixel 9 / Android.

## What it does
- Requests Android's built-in screen-capture permission.
- Shows a movable floating panel over other apps.
- Draws RGB histogram, luminance histogram, RGB parade, waveform and a basic vectorscope.
- Processes frames locally on the phone; it does not upload screenshots.

## Important limitations
This is a **prototype source project, not an installed APK**. It has not been built or tested on a physical Pixel 9 in this environment.
- Screen capture analyses the entire visible screen, not just the Google Photos image. UI chrome, buttons, thumbnails, and the overlay itself can influence measurements.
- Android may include the overlay in captured frames; the overlay's own dark panel can therefore skew the readings.
- The vectorscope is a simplified chroma visualization, not a calibrated broadcast scope.
- Frequent screen capture uses battery and Android displays a persistent screen-sharing indicator.
- On newer Android versions, you must grant overlay permission and screen-capture permission each session.
- Some device policies or OS versions may restrict foreground media-projection startup. If it fails, stop and restart from the app.

## Build
1. Install Android Studio on a computer and open this folder as a project.
2. Let Gradle sync; install Android SDK 35 if prompted.
3. Connect Pixel 9 with Developer options + USB debugging enabled, or use an emulator.
4. Run the `app` configuration.
5. On the phone, allow “Display over other apps”.
6. Tap “Start live scopes” and approve Android's screen-capture prompt.
7. Open Google Photos and edit a photo. Drag the scopes panel to a convenient location.

## Privacy
Capture is handled locally. The app requests screen capture because Android requires user approval for reading another app's screen. Do not use screen capture while viewing sensitive content.

## Next step for reliable readings
For accurate scopes, the app should analyse the actual photo asset (via Android's photo picker) rather than the whole screen. A hybrid workflow could provide accurate imported-image scopes plus the floating panel as a reference while editing.


## Easiest build without installing Android Studio (cloud build)
This project includes `.github/workflows/android-apk.yml`, which builds an APK on GitHub's cloud computers.
1. In a browser, sign in to GitHub and create a **new public repository** named `ProPhotoScopes`.
2. Upload the contents of this ZIP to the repository (upload the files/folders inside `ProPhotoScopes`, not the outer folder itself), then commit.
3. Open the repository's **Actions** tab and select **Build installable Android APK**.
4. Press **Run workflow** if it hasn't started automatically.
5. When the run completes successfully, open the run and download the `ProPhotoScopes-debug-apk` artifact.
6. Extract that downloaded artifact ZIP on your phone and tap `app-debug.apk` to install. Android may ask you to allow installing unknown apps for the browser/files app you used.
Only install APKs you built yourself or trust. This debug APK is unsigned for Play Store distribution but can be installed manually.
