# Recon map: Spotify (Android)

Scope: the whole app, prioritised. **must** = core player (home, search, playback, queue, playlists, library, background playback). **should** = podcasts. **could** = offline, social, personalised recommendations.
For: (1) the user's own artist/label catalogue, (3) a practice and portfolio project, and personal use. Content is only what the user owns or royalty-free audio.
Date: 2026-10-15

> **Source caveat:** this container could not reach spotify.com, play.google.com or developer.spotify.com (DNS blocked). This map draws on one web search plus well-documented public behaviour of the app. The URLs below are where to check each point. Before /replica-build, take your own screenshots on your own phone into `replica/screens/` (S01.png, S02.png …) and fix any row here that doesn't match.

## Sources

| # | source | URL | notes |
| --- | --- | --- | --- |
| 1 | Play Store listing | https://play.google.com/store/apps/details?id=com.spotify.music | the pitch, store screenshots of the key screens. Not reachable from here. |
| 2 | Premium / pricing page | https://www.spotify.com/premium/ | which features are paid: offline, any order, unlimited skips, no ads. Not reachable from here. |
| 3 | Help centre | https://support.spotify.com/ | full feature list (Jam, sleep timer, crossfade, Blend, canvas, lyrics). Not reachable from here. |
| 4 | Public Web API docs | https://developer.spotify.com/documentation/web-api | the data model almost for free: track, album, artist, playlist, show, episode, audiobook, player. Not reachable from here. |
| 5 | Offline limits (press) | https://www.kitguru.net/channel/generaltech/matthew-wilson/spotify-raises-offline-download-limit-to-10000-tracks-users-can-download-on-up-to-5-devices/ | 10,000 tracks per device, 5 devices (an older announcement) |
| 6 | Free vs Premium (2026) | https://www.eskimo.travel/en/blog/spotify-premium-worth | Free: 128 kbps, shuffle-only on mobile, about 6 skips an hour, ads. Premium about $12.99/mo in the US. |
| 7 | Free offline test | https://routenote.com/blog/?p=25568 | Free-tier offline test, unconfirmed |
| 8 | the user's own account | — | the real thing. Use your own Spotify account at human speed and take screenshots. Spotify's terms forbid reverse engineering the app, so stay at screenshots and behaviour, not code or network calls. |

## Core loop

Open the app, get a feed of music made for you, tap and it plays right away, and the player follows you everywhere (mini player, lock screen, Bluetooth) while you save what you like into your library.

## Verified from the user's screenshots (2026-10-15, Android, Thai UI)

Files: `replica/screens/S04-home.jpg`, `S05-search*.jpg`. These overrule the rows below where they differ.

- **Bottom nav has 5 tabs**, not 3–4: หน้าหลัก (Home), ค้นหา (Search), คอลเลกชันของคุณ (Your Library), Premium, สร้าง (Create +). Translucent dark bar over content.
- **Home (S04):** avatar circle (initial letter) + filter chips ทั้งหมด / เพลง / พอดแคสต์ (selected = filled accent). Sections are titled shelves with a small grey subtitle above the bold title ("เริ่มฟัง", "เพลงที่คุณฟังบ่อยช่วงนี้"). Shelves are **pages of 3 track rows** swiped horizontally (next page peeks at the right edge), not only square cards. Track row: 56dp art, title, artist, saved check (accent circle), ⋮. The playing track's title is in the accent colour with a "…" equaliser prefix.
- **Mini player (S13):** floating rounded card, 8dp margin, **background tinted from the artwork colour**, art + title + artist + Connect-device icon + saved check + play button, thin white progress line along the bottom edge.
- **Search (S05):** big "ค้นหา" title + camera (scan) icon; **white** search bar ("คุณต้องการฟังอะไร") that sticks to the top on scroll; 2-column grid of colour tiles (title top-left, rotated artwork cropped bottom-right); a "ค้นพบสิ่งใหม่ๆ สำหรับคุณ" shelf of tall video-style cards between the top tiles and "browse all".

