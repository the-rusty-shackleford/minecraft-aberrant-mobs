"""Builds what the mod ships that is not the friend's model: the item icons
(drawn here, pixel by pixel, so a change is a diff of numbers) and the sounds
(cut from the CC0 recordings in sounds/src, see sounds/SOURCES.md). Run from
the repo root with `uv run --no-project python devtools/art/build.py [icons] [sounds]`.
Needs ffmpeg for the sounds. Bit-exact: an unchanged sound is an unchanged file."""
from __future__ import annotations

import struct
import subprocess
import sys
import zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/aberrantmobs"

# ---------------------------------------------------------------- icons


def write_png(path: Path, w: int, h: int, pixels) -> None:
    """Writes 8-bit RGBA; `pixels[y][x]` is (r, g, b, a)."""
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(w)) for y in range(h))

    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(png)


CLEAR = (0, 0, 0, 0)
# The carapace's colours, read off nfx's texture: a dark violet-grey plate with a paler edge.
PLATE = (58, 46, 66, 255)
PLATE_LIGHT = (92, 78, 104, 255)
PLATE_DARK = (34, 26, 40, 255)
EMBER = (255, 120, 40, 255)
EMBER_HOT = (255, 200, 90, 255)
BONE = (214, 200, 176, 255)
BONE_DARK = (150, 136, 112, 255)
SOCKET = (24, 18, 28, 255)


def blank():
    return [[CLEAR for _ in range(16)] for _ in range(16)]


def plate(px, cracked: bool):
    """A curved plate of chitin: an oval with a lit upper edge and a dark lower one."""
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 6.5, (y - 7.5) / 5.5
            r = dx * dx + dy * dy
            if r <= 1.0:
                px[y][x] = PLATE_LIGHT if dy < -0.55 or (dx < -0.6 and dy < 0) else PLATE_DARK if dy > 0.6 else PLATE
    if cracked:
        for x, y in [(4, 9), (5, 8), (6, 8), (7, 7), (8, 7), (9, 6), (10, 5), (11, 4), (7, 9), (8, 10), (6, 6)]:
            px[y][x] = EMBER
        for x, y in [(7, 8), (8, 8), (9, 7)]:
            px[y][x] = EMBER_HOT
    return px


def chitin():
    return plate(blank(), False)


def cracked_carapace():
    return plate(blank(), True)


def stolen_face():
    """A pale mask: an oval of bone with two dark eye holes and a slit of a mouth, a socket's rim behind."""
    px = blank()
    for y in range(16):
        for x in range(16):
            dx, dy = (x - 7.5) / 5.0, (y - 7.5) / 6.5
            r = dx * dx + dy * dy
            if r <= 1.25:
                px[y][x] = SOCKET
            if r <= 1.0:
                px[y][x] = BONE_DARK if dy > 0.5 or dx > 0.55 else BONE
    for x, y in [(5, 6), (6, 6), (9, 6), (10, 6), (5, 7), (10, 7)]:
        px[y][x] = SOCKET
    for x in range(6, 10):
        px[11][x] = SOCKET
    px[12][7] = SOCKET
    return px


def armour_icon(kind: str):
    """A piece of chitin armour as an icon: the plate's colours in the piece's silhouette."""
    px = blank()
    shape = {
        "helmet": [(4, 3, 11, 3), (3, 4, 12, 8), (3, 9, 5, 11), (10, 9, 12, 11)],
        "chestplate": [(2, 3, 5, 5), (10, 3, 13, 5), (3, 5, 12, 13), (5, 2, 10, 3)],
        "leggings": [(3, 2, 12, 5), (3, 5, 7, 13), (8, 5, 12, 13)],
        "boots": [(3, 6, 6, 12), (9, 6, 12, 12), (2, 12, 7, 13), (8, 12, 13, 13)],
    }[kind]
    for x0, y0, x1, y1 in shape:
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                px[y][x] = PLATE
    for x0, y0, x1, y1 in shape:
        for x in range(x0, x1 + 1):
            px[y0][x] = PLATE_LIGHT
            px[y1][x] = PLATE_DARK
    if kind == "chestplate":
        for x, y in [(7, 8), (8, 8), (7, 9), (8, 9)]:
            px[y][x] = EMBER
    return px


ICONS = {"chitin": chitin, "cracked_carapace": cracked_carapace, "stolen_face": stolen_face,
         "chitin_helmet": lambda: armour_icon("helmet"), "chitin_chestplate": lambda: armour_icon("chestplate"),
         "chitin_leggings": lambda: armour_icon("leggings"), "chitin_boots": lambda: armour_icon("boots")}


def armour_layer(layer: int):
    """A 64 by 32 armour layer in the game's layout: layer 1 carries the helmet, the chest and the boots,
    layer 2 the leggings, each box's faces filled with the plate's colours, a lit top row and a dark bottom
    row per box so the plating reads as plates. Transparent elsewhere."""
    px = [[CLEAR for _ in range(64)] for _ in range(32)]

    def box(u, v, w, h, d):
        # The game's box UV: top/bottom at (u+d, v), sides in a strip at (u, v+d) of height h.
        for y in range(v, v + d):
            for x in range(u + d, u + d + 2 * w):
                px[y][x] = PLATE_LIGHT if x < u + d + w else PLATE_DARK
        for y in range(v + d, v + d + h):
            for x in range(u, u + 2 * d + 2 * w):
                px[y][x] = PLATE_LIGHT if y == v + d else PLATE_DARK if y == v + d + h - 1 else PLATE

    if layer == 1:
        box(0, 0, 8, 8, 8)      # the head
        box(16, 16, 8, 12, 4)   # the body
        box(40, 16, 4, 12, 4)   # the right arm
        box(0, 16, 4, 12, 4)    # the right leg (the boots draw its lower part)
    else:
        box(16, 16, 8, 12, 4)   # the body (the belt)
        box(0, 16, 4, 12, 4)    # the legs
    return px


