# brutal-legend-droid

**Brütal Legend (PC) üçün tək APK-lı, Mali GPU-lar üçün optimallaşdırılmış Android çalıştırma mühiti.**

Hədəf cihaz: Samsung Galaxy A06 — Helio G85, Mali-G52 MC2, 4 GB RAM, Android 14.

## Yanaşma (vahid build)

Layihə bir neçə açıq mənbə emulatorun ən yaxşı hissələrini birləşdirir:

| Mənbə | Götürülən hissə |
|---|---|
| **Winlator** (LGPL-2.1) | İçinə gömülü X server, Vortek/VirGL/Gladio renderer, Wine, Box64, proot, rootfs — **tək APK** |
| **Mobox** | Yığcam glibc + Wine 9.3 WoW64 təcrübəsi, Mali üçün DXVK 1.10.3 async tövsiyəsi |
| **GameNative** | FEX/Box64 və yeni drayver versiyaları haqqında tədqiqat |
| **Bizim qabıq** | Tək oyunlu profil, 640×360 + FPS HUD default, Mali/Vortek tuninqi, avtomatlaşdırılmış build |

Nəticə: əlavə tətbiq (**Termux:X11**) tələb olunmur — hər şey bir APK-dadır.

## Tənzimləmələr (A06 üçün)

- Ekran: **640×360** (`Container.DEFAULT_SCREEN_SIZE`)
- Qrafika: **Vortek** (Mali üçün Winlator-ın avtomatik seçimi)
- Box64 preset: **PERFORMANCE** (default)
- FPS HUD: **SIMPLE** (default)
- DXVK 1.10.3 async mövcuddur (`assets/dxwrapper/`)

## Build

GitHub Actions hər push-da `./gradlew assembleDebug` işlədir və APK-nı artifact olaraq yükləyir.

## Status

- [x] Vahid tək-APK bazası (Winlator) — CI `success`, artifact ~157 MB
- [x] A06 üçün default tuninq (640×360, Vortek, FPS HUD)
- [ ] Brutal Legend üçün avtomatik konteyner + shortcut
- [ ] Səs və performans tuninqi (real FPS ölçümündən sonra)

## Qanuni istifadə

Oyun faylları repoda yoxdur. İstifadəçi öz qanuni nüsxəsini təmin edir. Winlator LGPL-2.1 ilə lisenziyalanıb — bax `LICENSE` və upstream layihələrə kredit.

## Arxiv (əvvəlki mərhələ)

Müstəqil runtime bundle (`scripts/build-runtime.sh`, GitHub Release `runtime-v0.1`) artıq istifadə olunmur — vahid APK bütün komponentləri özündə daşıyır.
