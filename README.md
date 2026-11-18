# Issue 8827

### Github Issue Link
- https://github.com/firebase/firebase-android-sdk/issues/8827

### Summary
- The Remote Config real-time listener stops receiving updates indefinitely if a connection is attempted while the client is still in a backoff period. This prevents the app from receiving new configurations until the process is restarted.

### Prerequisite:
- Add `google-services.json` file in app

### Steps:

1. Launch the app on a device or emulator and ensure the ConfigUpdateListener connects successfully.
2. Simulate a network failure or use a proxy to return an HTTP 503 from the Remote Config backend. Keep the app in the foreground until the connection fails, triggering the backoff period.
```
adb shell cmd connectivity airplane-mode enable
adb shell cmd connectivity airplane-mode disable
```
3. Put the app in the background.
```
adb shell am force-stop com.google.dps
```
4. Bring the app back to the foreground before the backoff period ends (e.g., within 1 minute of the failure).
```
adb shell am start -n com.google.dps/.MainActivity
```
