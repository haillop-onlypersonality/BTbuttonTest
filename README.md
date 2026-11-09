# BTbuttonTest v2

This version tests Bluetooth media controls using a real Media3 playback pipeline.

- AndroidX Media3 ExoPlayer plays three Korean TTS sentences from synthesized WAV files.
- MediaSession is hosted by a MediaSessionService, not the Activity.
- Playback continues independently of the Activity while the service is active.
- Bluetooth media commands are routed by Media3 to the Player.
- JSON logs are shared by Activity and Service in the app's private files directory.

## Test procedure

1. Install the APK and open the app.
2. Wait until Media3 Service says CONNECTED.
3. Tap "테스트 문장 재생" once and confirm the three sentences play.
4. While audio is playing, press the headset play/pause button several times. Do not use the app's playback buttons during this test.
5. Lock the screen and repeat the button test.
6. Return to the app and copy the JSON log.

The log records service creation, MediaController connections, Player play/pause state changes, playback transitions and errors. A Bluetooth controller connection may appear as package com.android.bluetooth, depending on Android/OEM routing.