| ID | screen | route / how to reach | purpose | key components | states seen |
| --- | --- | --- | --- | --- | --- |
| S01 | Welcome / sign in | first launch | sign up or log in | logo, "Sign up free", Google/Facebook/phone buttons, "Log in" | default, loading, error |
| S02 | Sign up (multi-step) | S01 → Sign up | email, password, date of birth, gender, name | text inputs, stepper, Next button | validation error, email taken |
| S03 | Taste onboarding | after sign up | choose artists you like, as a seed for recommendations | artist bubble grid, search, Done | none chosen (Done disabled), chosen |
| S04 | Home | bottom tab 1 | personalised feed | filter chips (All/Music/Podcasts), quick-access grid of 6–8, horizontal shelves of cards | loading skeleton, filled, offline |
| S05 | Search (browse) | bottom tab 2 | browse by category | search bar, colour category tiles | filled, offline |
| S06 | Search (typing) | tap the search bar | live results | input, result chips by type, result rows, recent searches | empty (recents), no results, loading |
| S07 | Category / genre page | S05 → tile | playlists in that genre | header, playlist shelves | filled |
| S08 | Your Library | bottom tab 3 | everything saved | type chips (Playlists/Artists/Albums/Podcasts/Downloaded), sort, list/grid toggle, "+" | empty (new user), filled, filtered |
| S09 | Playlist | card/row | view and play a playlist | cover, title, owner, duration, play/shuffle FAB, download, ••• , track rows | empty playlist (add songs), filled, downloading, offline |
| S10 | Album | card/row | view an album | cover, artist, year, track numbers, play | filled |
| S11 | Artist | card/row | artist page | hero image, monthly listeners, Follow, Popular top 5, discography, about | filled |
| S12 | Now Playing (full) | tap the mini player | control playback | big artwork, title, artist, heart, seek bar, prev/play/next, shuffle, repeat, devices, share, lyrics card | playing, paused, buffering, ad (Free) |
| S13 | Mini player | bottom of every screen | quick control | small artwork, title, device, play/pause, heart, progress line | hidden (nothing playing), playing |
| S14 | Queue | S12 → queue icon | see and edit what's next | Now playing, "Next in queue", "Next from: …", drag handles, remove | empty queue, filled |
| S15 | Track context menu | ••• on any track | track actions | bottom sheet: like, add to playlist, add to queue, go to album/artist, share, hide song | — |
| S16 | Add to playlist | S15 → Add to playlist | pick a playlist | "New playlist", search, playlist list with ticks | — |
| S17 | Create / edit playlist | Library "+" or S16 | name, cover, description, order | text input, image picker, reorder handles | — |
| S18 | Lyrics | S12 → lyrics card | synced lyrics | scrolling lines highlighted in time | none available, synced |
| S19 | Connect / devices | S12 → device icon | play on another device | device list, current device | single device, many |
| S20 | Podcast show | search or home | podcast page | cover, follow, about, episode list, sort | filled |
| S21 | Episode | S20 → episode | episode detail | description, play, progress, download, speed | not started, half-played, finished |
| S22 | Settings | profile avatar → Settings | settings | audio quality, crossfade, gapless, normalisation, download over cellular, storage, sleep timer, logout | — |
| S23 | Profile | avatar | public profile | avatar, followers/following, public playlists | — |
| S24 | Downloads / offline mode | Library → Downloaded, or no internet | listen with no internet | "You're offline" banner, downloaded-only list | offline, nothing downloaded |
| S25 | Premium paywall | tap a locked feature (Free) | upsell | plan cards, "Try Premium" | — |
| S26 | Lock screen / notification | Android system | control outside the app | MediaSession notification with artwork, prev/play/next | — |

## Flows

