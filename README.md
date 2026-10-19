# Ditto

แอปฟังเพลงบน Android (ชื่อเดิม SecretMelody) เล่นไฟล์ในเครื่องเป็นหลัก และนำเข้า/สตรีมจาก YouTube และ Spotify ได้

## สิ่งที่ทำต่อ (Roadmap)

ไอเดียรวบรวมจากแอปเพลงโอเพนซอร์ส: SimpMusic, Metrolist, Rhythm, PixelPlayer, Namida, Gramophone, Auxio, Shuttle2, RiMusic, InnerTune, BlackHole
(ดูจาก README ของแต่ละโปรเจกต์ ยังไม่ได้ตรวจโค้ดของเขาว่าทำได้จริงตามที่เขียน)

### ทำก่อน: คุ้มสุด ทำเสร็จได้ในรอบเดียว

#### 1. เก็บเพลงสตรีมไว้ในเครื่องชั่วคราว (มีใน Metrolist, SimpMusic)
- **ปัญหาตอนนี้:** เพลงจาก YouTube โหลดใหม่ทุกครั้งที่ฟัง เปลืองเน็ต และเน็ตหลุดก็ฟังไม่ได้
- **วิธีทำ:**
  - ใช้ `SimpleCache` + `CacheDataSource.Factory` ของ Media3 (ใช้ library ที่มีอยู่แล้ว)
  - ห่อ data source ใน `PlaybackService.kt` ตรงที่ตั้ง `ResolvingDataSource.Factory(... StreamResolver)` อยู่
  - ใช้ `LeastRecentlyUsedCacheEvictor` จำกัดขนาด เช่น 500 MB
- **ข้อควรระวัง:**
  - ลิงก์ googlevideo หมดอายุใน 6 ชม. จึงต้องตั้ง cache key เป็น video id ด้วย `setCustomCacheKey` ใน `StreamResolver`
  - ถ้าใช้ URL เป็นคีย์ cache จะไม่ถูกใช้ซ้ำ
- **ขนาดงาน:** ประมาณ 20 บรรทัด

#### 2. รูปศิลปินจาก Deezer (มีใน PixelPlayer)
- **ปัญหาตอนนี้:** หน้าศิลปินเป็นวงกลมสีเทา
- **วิธีทำ:**
  - เรียก `https://api.deezer.com/search/artist?q=<ชื่อ>` (ฟรี ไม่ต้องใช้คีย์) แล้วหยิบ `data[0].picture_xl`
  - ต้องเช็กว่าชื่อตรงกันก่อนใช้ เพื่อไม่ให้หยิบรูปผิดคน
  - ทำ cache รูปในเครื่อง แบบเดียวกับ `LyricsCache`
  - แสดงที่ `CollectionScreen` ตอน `g.isArtist` และในรายการศิลปินของ `LibraryScreen`
- **ข้อควรระวัง:** ยังไม่ได้ตรวจว่าศิลปินไทยมีรูปครบแค่ไหน ส่วน API ลองเรียกจากเครื่อง build แล้วใช้ได้

#### 3. การ์ดเนื้อเพลงเป็นรูป (มีใน SimpMusic, Spotify)
- **ต่อยอดจาก:** ตอนนี้แชร์เนื้อเพลงได้เป็นข้อความ (`shareLyrics` ใน `Ui.kt`: กดค้างที่บรรทัด หรือกดปุ่ม "แชร์เนื้อเพลง")
- **วิธีทำ:**
  - วาดรูปลง `Bitmap`/`Canvas` ประกอบด้วยปกเพลง + บรรทัดที่เลือก + ชื่อเพลง/ศิลปิน
  - ใช้สีพื้นหลังจาก `rememberArtColor`
  - บันทึกลง cache dir แล้วแชร์ผ่าน `FileProvider` (`ACTION_SEND` แบบ `image/png`)
- **ใช้ทำอะไร:** ลงสตอรี่ IG / LINE

#### 4. สรุปการฟังรายเดือน (แบบ Wrapped) (มีใน SimpMusic)
- **ต่อยอดจาก:** สถิติที่มีอยู่แล้ว `Stats.kt` (`summarize`, `StatsScreen`)
- **ต้องเพิ่ม:**
  - ตอนนี้ `SongStat` เก็บแค่ยอดรวม ไม่ได้แยกเดือน
  - ต้องเก็บแยกรายเดือน เช่น key = `yyyy-MM` ในไฟล์สถิติ
- **แสดง:** การ์ดเพลงท็อป 5, ศิลปินท็อป, จำนวนชั่วโมงที่ฟังในเดือนนั้น และแชร์เป็นรูปได้ (ใช้ร่วมกับข้อ 3)

