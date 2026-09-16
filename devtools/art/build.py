# Copyright (C) 2026 Rusty Shackleford and nfx
# SPDX-License-Identifier: AGPL-3.0-or-later
"""Builds the mod's item/armour art from vanilla pixel templates and authored
cracks/mask details (see SOURCES.md), leaving nfx's model untouched, and sounds
(cut from the CC0 recordings in sounds/src, see sounds/SOURCES.md). Run from
the repo root with `uv run --no-project python devtools/art/build.py [icons] [sounds]`.
Needs ffmpeg for template decoding and sounds. Bit-exact: an unchanged sound is an unchanged file."""

from __future__ import annotations

import struct
import subprocess
import sys
import zlib
from collections.abc import Callable, Sequence
from dataclasses import dataclass
from functools import cache
from math import isfinite
from pathlib import Path
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "src/main/resources/assets/aberrantmobs"

# ---------------------------------------------------------------- icons


type RGBA = tuple[int, int, int, int]
# Mutable only while drawing; every finished asset is an immutable Raster.
type Canvas = list[list[RGBA]]


def write_png(path: Path, w: int, h: int, pixels: Sequence[Sequence[RGBA]]) -> None:
    """requires: dimensions match nonempty valid RGBA rows. effects: writes a deterministic PNG, creating its parent. throws: OSError on I/O failure."""
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *pixels[y][x]) for x in range(w))
        for y in range(h)
    )

    def chunk(kind: bytes, data: bytes) -> bytes:
        return (
            struct.pack(">I", len(data))
            + kind
            + data
            + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
        )

    png = (
        b"\x89PNG\r\n\x1a\n"
        + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
        + chunk(b"IDAT", zlib.compress(raw, 9))
        + chunk(b"IEND", b"")
    )
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


@dataclass(frozen=True)
class Raster:
    """AF: pixels is an RGBA image, indexed [row][column].

    RI: nonempty rectangular rows; four 8-bit channels per pixel; deeply immutable.
    """

    pixels: tuple[tuple[RGBA, ...], ...]

    def __post_init__(self) -> None:
        """Requires valid RGBA rows; raises ValueError for an invalid raster."""
        if (
            not self.pixels
            or not self.pixels[0]
            or any(len(row) != len(self.pixels[0]) for row in self.pixels)
        ):
            raise ValueError("a raster must have nonempty rectangular rows")
        if any(
            len(pixel) != 4
            or any(not isinstance(c, int) or not 0 <= c <= 255 for c in pixel)
            for row in self.pixels
            for pixel in row
        ):
            raise ValueError("RGBA channels must be bytes")

    @property
    def width(self) -> int:
        """Effects: returns the number of columns."""
        return len(self.pixels[0])

    @property
    def height(self) -> int:
        """Effects: returns the number of rows."""
        return len(self.pixels)


def freeze(canvas: Canvas) -> Raster:
    """requires: nonempty rectangular RGBA canvas. effects: copies into an immutable raster. throws: ValueError for invalid pixels."""
    return Raster(tuple(tuple(row) for row in canvas))


@cache
def template(name: str, width: int, height: int) -> Raster:
    """Requires a Minecraft texture path and its dimensions.

    Effects: reads the installed game's template; never edits or vendors originals.
    Throws: RuntimeError if Gradle has not prepared the game's resources, ValueError
    for unexpected dimensions, and the underlying archive/ffmpeg error on failure.
    """
    jars = sorted(
        (ROOT / "build/moddev/artifacts").glob(
            "*client-extra-aka-minecraft-resources.jar"
        )
    )
    if len(jars) != 1:
        raise RuntimeError(
            "run ./gradlew createMinecraftArtifacts first; expected one game resources jar"
        )
    with ZipFile(jars[0]) as jar:
        png = jar.read(f"assets/minecraft/textures/{name}.png")
    raw = subprocess.run(
        [
            "ffmpeg",
            "-v",
            "error",
            "-i",
            "pipe:0",
            "-f",
            "rawvideo",
            "-pix_fmt",
            "rgba",
            "pipe:1",
        ],
        input=png,
        capture_output=True,
        check=True,
    ).stdout
    if len(raw) != width * height * 4:
        raise ValueError(f"unexpected dimensions for {name}")
    rows: Canvas = []
    for y in range(height):
        row: list[RGBA] = []
        for x in range(width):
            i = (y * width + x) * 4
            row.append((raw[i], raw[i + 1], raw[i + 2], raw[i + 3]))
        rows.append(row)
    return freeze(rows)