LAYERS = {"chitin_layer_1": lambda: armour_layer(1), "chitin_layer_2": lambda: armour_layer(2)}

# ---------------------------------------------------------------- sounds

RATE = 44100
SOURCES = Path(__file__).resolve().parent / "sounds" / "src"


def decode(stem):
    """Reads sounds/src/<stem>.ogg through ffmpeg as mono float samples at RATE."""
    raw = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(SOURCES / f"{stem}.ogg"), "-f", "f32le", "-ac", "1",
                          "-ar", str(RATE), "pipe:1"], capture_output=True, check=True).stdout
    return list(struct.unpack(f"<{len(raw) // 4}f", raw))


def take(stem, start, end=None, at=0.0, gain=1.0, fade_in=0.005, fade_out=0.05):
    """One cut of a recording: `stem` from `start` to `end` seconds (`None`:
    its end), faded in and out so a cut never clicks, placed `at` seconds
    into the result at `gain`."""
    return (stem, start, end, at, gain, fade_in, fade_out)


def cut(stem, start, end):
    samples = decode(stem)
    return samples[int(start * RATE):len(samples) if end is None else min(len(samples), int(end * RATE))]


def assemble(takes, peak):
    """Mixes the takes into one clip normalized to `peak`. Nothing is added:
    no reverb, no tone, no filtering; what is heard is the recordings."""
    cuts = []
    for stem, start, end, at, gain, fade_in, fade_out in takes:
        samples = cut(stem, start, end)
        n_in, n_out = int(fade_in * RATE), int(fade_out * RATE)
        for i in range(min(n_in, len(samples))):
            samples[i] *= i / n_in
        for i in range(min(n_out, len(samples))):
            samples[len(samples) - 1 - i] *= i / n_out
        cuts.append((int(at * RATE), [c * gain for c in samples]))
    out = [0.0] * max(i0 + len(c) for i0, c in cuts)
    for i0, c in cuts:
        for i, x in enumerate(c):
            out[i0 + i] += x
    return normalize(out, peak)


def normalize(samples, peak=0.95):
    top = max(abs(x) for x in samples) or 1.0
    return [x * peak / top for x in samples]


def write_ogg(path: Path, samples) -> None:
    """Writes 16-bit mono PCM through ffmpeg into Ogg Vorbis, bit-exact."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = b"".join(struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples)
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-f", "s16le", "-ar", str(RATE), "-ac", "1", "-i", "pipe:0",
                    "-c:a", "libvorbis", "-q:a", "5", "-fflags", "+bitexact", "-flags", "+bitexact", str(path)], input=pcm, check=True)


# The shipped sounds: which recording, which seconds of it, chosen from 100 ms
# RMS envelopes. In-game volumes: skitter 0.35 stalking / 1.0 hunting, dig_quiet
# 0.3, dig_loud 1.0, click 0.8, hiss 0.9, screech 1.2, grab 1.0, bite 1.0,
# crack 1.0, death 1.0, breath 0.4.
SOUNDS = {
    "skitter1": lambda: assemble([take("266014-skitter-fast-crawling-bug", 0.1, 1.3, fade_out=0.08)], 0.85),
    "skitter2": lambda: assemble([take("651488-skitter-alien-bug-crawl", 0.5, 1.7, fade_out=0.08)], 0.85),
    "dig_loud": lambda: assemble([take("321488-dig-scraping-stone", 0.0, 1.2, fade_out=0.1)], 0.95),
    "dig_quiet": lambda: assemble([take("667284-dig-stone-scrape", 0.0, None, gain=0.5, fade_out=0.1)], 0.5),
    "click": lambda: assemble([take("467700-click-insectoid-monster", 0.25, 0.9, fade_out=0.04)], 0.9),
    "hiss": lambda: assemble([take("553374-hiss-snake", 0.45, 1.8, fade_out=0.15)], 0.9),
    "screech": lambda: assemble([take("807383-screech-monster-a", 0.0, 1.6, fade_out=0.15)], 0.95),
    "grab": lambda: assemble([take("443328-snap-quick-clack", 0.0, None, fade_out=0.02)], 0.95),
    "bite": lambda: assemble([take("355052-crunch-bone", 0.05, 1.5, fade_out=0.1)], 0.95),
    "crack": lambda: assemble([take("150479-egg-crack", 0.0, None, fade_out=0.05)], 0.95),
    "death": lambda: assemble([take("734841-dying-beast", 0.0, None, fade_out=0.4)], 0.95),
    "breath": lambda: assemble([take("844096-heavy-breathing-beast", 1.8, 3.3, fade_in=0.05, fade_out=0.2)], 0.8),
}


def main(argv) -> None:
    want = set(argv[1:]) or {"icons", "sounds"}
    if "icons" in want:
        for name, draw in ICONS.items():
            write_png(ASSETS / f"textures/item/{name}.png", 16, 16, draw())
        for name, draw in LAYERS.items():
            write_png(ASSETS / f"textures/models/armor/{name}.png", 64, 32, draw())
        print("wrote the icons and the armour layers")
    if "sounds" in want:
        for name, build in SOUNDS.items():
            write_ogg(ASSETS / f"sounds/{name}.ogg", build())
        print("wrote the sounds")


if __name__ == "__main__":
    main(sys.argv)