```
F01 New user starts listening
    S01 -> S02 -> S03 -> S04 -> tap a card -> S09 -> play
    happy path clicks: about 8 (from sign-up to music playing)
    edge: email already used, under 13, no network during sign up

F02 Search for a song and play it
    S04 -> S05 -> S06 (type) -> tap a result -> playing (S13)
    happy path clicks: 3 plus typing
    edge: no results, offline, misspelling

F03 Like a song and find it later
    S12 heart -> S08 -> Liked Songs (S09)
    happy path clicks: 1 to like, 2 to find
    edge: liked offline (syncs later)

F04 Create a playlist and add songs
    S08 "+" -> S17 name -> S09 (empty) -> "Add songs" -> pick
    or S15 -> S16 -> pick playlist
    happy path clicks: 4
    edge: duplicate song warning, empty name

F05 Control the queue
    S15 "Add to queue" -> S12 -> S14 reorder / remove
    happy path clicks: 2
    edge: queue empties into autoplay radio, shuffle changes "Next from"

F06 Background playback
    playing -> home button / lock -> S26 controls -> headphones unplugged = pause
    edge: phone call interrupts, Bluetooth disconnects, other app takes audio focus

F07 Listen to a podcast and resume later
    S06 -> S20 -> S21 play -> leave -> come back -> resumes at position
    happy path clicks: 3
    edge: playback speed, marked as played, new episode notification

F08 Download for offline
    S09 download toggle -> progress -> airplane mode -> S24
    happy path clicks: 1
    edge: storage full, cellular download off, 30-day licence check-in (unverified), removed from the server

F09 Play on another device
    S12 -> S19 -> pick device
    edge: device disappears, two phones on the same account
```

## Components

| component | variants | states | used on |
| --- | --- | --- | --- |
| Bottom tab bar | 3–4 tabs (Home, Search, Library, Premium for Free users) | active, inactive | all |
| Mini player | — | hidden, playing, paused, buffering | all except S12 |
| Media card | square (album/playlist), round (artist), wide (podcast) | default, pressed, playing indicator | S04, S05, S07, S11 |
| Shelf (horizontal list) | with title, with "Show all" | loading skeleton | S04, S07, S11 |
| Quick-access tile | — | default, playing | S04 |
| Filter chip | single-select, removable | selected, unselected | S04, S06, S08 |
| Track row | with number, with artwork, with explicit "E" badge, downloaded dot | playing (green title + bars), unavailable (greyed), hidden | S06, S09, S10, S11, S14 |
| Play/shuffle FAB | play, pause, shuffle-play | — | S09, S10, S11, S20 |
| Icon button | heart, download, more, share, shuffle, repeat (off/all/one) | on, off, disabled | S09, S12 |
| Seek bar | — | dragging, buffering | S12, S21 |
| Bottom sheet menu | track, playlist, artist | — | S15 |
| Collapsing header | gradient from artwork colour | expanded, collapsed (sticky title) | S09, S10, S11, S20 |
| Search bar | — | focused, with text, clear | S05, S06, S16 |
| Category tile | colour + rotated artwork | — | S05 |
| Text input | — | focus, error | S02, S17 |
| Toast/snackbar | "Added to Liked Songs", with Undo | — | all |
| Offline banner | — | — | S04, S24 |
| Loading skeleton | card, row | — | S04, S09 |
| Empty state | icon + message + CTA | — | S08, S09, S14, S24 |
| Lyrics view | — | synced, static, unavailable | S18 |

## Inferred data model

