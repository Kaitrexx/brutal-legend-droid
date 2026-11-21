# brutal-legend-droid

Android üçün **yalnız Brütal Legend (PC)** oyununu işlətmək üçün optimallaşdırılmış, açıq mənbə çalıştırma qabığı.

**Hədəf cihaz:** Samsung Galaxy A06 — Helio G85, Mali-G52 MC2, 4 GB RAM, Android 14, root yox.
**Hədəf:** maksimum əldə edilə bilən FPS (sabit 30 FPS şərt deyil).

## Status

| Mərhələ | Vəziyyət |
|---|---|
| Phase 1 — repo, GitHub Actions debug APK, profillər, runtime manifest | ✅ hazırdır |
| Phase 2 — runtime bundle (proot, FEX/Box86/Box64, Mesa, Wine/Vortek) | ⏳ növbəti |
| Phase 3 — qrafika və performans tuninqi | ⏳ |
| Phase 4–7 — CPU tuning, input UX, paketləmə | ⏳ |

> İndiki APK yalnız **iskelet**-dir: UI, runtime endirici və profil seçimi işləyir, lakin oyunu işlətmək üçün runtime bundle hələ yayımlanmayıb.

## Yanaşma

İki oyun build-i üçün iki profil saxlanılır:

| Profil | Oyun | CPU | Qrafika |
|---|---|---|---|
| `linux-fex` | Ubuntu 32-bit | FEX | Mesa Zink (OpenGL→Vulkan) |
| `linux-box86` | Ubuntu 32-bit | Box86 | Mesa Zink |
| `windows-box64` | Windows 64-bit | Wine64 + Box64 | Vortek + DXVK async |

Root olmadığı üçün hər şey **proot** konteynerində işləyir. Texniki əsaslandırma: [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Build

GitHub Actions avtomatik olaraq hər push-da `assembleDebug` işlədir və APK-nı artifact olaraq yükləyir:
`.github/workflows/build.yml`

Lokal build üçün JDK 17 + Android SDK + Gradle 8.9 tələb olunur:
```bash
gradle assembleDebug
```

## Ölçüm

Phase 0 protokolu və nəticə cədvəli: [`benchmarks/README.md`](benchmarks/README.md).

## Qanuni istifadə

Bu layihə **oyun fayllarını ehtiva etmir** və DRM bypass etmir. İstifadəçi öz qanuni nüsxəsini (GOG, itch.io və ya digər) təmin edir və emulator yalnız çalışdırma mühiti rolunu oynayır. Brütal Legend Double Fine Productions-ın əsəridir.

## Lisenziya

GPL-3.0 — bax [`LICENSE`](LICENSE).
