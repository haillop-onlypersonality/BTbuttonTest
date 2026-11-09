# BTbuttonTest

A minimal Android app for isolating Bluetooth media-button delivery to an AndroidX MediaSessionCompat.

## Test
1. Install and open the app.
2. Confirm MediaSession is ACTIVE and PlaybackState is PLAYING.
3. Lock the screen and use the Bluetooth headset buttons.
4. Return to the app and tap **로그 JSON 복사**.
5. Send the copied JSON log for analysis.

The app records Activity KeyEvents and MediaSession callbacks separately. It does not play audio; the session advertises a PLAYING state to test system media-button routing without the reader app's WebView or TTS code.
