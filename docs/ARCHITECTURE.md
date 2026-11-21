# Memarlıq və texniki qərarlar

## Hədəf cihaz və ölçülər

| Parametr | Dəyər |
|---|---|
| Cihaz | Samsung Galaxy A06 (4G) |
| SoC | MediaTek Helio G85 (12 nm) |
| CPU | 2× Cortex-A75 @ 2.0 GHz + 6× Cortex-A55 @ 1.8 GHz |
| GPU | Mali-G52 MC2 (Vulkan 1.1, **Turnip dəstəyi yoxdur**) |
| RAM | 4 GB, root yox |
| Ekran | 720×1600 |
| Android | 14 |
| AnTuTu v10 | ~247 000 |
| 3DMark Wild Life Extreme | ~188 (çox aşağı) |

Oyunun minimum PC tələbi: Core 2 Duo 2.2 GHz + 512 MB GPU. Telefonun x86 tərcüməsi ilə effektiv performansı bundan aşağıdır → yüksək ayarlarda 30 FPS realistik deyil. Hədəf: maksimum əldə edilə bilən FPS.

## Niyə iki oyun yolu?

### Yol A — Ubuntu/Linux build (32-bit)

GOG/itch.io Ubuntu build-i **yalnız 32-bit** ELF-dir. Üstünlüyü: Wine və DirectX tərcüməsi yoxdur.

```
x86-32 ELF → FEX (və ya Box86) → OpenGL 2.1 → Mesa Zink → Vulkan → Mali-G52
           + proot konteyneri (glibc, i386 libs, Xvfb, PulseAudio)
```

- CPU: FEX (32/64-bit dəstək, bir JIT) və ya Box86 (`DYNAREC_BIGBLOCK=3` və s.)
- Qrafika: Zink (OpenGL→Vulkan), ehtiyatda VirGL, LLVMpipe (yalnız diaqnostika)
- Yaddaş: ən az overhead
- Risk: 32-bit tərcümə Box64 qədər yetkin deyil; 2013-cü il portu, gamepad və 60 FPS cap məsələləri

### Yol B — Windows 64-bit build

```
PE64 → Wine64 → Box64 → D3D9 → Vortek / DXVK 1.10.3 async → Vulkan → Mali-G52
```

- Winlator 11.2.0 komponentləri əsasında (Vortek 10.x Mali-də DX9/DX10/11 dəstəyini yaxşılaşdırıb)
- Box64 ən yetkin dynarec-dir (`BIGBLOCK=3`, `STRONGMEM=1`, `FORWARD=1024`)
- Risk: Wine prefix + JIT + DXVK yaddaşı 4 GB-da çox sıxır

## Niyə "telefon Linux'dur" avtomatik üstünlük vermir?

Android yalnız **kernel** olaraq Linux istifadə edir. Userspace bionic libc-dir, glibc/X11/ALSA yoxdur. Ubuntu binarisi birbaşa işləmir; proot konteyneri, i386 kitabxanaları, X server və səs serveri tələb olunur. Üstəlik binari x86 olduqca CPU tərcüməsi də qaçılmazdır.

## Root yox, 4 GB məhdudiyyəti

- `swapon` üçün CAP_SYS_ADMIN tələb olunur → əlavə swap yox, yalnız zRAM
- proot syscall overhead ~5–15% (chroot ilə müqayisədə)
- Windows yolu OOM riski daşıyır → Phase 0-da yaddaş ölçümü vacib
- CPUaffinity (`taskset`) A75 nüvələrinə yönləndirmək üçün istifadə edilir

## Komponent versiyaları (Phase 1-də sabitləndi)

| Komponent | Versiya | Qaynaq |
|---|---|---|
| FEX | FEX-2611.1 | github.com/FEX-Emu/FEX |
| Box64 | v0.4.4 | github.com/ptitSeb/box64 |
| Box86 | 0.3.x | github.com/ptitSeb/box86 |
| Winlator (Wine/Vortek mənbəyi) | 11.2.0 | github.com/brunodev85/winlator |

## Lisenziya

Wine (LGPL), Box64/Box86 (MIT), FEX (MIT), Mesa (MIT), Winlator/Vortek (GPL) qarışığı. Yekun layihə **GPL-3.0** altında açıq saxlanılır — `LICENSE` faylına baxın. Oyun faylları və DRM bypass heç vaxt daxil edilmir.
