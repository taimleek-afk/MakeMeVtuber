<p align="center">
  <img src="imgs/mmvico.png" width="120" alt="MakeMeVtuber Logo">
</p>

<h1 align="center">MakeMeVtuber</h1>

<p align="center">
  A lightweight client-side NeoForge mod for Minecraft 1.21.x<br>
  Transform your player into a VTuber-style avatar with a single command.
</p>

<p align="center">
  <img src="https://img.shields.io/badge/Minecraft-1.21.x-green?style=flat-square" alt="Minecraft">
  <img src="https://img.shields.io/badge/Loader-NeoForge-orange?style=flat-square" alt="NeoForge">
  <img src="https://img.shields.io/badge/License-MIT-yellow?style=flat-square" alt="License">
  <img src="https://img.shields.io/badge/Version-2.1.1-blue?style=flat-square" alt="Version">
</p>

<p align="center">
  <b>Looking for Fabric?</b> → <a href="https://github.com/taimleek-afk/MakeMeVtuber/tree/Fabric-1.21.3-1.21.4">Fabric-1.21.3-1.21.4 branch</a>
</p>

---

## Preview

| In-game Render | Settings Window |
|:-:|:-:|
| ![In-game](https://media4.giphy.com/media/v1.Y2lkPTc5MGI3NjExY3FqMHI1OWpqaGF3ZzZ3YzlzNmlqZjA0N2l2d2NwZTJlZ3RheHNpbiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/iqgYl4KVZVt17zulbd/giphy.gif) | ![Settings](https://media0.giphy.com/media/v1.Y2lkPTc5MGI3NjExNng1M3F6ZjZqNzdrc29qMjdidXcwaG5ldHU4a3dtYWh2dzkxZHRnYiZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9Zw/dTuuWhgVob9D2bU9oI/giphy.gif) |

---

## Features

- **VTuber avatar** — renders your player model in a separate window with chromakey background
- **Microphone lip-sync** — mouth opens based on mic volume in real-time
- **Spring camera** — smooth camera attached to the face, follows head movement
- **Chromakey options** — green, blue, or transparent background
- **Customizable mouth** — position, size, threshold, intensity sliders
- **Dither fade** — body parts dissolve with stipple pattern when looking down
- **Lightweight** — runs in a separate thread, minimal impact on game performance

---

## Requirements

| | Version |
|---|---|
| Minecraft | 1.21.3 — 1.21.4 |
| NeoForge | ≥ 21.3 |
| Java | ≥ 21 |

---

## Installation

1. Install [NeoForge](https://neoforged.net/)
2. Place `makemevtuber-2.1.1.jar` into the `mods` folder
3. Launch the game

---

## Download

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/taimleek-afk/MakeMeVtuber/releases">
        <img src="https://img.shields.io/github/v/release/taimleek-afk/MakeMeVtuber?label=GitHub%20Releases&style=for-the-badge&logo=github&color=181717" alt="GitHub Releases">
      </a>
    </td>
    <td align="center">
      <a href="https://www.curseforge.com/minecraft/mc-mods/makemevtuber">
        <img src="https://img.shields.io/badge/CurseForge-Download-F16436?style=for-the-badge&logo=curseforge&logoColor=white" alt="CurseForge">
      </a>
    </td>
  </tr>
</table>

---

## Usage

Open chat and type:

```
/mmv
```

This opens:
- **Render window** — your VTuber avatar with chromakey background
- **Settings window** — configure background, microphone, and mouth parameters

---

## Branches

| Branch | Loader | Minecraft |
|--------|--------|-----------|
| `NeoForge-1.21.3-1.21.4` | NeoForge | 1.21.3 — 1.21.4 |
| `Fabric-1.21.3-1.21.4` | Fabric | 1.21.3 — 1.21.4 |
| `NeoForge-1.21.1` | NeoForge | 1.21.1 |
| `Fabric-1.21.1` | Fabric | 1.21.1 |

---

## Author

**taimleek-afk**

---

## License

[MIT License](LICENSE)
