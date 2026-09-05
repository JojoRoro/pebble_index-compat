# PT2 Index capture

This branch adds an opt-in bridge from the dedicated
[Index Capture watchapp](https://github.com/JojoRoro/pebble-index-emu/tree/feat/pt2-index-capture)
to the existing Index local-audio processing queue.

## Build in GitHub Actions

This fork retains the official build workflow and adds one step: a successful Android
job uploads `pebble-index-compat-debug-apk`. Download it from the run's **Artifacts**
section. The workflow uses the repository's dummy Firebase configuration and does not
need production service credentials to compile.

## Use

1. Install the debug APK from a successful Actions run.
2. Pair your PT2 with the modified companion app.
3. Enable **Enable Index Feed** and then **PT2 Index capture** in Index settings.
4. Install the matching watchapp and assign **Index Capture** to Quick Launch.
5. Complete system dictation. **Queued on phone** confirms that the phone saved and
   accepted audio for Index processing; the final feed item may finish later.

The phone must remain connected during capture. A lost Bluetooth acknowledgement can
occur after queueing, so check Index before retrying.

## Safety notes

- The route is fixed to one watchapp UUID and is off by default.
- It accepts only the upstream pinned codec's known 16 kHz / 320-sample Speex layout.
- Capture is bounded to 60 seconds, decoding/saving has a short deadline, and no
  dedicated-app audio falls back to ordinary dictation when the bridge is disabled.
- The existing Index storage, transcription, agent and integration settings continue
  to govern how a queued recording is processed.

The design was independently implemented after reviewing, but not importing, PR
[coredevices/mobileapp#303](https://github.com/coredevices/mobileapp/pull/303).
