# Architecture: SecretMelody (a rebuild of Youtube music's core player)

## Stack

| layer | choice | why |
| --- | --- | --- |
| app | Kotlin + Jetpack Compose (Material 3), one Android module | the user wants native Kotlin; Compose is the current default UI toolkit |
| playback | AndroidX Media3: ExoPlayer + MediaSessionService | gives background playback, lock screen / notification / Bluetooth controls, audio focus and "headphones unplugged" pause without custom code |
| music source (v1) | the phone's own audio files via MediaStore | no server, no licences: the user plays music they already own |
| artwork | `ContentResolver.loadThumbnail` (API 29+) | built in, no image library needed |
| build | GitHub Actions → `app-debug.apk` artifact | this container cannot download the Android SDK |
| backend (later) | Supabase (Postgres + Auth + Storage) | needed only when the music lives on a server (own catalogue upload, sync across devices) |

minSdk 29 (Android 10), target/compile 35.

## Schema

v1 has **no database**. Track data is read live from MediaStore on every app start:

```
Track  id (MediaStore _ID), title, artist, album, album_id, duration_ms   (IS_MUSIC != 0)
```

The play queue, shuffle and repeat live in the Media3 player inside `PlaybackService`.

Later (milestone 3+), when the app gets accounts and server-hosted music, the recon data model becomes Postgres tables (users, artists, albums, tracks, playlists, playlist_items, liked_tracks, play_events) with RLS on Supabase. That SQL is not written yet: nothing would use it in v1.

## API

v1 has no network API. The internal contract is the Media3 session:

| call | does | flow |
| --- | --- | --- |
| `setMediaItems(list, index)` + `play()` | play a list starting at a track | F02, F01 |
| `addMediaItem(current+1, item)` | "play next" | F05 |
| `seekTo / seekToNext / seekToPrevious` | Now Playing controls | F06 |
| `shuffleModeEnabled / repeatMode` | shuffle, repeat off/all/one | F05 |

The session callback `onAddMediaItems` restores each item's URI from `requestMetadata.mediaUri`, because Media3 does not pass URIs from a controller to a session by default.

## The parts that bite

- **Background playback:** the service must be a `mediaPlayback` foreground service (manifest permissions `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`). Media3 starts and stops the foreground state itself.
- **Battery savers** (Xiaomi, Oppo, Vivo, Samsung "sleeping apps") can still kill it. That is a phone setting, not code: users may need "no restrictions" for the app.
- **Permissions:** `READ_MEDIA_AUDIO` on Android 13+, `READ_EXTERNAL_STORAGE` below. "Don't ask again" means the app must send people to Settings.
- **Signing:** every CI run would make a new debug key, so an update would refuse to install over the old one. A fixed `app/debug.keystore` is committed. It is a debug key only, never for the Play Store.
- **Big libraries:** thousands of files load in one MediaStore query on a background thread. That is fine up to tens of thousands.

## Build order

1. **v1 vertical slice (this commit):** permission → list your songs (Home, S04) → search + browse by artist (S05/S06) → tap to play → mini player (S13) → Now Playing with seek, shuffle, repeat (S12) → "play next" (S15) → background + lock screen (S26). Features: must rows in `playback` and `search`.
2. **Library on device:** Liked Songs, playlists, queue screen (S08, S09, S14, S16, S17) in a Room database.
3. **Server catalogue:** Supabase auth + upload of the user's own tracks, streaming (S01–S03, the "upload own catalogue" row).
4. **Podcasts and offline (should/could).**
