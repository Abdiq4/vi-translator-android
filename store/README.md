# Google Play: what to submit

Everything for the Play Console listing is in this repository:

- **Text, 33 languages:** `fastlane/metadata/android/<locale>/` (`title.txt` ≤ 30, `short_description.txt` ≤ 80,
  `full_description.txt` ≤ 4000, `changelogs/<version code>.txt` ≤ 500). This is the layout
  [fastlane supply](https://docs.fastlane.tools/actions/supply/) uploads; by hand, copy each language into
  *Grow → Store presence → Main store listing → Manage translations*.
- **Images:** `fastlane/metadata/android/en-US/images/` (Play shows the default language's images in every
  language that has none of its own):
  - `icon.png` 512×512, the hi-res icon
  - `featureGraphic.png` 1024×500
  - `phoneScreenshots/*.png` 1080×2070 (Play allows at most 2:1)
- **The app:** the `.aab` from the GitHub Release (`vi-translator-android-X.Y.Z.aab`). It is the `play`
  build: no self-update and no `REQUEST_INSTALL_PACKAGES` (Play forbids both); Play updates it. The
  GitHub APK is the `github` build, which updates itself from GitHub Releases. Both have the same
  package name and key, so a phone can move from one to the other only by uninstalling first.

## First-time setup

1. Create the app: name *VI Translator*, default language English (US), *App*, *Free*.
2. *Setup → App signing:* keep **Play App Signing** on. The key in
   `C:\dev\vitranslator.app\android-phone\release.p12` is the **upload key**.
3. Personal developer accounts: run a **closed test with at least 12 testers for 14 days** before
   production is unlocked.

## App content answers

| Form | Answer |
| --- | --- |
| Privacy policy | https://vitranslator.app/privacy/ (it has an *Android phone app* section) |
| Ads | No ads |
| App access | Needs the user's own PS5 or Xbox and a PSN or Microsoft account; see *Reviewer notes* below |
| Content rating (IARC) | Category *Utility, Productivity, Communication or Other*; no violence, sexuality, language, gambling, user interaction or sharing of location; the app shows translations of text read from the user's own console. Expected: Everyone / PEGI 3 |
| Target audience | 13 and over (a tool for console players; not aimed at children) |
| Data safety | See below |
| Government app / financial / health / news | No |
| Foreground service permissions | `FOREGROUND_SERVICE_SPECIAL_USE` and `FOREGROUND_SERVICE_MEDIA_PROJECTION`, see below |

### Data safety

**No data collected, no data shared** with the developer:

- The PSN sign-in happens on Sony's page; the app reads only the account ID from Sony's answer and
  keeps it on the phone (with the PS5's link key), to register with the user's own PS5. No password,
  no token is kept. The Microsoft sign-in happens on Microsoft's own page in Microsoft's player
  (play.xbox.com); the app never sees it.
- The lines read from the game go to the translation service the user picks, with the user's own
  key, at the user's request (a user-initiated transfer, which Play doesn't count as sharing). Game
  text isn't personal data.
- Downloads from vitranslator.app (translation data) and GitHub carry no personal data.
- Translations go to the user's own TV on the local network.

Encrypted in transit: yes for everything that leaves the home network (HTTPS); the TV link on the
home network is plain HTTP to the user's own TV. Deletion: nothing is stored off the device.

### Foreground services

The one service (`run.TranslateService`) declares both types:

**Special use** (`specialUse`), text for the declaration:

> While the user plays, the app keeps a PS Remote Play stream of the user's own PS5 open, reads the
> game's subtitles from it and sends the translations to the user's TV, which draws them over the
> game. The user plays on the TV, not on the phone, so the phone's screen is usually off: the
> service keeps the stream, the reading and the small home-network server the TV connects to
> running. It starts only when the user presses *Start translating*, shows a notification with
> *Stop*, and stops when the user stops.

**Media projection** (`mediaProjection`): for the Xbox, the game plays in Microsoft's Remote Play
web player inside the app. If the phone can't copy that player's picture directly (it comes out
black on some phones), the app offers to capture its own screen instead; Android asks the user to
allow it each time. Only the app's own player is read, to find the subtitles.

Attach a short video of each: *Start translating* with a PS5 and the subtitles showing on the TV;
the Xbox player with the screen capture prompt.

## Policy risks to know before submitting

- **PS Remote Play** is chiaki-ng's open-source implementation, and the PSN sign-in uses Remote
  Play's own sign-in client, as chiaki-ng does (third-party Remote Play apps are on Play, but Sony
  could object); the listing says the app is unofficial and not affiliated with Sony.
- **The Xbox player** is Microsoft's own web page (play.xbox.com) shown in a WebView. Play's policy
  on apps that mainly wrap a third-party website could be raised; the app's own function (reading
  and translating) is what it adds.
- **Trademarks:** the listing names PS5, PlayStation and Xbox only to say what the app works with,
  with the trademark notice at the end. It doesn't name the game.

## Reviewer notes (App access → instructions)

> VI Translator translates a console game's subtitles and shows them over the game on the user's
> TV. It needs the user's own console on the same network, so it can't be fully used without one:
> a video of the whole flow is here: <add a link to an unlisted video>.
> 1. *Setup → Source:* choose PlayStation 5 (sign in to PSN, find the PS5, type the PIN it shows
>    under Settings › System › Remote Play › Link Device) or Xbox (sign in to Microsoft's player).
> 2. *Setup → Show on → Connect a TV:* open VI Translator on an Android TV, type its 6-digit code.
>    Or turn on *Share on my home network* and open the address in a browser.
> 3. *Play → Start translating:* the translations show on the TV and on the phone.
> No account with the developer is needed.