```
User         id, email, display_name, avatar_url, birth_date, country, plan (free | premium), created_at
             evidence: S02 sign-up fields, S23, S25. Web API "Get Current User's Profile" (source 4)
             confidence: high

Artist       id, name, bio, image_url, header_image_url, monthly_listeners (derived), verified
             evidence: S11. Web API Artist object
             confidence: high

Album        id, artist_id (+ album_artists for collabs), title, type (album | single | ep | compilation), release_date, cover_url, label, copyright
             evidence: S10, S11 discography. Web API Album object
             confidence: high

Track        id, album_id, title, duration_ms, track_number, disc_number, explicit, isrc, audio_url (files per quality), lyrics_id?, play_count (derived)
             evidence: S09/S10 rows, S12, S18. Web API Track object
             confidence: high
TrackArtist  track_id, artist_id, position (features)          confidence: high

Playlist     id, owner_id, name, description, cover_url (or a 2x2 mosaic), is_public, is_collaborative, snapshot_id, created_at
             evidence: S09, S17. Web API Playlist (snapshot_id)
             confidence: high
PlaylistItem playlist_id, position, track_id | episode_id, added_by, added_at
             evidence: S09 rows, "added by" in collab lists
             confidence: high

LikedTrack   user_id, track_id, liked_at            (Liked Songs is a virtual playlist)   confidence: high
LibraryItem  user_id, item_type (album | artist | playlist | show), item_id, added_at, pinned
             evidence: S08 chips, sort, pin. confidence: high
Follow       follower_id, followee_id (user)          evidence: S23. confidence: medium

Show         id, publisher, title, description, cover_url, rss_url?      evidence: S20. confidence: high
Episode      id, show_id, title, description, audio_url, duration_ms, published_at        confidence: high
EpisodeProgress  user_id, episode_id, position_ms, completed, updated_at
             evidence: F07 resume, "played" tick. Web API resume_point. confidence: high

PlaybackState  user_id, device_id, context_uri (playlist/album/artist), item_id, position_ms, is_playing, shuffle, repeat (off | context | track), updated_at
             evidence: S12, S19. Web API Player endpoints. confidence: high
QueueItem    user_id, position, item_id, source (user_added | context)      evidence: S14. confidence: medium
Device       id, user_id, name, type, last_seen        evidence: S19. confidence: medium
PlayEvent    user_id, track_id, played_at, ms_played, context_uri      (recently played, recommendations, artist plays)
             evidence: "Recently played" shelf, Web API recently-played. confidence: high
Download     user_id, device_id, item_type, item_id, downloaded_at, licence_expires_at
             evidence: S24, F08. confidence: medium (licence expiry is a guess)

Category     id, name, colour, image_url; CategoryPlaylist(category_id, playlist_id)   evidence: S05/S07. confidence: high
HomeSection  computed per user (not stored): "Recently played", "Made for you", "Jump back in" …   confidence: guess
```

Relationships: Artist 1-n Album 1-n Track; Track n-n Artist; User 1-n Playlist 1-n PlaylistItem n-1 Track|Episode; User n-n Track (Liked); Show 1-n Episode; User 1-n PlayEvent; User 1-1 PlaybackState; User 1-n Device.

## Feature matrix

See `features.csv`. Must: 22, should: 12, could: 14, skip: 7.

## Out of scope (cannot or should not be cloned)

- **Spotify's catalogue** (100M+ licensed songs, podcasts, audiobooks). Use only your own music, your label's, or royalty-free/CC music (e.g. Free Music Archive, with each licence checked) for practice.
- Label/publisher licence deals, royalty accounting and Content ID matching.
- Spotify's users and their data (follows, public playlists, listening history).
- Editorial playlists and Spotify's own content (Wrapped, DJ voice, Spotify Originals).
- Recommendation algorithms trained on their data. A small rule-based version (same genre, played often) is possible.
- Connect integrations with cars, speakers, consoles (hardware partner deals).
- Names, logos, the green, the Circular font, the sound logo. replica-brand handles this.

## Size

Screens 26, flows 9, entities 19. Hard parts:
1. **Audio engine on Android:** background playback, MediaSession, audio focus, gapless/crossfade, buffering. Use ExoPlayer/Media3, don't write your own.
2. **Offline + sync:** encrypted downloads, licences, syncing likes/playlists made offline.
3. **Streaming and storage:** transcoding several qualities (HLS), CDN, bandwidth cost.
4. Multi-device Connect (realtime), synced lyrics, recommendations.

Size: **XL** for everything. The skill says rescope at XL: do the **must** rows first (core player, about L, roughly a quarter for one person), then add podcasts (should) and offline/social (could) step by step.