#### 5. เสียงโมโน (มีใน Rhythm)
- **สำหรับ:** คนใส่หูฟังข้างเดียว
- **วิธีทำ:**
  - ใช้ `ChannelMixingAudioProcessor` ของ Media3 (รวมซ้าย+ขวา)
  - ใส่ผ่าน `DefaultRenderersFactory` → `buildAudioSink` ใน `PlaybackService.kt`
  - เพิ่มสวิตช์ในหน้าเสียง เก็บใน `AudioSettings` (`Audio.kt`) แบบเดียวกับ `skipSilence`

### ลงแรงปานกลาง

- **เพลย์ลิสต์แนะนำประจำวัน (Daily Mix)** (มีใน PixelPlayer, Metrolist)
  - เอาเพลงที่ฟังบ่อยจาก `mostPlayed` ผสมกับเพลงที่ YouTube แนะนำต่อ (InnerTube `next` endpoint)
- **เพลงค่อยๆ ต่อกัน (Crossfade)** (มีแทบทุกแอป)
  - Media3 ไม่มีให้ในตัว ต้องใช้ ExoPlayer 2 ตัวเล่นสลับกันพร้อมไล่ volume
  - เป็นส่วนที่บั๊กง่าย ต้องทดสอบบนเครื่องจริงเยอะ
- **ส่งประวัติการฟังไป Last.fm** (มีใน Metrolist, SimpMusic)
  - ต้องสมัคร API key ของ Last.fm ก่อน
  - ส่ง scrobble ตอนฟังครบ 30 วิ/ครึ่งเพลง ใช้จุดนับเดียวกับที่ `Stats.kt` นับการเล่น
- **เลือกดูเพลงตามแนวเพลง (Genre)** (มีใน PixelPlayer)
  - ข้อมูลแนวเพลงมีอยู่ใน MediaStore (`MediaStore.Audio.Genres`) เพิ่มเป็นแท็บใน `LibraryScreen`
- **วิดเจ็ตเนื้อเพลงบนหน้าจอหลัก** (มีใน Rhythm)
  - ใช้ `LyricsCache` + `currentLine` ที่มีอยู่ ต่อจาก `Widget.kt`

### ไอเดียจากแอปประเภทอื่น

- **นาฬิกาปลุกด้วยเพลง:** ตั้งให้เพลงจากคลังดังเป็นนาฬิกาปลุก (`AlarmManager.setAlarmClock` แล้วสั่ง `PlaybackService` เล่น)
- **ไล่ลบเพลงซ้ำในเครื่อง:**
  - หาไฟล์ที่ชื่อเพลง + ศิลปิน + ความยาว (±2 วิ) ตรงกัน แล้วให้เลือกลบ
  - ใช้ระบบลบที่ขอสิทธิ์ผ่าน MediaStore ที่มีอยู่แล้ว
- **ปุ่มสุ่มทั้งอัลบั้ม** (มีใน Shuttle2): สุ่มเลือกอัลบั้ม แล้วเล่นตามลำดับในอัลบั้ม

### ไม่แนะนำ

ใหญ่เกินไป หรือต้องมีเซิร์ฟเวอร์ของเราเอง

- **Chromecast:** ถ้าจะส่งไฟล์ในเครื่องไปเล่น ต้องทำให้มือถือเป็นเซิร์ฟเวอร์ด้วย
- **ฟังพร้อมเพื่อน (Listen Together):** ต้องมีเซิร์ฟเวอร์กลาง
- **เพลย์ลิสต์ที่สร้างด้วย AI / Discord Rich Presence:** ต้องใช้คีย์และระบบภายนอก
- **AutoEQ (พรีเซ็ตเสียงตามรุ่นหูฟัง):** ต้องฝังข้อมูลหูฟังหลายพันรุ่น ทำให้ APK ใหญ่ขึ้นมาก
- **ดึงวิดีโอพื้นหลัง (Canvas) จาก Spotify อัตโนมัติ:**
  - ลองแล้ว token ที่ไม่ได้ล็อกอินได้คำตอบว่างเปล่า ต้องใช้ session ที่ล็อกอิน Spotify
  - ตอนนี้ให้ผู้ใช้เลือกวิดีโอเองแทน (`Canvas.kt`)

## สิ่งที่ยังไม่เคยทดสอบบนเครื่องจริง

- สตรีมจาก YouTube
- ล็อกอิน Google ใน WebView และการซิงก์บัญชี YouTube
- Android Auto
- แปลเนื้อเพลง (ใช้ Google Translate ที่ไม่ต้องใช้คีย์ ซึ่งถูกบล็อกจากเครื่อง build)
- เลื่อนเนื้อเพลงอัตโนมัติตาม intro ของคลิป MV
- วิดีโอพื้นหลัง
- การติดตั้งทับหลังเปลี่ยนชื่อแอปเป็น Ditto

## Build

```
gradle testDebugUnitTest assembleRelease
```

CI (`.github/workflows/build-apk.yml`) สร้าง APK ให้ทุกครั้งที่ push ดาวน์โหลดได้จากหน้า Actions
