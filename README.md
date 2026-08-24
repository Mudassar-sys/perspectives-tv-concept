# Perspectives Frame — Android TV application

Reference implementation of an Android TV application for a wall-mounted 4K
photo frame, with a phone acting as its remote and as the only route photos can
take onto the panel.

Built by **CrewNexa** as concept work. Not affiliated with Perspectives Digital
Arts, Inc. Interactive walkthrough of the same flow:
https://mudassar-sys.github.io/perspectives-tv-concept/

---

## The problem

The product is a 28.2 inch and a 50.5 inch panel, 3:2, 4K, 10-bit, 128 GB on
device, Wi-Fi and Bluetooth 5.2. It is sold on one promise: photos arrive on the
wall on their own.

That promise rests on something that no longer exists. On 31 March 2025 Google
removed `photoslibrary.readonly`, `photoslibrary.sharing` and the full
`photoslibrary` scope. An application can now only read media that it created
itself. Continuous library sync is not available to anyone any more.

The replacement is the Picker API, and it is interactive by design. The user
chooses, on their own device, every time. A wall panel has no browser and no
Google Photos app, so the panel can never do the choosing.

That single constraint decides the architecture. The phone is not a convenience
feature bolted onto the TV app. It is the door.

---

## What is here

| Area | File |
|---|---|
| Picker session, polling, paging | `photos/PickerSessionClient.kt` |
| BLE provisioning on first boot | `pairing/BleProvisioner.kt` |
| Six digit pairing and device token | `pairing/PairingCodeService.kt` |
| Local discovery and control channel | `remote/LocalLinkService.kt` |
| Command surface | `remote/RemoteCommand.kt` |
| Aggregated home feed with fallback | `content/FeedRepository.kt` |
| On-device album cache | `content/AlbumCache.kt` |
| Display mode | `ui/DisplayScreen.kt` |
| Manifest, leanback and banner | `app/src/main/AndroidManifest.xml` |

---

## Six problems this had to solve

**1. Photos cannot be pulled, only handed over.**
The panel creates a picker session, renders the returned `pickerUri` as a QR
code, and polls `mediaItemsSet` until Google reports the selection is finished.
Sessions are single use, so a fresh one is created for every hand-off. Google
returns a `pollingConfig`, and it is honoured rather than replaced with a fixed
interval, because ignoring it is how an integration gets rate limited.

**2. Picker URLs are not renderable as they arrive.**
A base URL needs a size suffix. Phone originals are routinely forty megapixels
and will stall a TV-class SoC on decode, so the request asks for the long edge
the panel actually has.

**3. First boot has no network and no keyboard.**
Provisioning runs over BLE. A GATT characteristic write is capped at the
negotiated MTU, which starts at 23 bytes, so an SSID and passphrase have to be
chunked and reassembled. The passphrase is sealed to an ephemeral key published
in the advertisement rather than written in the clear. Provisioning stays
available after the frame is online, because home routers get replaced.

**4. A short code on a wall is guessable.**
Security does not rest on six digits. It rests on a two minute lifetime, one
live challenge at a time, a constant time comparison, and a lockout after five
wrong attempts. What the phone receives is a device-scoped token, never the
user's own session token, because a frame can be lifted off a wall.

**5. One transport is never enough.**
Local discovery over NSD is the fast path and survives an internet outage.
A cloud relay covers the phone being on a different network, and the isolated
guest network case which is more common in apartments than people expect. BLE
covers the state before any network exists. Shipping only the first one is the
usual reason a companion app works in an office and fails in homes.

**6. The home screen is too slow for a panel.**
The existing gallery builds its home view with one call per category. Measured
against the live API on 24 August 2026 that is 28 requests in flight, each
returning in 873 to 1168 ms under that load, against 297 to 649 ms when called
alone. Responses carry no `cache-control` and sit behind no CDN. The frame asks
for one aggregated document and falls back to per-category calls when the server
has not been updated, so it is shippable before any backend work lands.

---

## Two details that decide whether it feels finished

**No spinner, ever.** The next image is decoded while the current one is still on
screen and the swap only happens once it is ready. A loading indicator on a wall
reads as a fault, not as patience.

**The cache pins what is playing.** A least-recently-used policy does the wrong
thing for a slideshow, because the first item it evicts is the one coming up
next in the loop. The playing album is pinned and everything else competes for
what is left of the budget.

---

## Stack

Kotlin, Compose for TV (`androidx.tv:tv-material`), Coil, OkHttp, Retrofit,
Moshi, ZXing, Coroutines. `minSdk` 26, which is the floor for most shipping
Android TV panels.

`android.software.leanback` is required and `android.hardware.touchscreen` is
declared not required. Getting that pair wrong is the most common reason a build
is rejected from the Play Store TV track. The launcher intent filter is
`LEANBACK_LAUNCHER`, without which the app never appears on a TV home screen.

---

## Tests

`PairingCodeServiceTest` covers expiry, single use, lockout after repeated
failures, and code shape.

```
./gradlew :app:testDebugUnitTest
```
