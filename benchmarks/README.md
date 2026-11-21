# Phase 0 — ölçüm protokolu

Məqsəd: Samsung A06 (Helio G85 / Mali-G52 MC2 / 4 GB / root yox) üzərində hər iki oyun build-inin real performansını ölçmək. Sabit 30 FPS şərt deyil — **maksimum mümkün FPS** qeydə alınır.

## Sınaq matrisası

| ID | Oyun build | CPU tərcüməsi | Qrafika | Profil |
|---|---|---|---|---|
| A1 | Ubuntu 32-bit | FEX | Mesa Zink | `linux-fex` |
| A2 | Ubuntu 32-bit | Box86 (BIGBLOCK=3, STRONGMEM=1) | Mesa Zink | `linux-box86` |
| B1 | Windows 64-bit | Box64 (Performance) | Vortek + DXVK 1.10.3 async | `windows-box64` |

## Sabit ayarlar

- Daxili render: **640×360** (sonradan 480p sınanır)
- Oyun qrafika ayarları: **Low**, kölgələr off
- Ölçüm müddəti: **10 dəqiqə** (son 5 dəqiqə stabil qəbul edilir)
- Hər sınaqdan əvvəl cihazı 5 dəqiqə istirahət etdirin, arxa plan tətbiqlərini bağlayın
- Telefonu şarj cihazına **bağlamayın** (şarj temperaturu artırır)

## Ölçülən göstəricilər

| Göstərici | Mənbə |
|---|---|
| Median FPS | `FPSHOOK_LOG` / `DXVK_HUD=fps` |
| p95 frame-time | fps log-dan hesablanır |
| Yaddaş | `dumpsys meminfo` / `free -m` (proot daxilində) |
| Temperatur | `/sys/class/thermal/thermal_zone*/temp` |
| 10 dəq. sonra FPS düşüşü | son 60 saniyə / ilk 60 saniyə |

## Nəticə cədvəli (doldurulacaq)

| Test | Median FPS | p95 ms | Yaddaş (MB) | Temp (°C) | FPS düşüşü % | Qeyd |
|---|---|---|---|---|---|---|
| A1 | | | | | | |
| A2 | | | | | | |
| B1 | | | | | | |

## Qeydlər

- Nəticələr `results/<tarix>.json` faylına da yazılsın.
- Ekran qaralması/artifact olarsa, qrafika yolunu VirGL və ya LLVMpipe ilə müqayisə edin (LLVMpipe çox yavaşdır, yalnız diaqnostika üçün).
- OOM olarsa, Windows yolunda Wine prefix ölçüsünü azaldın, Linux yolunda rootfs-dan lazımsız paketləri çıxarın.