# Keep the silhouette, alpha and local shade ordering of the vanilla reference.
# The ramp is the model's violet-grey plating, with a small upper-edge highlight.
RAMP: tuple[RGBA, ...] = (
    (21, 17, 25, 255),
    PLATE_DARK,
    PLATE,
    PLATE_LIGHT,
    (118, 104, 132, 255),
)


def luma(pixel: RGBA) -> float:
    """requires: valid RGBA pixel. effects: returns weighted RGB brightness; alpha is ignored. throws: none."""
    return (3 * pixel[0] + 6 * pixel[1] + pixel[2]) / 10


def carapace(source: Raster) -> Raster:
    """requires: source contains an opaque pixel. effects: recolours to the plating ramp, preserving alpha and shade order. throws: ValueError for an entirely transparent source."""
    shades = [luma(p) for row in source.pixels for p in row if p[3]]
    low, high = min(shades), max(shades)
    canvas: Canvas = []
    for row in source.pixels:
        output: list[RGBA] = []
        for p in row:
            if not p[3]:
                output.append(CLEAR)
                continue
            t = (luma(p) - low) / (high - low or 1) * (len(RAMP) - 1)
            i = min(int(t), len(RAMP) - 2)
            f = t - i
            a, b = RAMP[i], RAMP[i + 1]
            output.append(
                (
                    round(a[0] + (b[0] - a[0]) * f),
                    round(a[1] + (b[1] - a[1]) * f),
                    round(a[2] + (b[2] - a[2]) * f),
                    p[3],
                )
            )
        canvas.append(output)
    return freeze(canvas)


def cracked(source: Raster, line: Sequence[tuple[int, int]]) -> Raster:
    """requires: every line coordinate is within source. effects: adds an orange fissure only on opaque pixels. throws: IndexError for coordinates outside the image."""
    canvas = [list(row) for row in source.pixels]
    for i, (x, y) in enumerate(line):
        if canvas[y][x][3]:
            canvas[y][x] = EMBER_HOT if i % 4 == 1 else EMBER
    return freeze(canvas)


def chitin() -> Raster:
    """requires: prepared game resources. effects: returns a layered plate in the vanilla scute family. throws: template archive or decoding errors."""
    return carapace(template("item/armadillo_scute", 16, 16))


def cracked_carapace() -> Raster:
    """requires: prepared game resources. effects: returns a shell plate with an orange seam. throws: template archive or decoding errors."""
    return cracked(
        carapace(template("item/turtle_scute", 16, 16)),
        ((5, 11), (6, 10), (7, 9), (7, 8), (8, 7), (9, 6), (10, 5), (8, 10), (9, 11)),
    )


def stolen_face() -> Raster:
    """requires: nothing. effects: returns a stepped bone mask with sockets and a broken jaw. throws: none."""
    canvas: Canvas = [[CLEAR] * 16 for _ in range(16)]
    widths = {
        2: (5, 10),
        3: (3, 12),
        4: (3, 12),
        5: (2, 12),
        6: (2, 12),
        7: (3, 12),
        8: (3, 12),
        9: (4, 11),
        10: (4, 11),
        11: (5, 10),
        12: (5, 10),
        13: (6, 9),
    }
    for y, (left, right) in widths.items():
        for x in range(left, right + 1):
            edge = x in (left, right) or y in (2, 13)
            canvas[y][x] = SOCKET if edge else BONE_DARK if x >= 10 or y >= 10 else BONE
    for x, y in ((4, 4), (5, 3), (6, 3), (7, 3), (8, 3), (4, 5), (4, 8)):
        canvas[y][x] = (238, 227, 205, 255)
    for x, y in (
        (5, 6),
        (6, 6),
        (9, 6),
        (10, 6),
        (5, 7),
        (6, 7),
        (9, 7),
        (10, 7),
        (7, 9),
        (8, 9),
        (6, 11),
        (8, 11),
        (9, 12),
    ):
        canvas[y][x] = SOCKET
    return freeze(canvas)


def armour_icon(kind: str) -> Raster:
    """requires: kind is helmet, chestplate, leggings or boots; prepared game resources. effects: returns vanilla armour recoloured as chitin. throws: template archive or decoding errors."""
    source = carapace(template(f"item/netherite_{kind}", 16, 16))
    if kind == "chestplate":
        return cracked(source, ((7, 7), (8, 8), (7, 9), (7, 10), (8, 11)))
    return source


