# Accessible Tuner

A chromatic tuner for Android, built for screen reader use.

- Says the nearest note and how many cents flat or sharp it is, for example "E 2, 5 cents flat" or "A 4, in tune".
- When TalkBack is on, readings are spoken by TalkBack through a live region, so they use your TalkBack voice and speed. When TalkBack is off, Android text-to-speech is used.
- Ignores the microphone while it is speaking, so it doesn't try to tune to its own voice. With headphones, turn on Headphones mode for faster updates.

## Building with GitHub (no Android Studio needed)

1. Create a new private repository on GitHub and upload all of these files to it, keeping the folders.
2. Every push runs the "Build APK" workflow (on the Actions tab). It takes about 3 to 5 minutes.
3. When it finishes, open the repository's Releases page on your phone. The "Latest build" release has AccessibleTuner.apk. Download it and open it to install (Android will ask you to allow installs from your browser the first time).

## Settings you can change in code

- `NoteMath.kt`: `A4_HZ` (reference pitch, default 440) and `IN_TUNE_CENTS` (how close counts as "in tune", default 2).
- `MainActivity.kt`: `MIN_INTERVAL_MS` (minimum time between announcements) and `CENTS_CHANGE` (how many cents of movement triggers a new announcement).
- `Announcer.kt`: `MS_PER_CHAR` (how long the app assumes TalkBack takes to speak; lower it if you use fast speech).
- `activity_main.xml`: the readout's `accessibilityLiveRegion` ("assertive" interrupts older speech, "polite" waits).
