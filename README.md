# Yaad Dahani – Android App

Ye Yaad Dahani ka asli Android app version hai. Ye phone ke apne alarm system se chalta hai:

- App band ho, phone lock ho, ya phone restart ho jaye, tab bhi reminder waqt par bajta hai.
- Ghanti bajti hai aur task bol kar sunaya jata hai (aap ki chuni hui awaaz, raftaar aur kitni baar, sab settings ke mutabiq).
- Notification par **✓ Ho gaya / ⏰ 5 min / ⏰ 15 min** buttons hain, app khole baghair.
- Lock screen par poora alert screen khulta hai.

## APK kaise banayein (muft, GitHub par, kisi software ki zaroorat nahi)

1. github.com par muft account banayein, ya pehle se ho to login karein.
2. Upar **+** → **New repository** dabayein. Naam rakhein `yaad-dahani`, **Public** ya **Private**, phir **Create repository**.
3. Naye page par **"uploading an existing file"** link dabayein.
4. Is zip ko computer par unzip karein. `YaadDahaniApp` folder ke **andar** ki saari cheezein (folders samait, `.github` folder bhi) upload box mein kheench kar daal dein, phir **Commit changes**.
   - `.github` folder chhupa hua ho sakta hai. Windows mein: View → Hidden items. Mac mein: Cmd+Shift+. dabayein.
5. Upar **Actions** tab kholein. "Build APK" chal raha hoga (pehli dafa 3–5 minute). Agar wahan likha ho ke workflows enable karein, to enable kar ke **Build APK → Run workflow** dabayein.
6. Hara ✓ aane par usi run ko kholein. Neeche **Artifacts** mein **YaadDahani-APK** download karein. Ye zip hai, is ke andar `app-release.apk` hai.
7. APK phone par bhejein (WhatsApp/Drive/USB) aur install karein. Phone "unknown apps" ki ijazat maange to de dein.

## Pehli dafa app kholne par

App mein upar peelay (yellow) banners aayen to har aik par button daba kar ijazat de dein:
Notifications, Alarms & reminders, Lock screen alert, Battery pabandi hatana.

Phir **⚙️ Settings → 🔔 Test notification** dabayein, app band kar dein (home button), aur 10 second intezar karein. Reminder bajna chahiye.

Kuch phones (Xiaomi, Oppo, Vivo, Realme, Infinix, Tecno) mein alag se bhi karna parta hai:
Settings → Apps → Yaad Dahani → **Autostart ON** aur **Battery → No restrictions**.

## Note

- Agla version banane par purani app pehle uninstall karni paregi (har GitHub build ki apni signing key hoti hai). Uninstall se tasks mit jayenge.
- Android Studio ho to ye folder seedha khol kar bhi APK bana sakte hain.