def armour_layer(layer: int) -> Raster:
    """requires: layer is 1 or 2; prepared game resources. effects: preserves armour UV coverage and joints, adding the chest seam. throws: template archive or decoding errors."""
    source = carapace(template(f"models/armor/netherite_layer_{layer}", 64, 32))
    if layer == 1:
        return cracked(
            source, ((23, 23), (24, 24), (24, 25), (23, 26), (24, 27), (25, 28))
        )
    return source


ICONS: dict[str, Callable[[], Raster]] = {
    "chitin": chitin,
    "cracked_carapace": cracked_carapace,
    "stolen_face": stolen_face,
    "chitin_helmet": lambda: armour_icon("helmet"),
    "chitin_chestplate": lambda: armour_icon("chestplate"),
    "chitin_leggings": lambda: armour_icon("leggings"),
    "chitin_boots": lambda: armour_icon("boots"),
}
LAYERS: dict[str, Callable[[], Raster]] = {
    "chitin_layer_1": lambda: armour_layer(1),
    "chitin_layer_2": lambda: armour_layer(2),
}

# ---------------------------------------------------------------- sounds

RATE = 44100
SOURCES = Path(__file__).resolve().parent / "sounds" / "src"


def decode(stem: str) -> list[float]:
    """requires: stem names an existing source recording. effects: decodes mono float samples at RATE. throws: subprocess errors for missing or undecodable sources."""
    raw = subprocess.run(
        [
            "ffmpeg",
            "-loglevel",
            "error",
            "-i",
            str(SOURCES / f"{stem}.ogg"),
            "-f",
            "f32le",
            "-ac",
            "1",
            "-ar",
            str(RATE),
            "pipe:1",
        ],
        capture_output=True,
        check=True,
    ).stdout
    return [float(value[0]) for value in struct.iter_unpack("<f", raw)]


@dataclass(frozen=True)
class Take:
    """AF: a source interval, its output position, gain and endpoint fades.

    RI: nonempty stem; finite, nonnegative times and gain; end absent or finite and greater than start.
    """

    stem: str
    start: float
    end: float | None
    at: float
    gain: float
    fade_in: float
    fade_out: float

    def __post_init__(self) -> None:
        """Requires valid cut parameters; raises ValueError otherwise."""
        values = (self.start, self.at, self.gain, self.fade_in, self.fade_out)
        if (
            not self.stem
            or any(not isfinite(v) or v < 0 for v in values)
            or (
                self.end is not None
                and (not isfinite(self.end) or self.end <= self.start)
            )
        ):
            raise ValueError("invalid recording cut")


def take(
    stem: str,
    start: float,
    end: float | None = None,
    at: float = 0.0,
    gain: float = 1.0,
    fade_in: float = 0.005,
    fade_out: float = 0.05,
) -> Take:
    """One cut of a recording: `stem` from `start` to `end` seconds (`None`:
    its end), faded in and out so a cut never clicks, placed `at` seconds
    into the result at `gain`."""
    return Take(stem, start, end, at, gain, fade_in, fade_out)


def cut(stem: str, start: float, end: float | None) -> list[float]:
    """requires: valid source and 0 <= start < end (or end absent), inside its duration. effects: returns the requested sample interval. throws: source decoding errors."""
    samples = decode(stem)
    return samples[
        int(start * RATE) : len(samples)
        if end is None
        else min(len(samples), int(end * RATE))
    ]


def assemble(takes: Sequence[Take], peak: float) -> list[float]:
    """Mixes the takes into one clip normalized to `peak`. Nothing is added:
    no reverb, no tone, no filtering; what is heard is the recordings."""
    cuts: list[tuple[int, list[float]]] = []
    for t in takes:
        samples = cut(t.stem, t.start, t.end)
        n_in, n_out = int(t.fade_in * RATE), int(t.fade_out * RATE)
        for i in range(min(n_in, len(samples))):
            samples[i] *= i / n_in
        for i in range(min(n_out, len(samples))):
            samples[len(samples) - 1 - i] *= i / n_out
        cuts.append((int(t.at * RATE), [c * t.gain for c in samples]))
    out = [0.0] * max(i0 + len(c) for i0, c in cuts)
    for i0, c in cuts:
        for i, x in enumerate(c):
            out[i0 + i] += x
    return normalize(out, peak)


