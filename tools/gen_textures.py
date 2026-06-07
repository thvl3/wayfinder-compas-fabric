#!/usr/bin/env python3
"""Generate the Wayfinder Compass item texture and mod icon (no external deps)."""
import math
import struct
import zlib


def write_png(path, width, height, pixels):
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter type 0 (none) per scanline
        for x in range(width):
            r, g, b, a = pixels[y * width + x]
            raw += bytes((r, g, b, a))

    def chunk(typ, data):
        body = typ + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    idat = zlib.compress(bytes(raw), 9)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", idat) + chunk(b"IEND", b""))


def make_compass(size):
    """A gold-ringed compass with a dark face and a glowing green needle."""
    cx = cy = (size - 1) / 2.0
    outer = size * 0.46
    ring_inner = size * 0.34
    face = size * 0.30
    pixels = []
    for y in range(size):
        for x in range(size):
            dx, dy = x - cx, y - cy
            dist = math.hypot(dx, dy)

            if dist > outer:
                pixels.append((0, 0, 0, 0))  # transparent
                continue

            if dist > ring_inner:
                # Gold ring with a little shading.
                shade = max(0.0, min(1.0, (cy - dy) / size + 0.6))
                pixels.append((int(190 * shade + 40), int(150 * shade + 30), 40, 255))
                continue

            # Dark compass face.
            r, g, b = 32, 36, 48

            # Glowing green needle pointing "north" (up), red tail pointing down.
            # Needle is a thin diamond along the vertical axis.
            needle_half_w = face * (1.0 - abs(dy) / max(face, 1)) * 0.45
            if abs(dx) <= needle_half_w and abs(dy) <= face:
                if dy <= 0:
                    r, g, b = 90, 255, 120   # north = green glow
                else:
                    r, g, b = 220, 70, 70     # south = red

            pixels.append((r, g, b, 255))
    return pixels


if __name__ == "__main__":
    write_png(
        "src/main/resources/assets/wayfinder/textures/item/wayfinder_compass.png",
        16, 16, make_compass(16),
    )
    write_png(
        "src/main/resources/assets/wayfinder/icon.png",
        64, 64, make_compass(64),
    )
    print("Wrote item texture (16x16) and mod icon (64x64).")
