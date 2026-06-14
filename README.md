# PageDrop 📚

**Wirelessly send books from your phone to your Kindle — no USB, no cloud, no Amazon.**

PageDrop is an open-source Android app that lets you transfer ebooks to your Kindle over local WiFi. It runs a lightweight HTTP server on your phone, and your Kindle's browser downloads books directly.

Built for Kindle owners who lost access to Amazon's Send-to-Kindle service (Kindle Paperwhite 1st Gen, Kindle Touch, etc.) but works with any Kindle that has a browser.

## How It Works

```
📱 Phone (PageDrop app)          📖 Kindle (Browser)
┌─────────────────────┐          ┌──────────────────┐
│  1. Add books        │          │                  │
│  2. Start server     │──WiFi──▶│  3. Open browser  │
│  3. Show URL         │          │  4. Go to URL     │
│                      │◀─────── │  5. Tap SYNC      │
│  Done! ✓             │          │  Book downloaded! │
└─────────────────────┘          └──────────────────┘
```

## Features

- **📤 Wireless Transfer** — No USB cables needed. Books transfer over local WiFi/hotspot
- **🔄 EPUB → MOBI Conversion** — Automatically converts EPUB files for older Kindles
- **📱 All From Your Phone** — Manage your library, queue books, control everything
- **🔒 100% Local** — No cloud, no accounts, no data leaves your network
- **📖 Kindle-Optimized** — E-ink friendly download page tested on real Kindle hardware
- **🆓 Free & Open Source** — No ads, no tracking, no BS

## Supported Formats

| Format | Transfer | Notes |
|--------|----------|-------|
| AZW3   | ✅ Direct | Best quality for Kindle |
| MOBI   | ✅ Direct | Legacy but works perfectly |
| PDF    | ✅ Direct | No reflow on 6" screens |
| TXT    | ✅ Direct | Plain text |
| EPUB   | ✅ Convert | Auto-converts to MOBI |

## Getting Started

### Prerequisites
- Android phone (API 26+, Android 8.0+)
- Any Kindle with a web browser (Experimental Browser)
- Both devices on the same WiFi network (or use your phone's hotspot)

### Install

1. Go to [Releases](../../releases) and download the latest APK
2. Install on your Android phone (enable "Install from unknown sources")
3. Open PageDrop

### Usage

1. **Add books** — Tap the ➕ button and pick ebook files
2. **Select books** — Tap books to queue them for transfer
3. **Start transfer** — Tap "Send to Kindle" and start the server
4. **On your Kindle** — Open the browser, go to the URL shown on your phone
5. **Download** — Tap SYNC next to each book

## Building from Source

### Using GitHub Actions (no Android Studio needed)
1. Fork this repository
2. Go to Actions → Build APK → Run workflow
3. Download the APK artifact

### Local Build
```bash
git clone https://github.com/YOUR_USERNAME/kindle-companion-app.git
cd kindle-companion-app
./gradlew assembleDebug
# APK at app/build/outputs/apk/debug/app-debug.apk
```

## Tech Stack

- **Language**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **Architecture**: MVVM + Repository pattern
- **DI**: Hilt
- **Database**: Room
- **HTTP Server**: NanoHTTPD
- **EPUB Parsing**: epublib

## Project Structure

```
app/src/main/java/app/pagedrop/
├── converter/          ← EPUB → MOBI conversion
├── data/               ← Book entity, DAO, Repository
├── transfer/
│   ├── server/         ← NanoHTTPD + Kindle HTML page
│   ├── service/        ← Foreground service
│   └── hotspot/        ← IP detection
└── ui/
    ├── book/           ← Library screen
    ├── transfer/       ← Transfer screen
    └── theme/          ← Material 3 theme
```

## Why?

In 2025, Amazon ended support for older Kindle devices — no more cloud sync, no more Send-to-Kindle, no more wireless book delivery. The only "official" option is USB sideloading.

PageDrop gives these devices a second life. Your phone becomes the bridge that Amazon removed.

## Contributing

Contributions welcome! Open an issue or PR.

## License

Apache License 2.0 — see [LICENSE](LICENSE)