def normalize(samples: Sequence[float], peak: float = 0.95) -> list[float]:
    """requires: nonempty finite samples and 0 <= peak <= 1. effects: scales to peak, preserving silence. throws: ValueError for empty samples."""
    top = max(abs(x) for x in samples) or 1.0
    return [x * peak / top for x in samples]


def write_ogg(path: Path, samples: Sequence[float]) -> None:
    """requires: finite samples. effects: writes bit-exact mono Vorbis, creating the parent. throws: OSError or subprocess errors on failure."""
    path.parent.mkdir(parents=True, exist_ok=True)
    pcm = b"".join(
        struct.pack("<h", int(max(-1.0, min(1.0, s)) * 32767)) for s in samples
    )
    subprocess.run(
        [
            "ffmpeg",
            "-y",
            "-loglevel",
            "error",
            "-f",
            "s16le",
            "-ar",
            str(RATE),
            "-ac",
            "1",
            "-i",
            "pipe:0",
            "-c:a",
            "libvorbis",
            "-q:a",
            "5",
            "-fflags",
            "+bitexact",
            "-flags",
            "+bitexact",
            str(path),
        ],
        input=pcm,
        check=True,
    )


# The shipped sounds: which recording, which seconds of it, chosen from 100 ms
# RMS envelopes. Runtime volumes and frequencies belong to Aberrant and the
# profile; the rare idle breath follows D-0012.
SOUNDS: dict[str, Callable[[], list[float]]] = {
    "skitter1": lambda: assemble(
        [take("266014-skitter-fast-crawling-bug", 0.1, 1.3, fade_out=0.08)], 0.85
    ),
    "skitter2": lambda: assemble(
        [take("651488-skitter-alien-bug-crawl", 0.5, 1.7, fade_out=0.08)], 0.85
    ),
    "dig_loud": lambda: assemble(
        [take("321488-dig-scraping-stone", 0.0, 1.2, fade_out=0.1)], 0.95
    ),
    "dig_quiet": lambda: assemble(
        [take("667284-dig-stone-scrape", 0.0, None, gain=0.5, fade_out=0.1)], 0.5
    ),
    "click": lambda: assemble(
        [take("467700-click-insectoid-monster", 0.25, 0.9, fade_out=0.04)], 0.9
    ),
    "hiss": lambda: assemble(
        [take("553374-hiss-snake", 0.45, 1.8, fade_out=0.15)], 0.9
    ),
    "screech": lambda: assemble(
        [take("807383-screech-monster-a", 0.0, 1.6, fade_out=0.15)], 0.95
    ),
    "grab": lambda: assemble(
        [take("443328-snap-quick-clack", 0.0, None, fade_out=0.02)], 0.95
    ),
    "bite": lambda: assemble(
        [take("355052-crunch-bone", 0.05, 1.5, fade_out=0.1)], 0.95
    ),
    "crack": lambda: assemble(
        [take("150479-egg-crack", 0.0, None, fade_out=0.05)], 0.95
    ),
    "death": lambda: assemble(
        [take("734841-dying-beast", 0.0, None, fade_out=0.4)], 0.95
    ),
    "chitter": lambda: assemble(
        [take("387068-chitter", 10.8, 14.8, fade_in=0.03, fade_out=0.15)], 0.9
    ),
    "breath": lambda: assemble(
        [take("744788-low-rumble", 0.6, 4.8, fade_in=0.12, fade_out=0.35)],
        0.8,
    ),
}


def main(argv: Sequence[str]) -> None:
    """requires: argv is program name followed by icons and/or sounds. effects: regenerates selected assets, both by default. throws: underlying validation, archive or I/O errors."""
    want = set(argv[1:]) or {"icons", "sounds"}
    if "icons" in want:
        for name, draw in ICONS.items():
            write_png(ASSETS / f"textures/item/{name}.png", 16, 16, draw().pixels)
        for name, draw in LAYERS.items():
            write_png(
                ASSETS / f"textures/models/armor/{name}.png", 64, 32, draw().pixels
            )
        print("wrote the icons and the armour layers")
    if "sounds" in want:
        for name, build in SOUNDS.items():
            write_ogg(ASSETS / f"sounds/{name}.ogg", build())
        print("wrote the sounds")


if __name__ == "__main__":
    main(sys.argv)
