"""
File path and name: tools/dev/make_store_logos.py
Modified On Timestamp: 2026-10-10 @ 02:26 EDT
Created On Timestamp: 2026-10-10 @ 02:26 EDT
File Description: Draws the three Microsoft Store logos (2:3 poster art, 1:1 box art, 16:9 super hero art, 1:1 app tile icons of 300, 150 and 71 px).
Uses: Pillow; branding/logo/Rann_Roost_Kite_Left_Facing.png and branding/msix/Assets (tile colour and icon).
Used By: the owner, before uploading the Store logos in Partner Center (docs/store/microsoft-store.md).
Purpose: Keep the Store logos reproducible from the logo files instead of hand-edited.

Usage: python tools/dev/make_store_logos.py
Writes branding/store/: poster-1440x2160.png, box-2160x2160.png, hero-3840x2160.png, hero-1920x1080.png,
tile-300x300.png, tile-150x150.png, tile-71x71.png.
The logo keeps to the top two-thirds of the poster and box art: the Store may lay text over the bottom third.
The super hero art shows the kite alone (the Store writes the title over it), lettering removed from the logo.
"""
import os

from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "..")
LOGO = os.path.join(ROOT, "branding", "logo", "Rann_Roost_Kite_Left_Facing.png")
TILE = os.path.join(ROOT, "branding", "msix", "Assets", "LargeTile.scale-400.png")
ICON = os.path.join(ROOT, "branding", "msix", "Assets", "Square150x150Logo.scale-400.png")
OUT = os.path.join(ROOT, "branding", "store")
# Where the black words RANN and ROOST sit in the logo file (pixels), to leave the kite alone.
LETTERING = [(530, 320, 1490, 570), (900, 1230, 2360, 1530)]


def luminance(rgb):
    def channel(c):
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (channel(c) for c in rgb[:3])
    return 0.2126 * r + 0.7152 * g + 0.0722 * b


def art(width, height, logo, sky, logo_width):
    canvas = Image.new("RGBA", (width, height), sky)
    scaled = logo.resize((logo_width, round(logo.height * logo_width / logo.width)), Image.LANCZOS)
    top_two_thirds = height * 2 // 3
    x = (width - scaled.width) // 2
    y = (top_two_thirds - scaled.height) // 2
    canvas.alpha_composite(scaled, (x, y))
    return canvas.convert("RGB")


def kite_alone(logo):
    kite = logo.copy()
    px = kite.load()
    for x0, y0, x1, y1 in LETTERING:
        for y in range(y0, y1):
            for x in range(x0, x1):
                r, g, b, a = px[x, y]
                if a and max(r, g, b) < 90 and max(r, g, b) - min(r, g, b) < 25:
                    px[x, y] = (0, 0, 0, 0)
    return kite.crop(kite.getbbox())


def main():
    os.makedirs(OUT, exist_ok=True)
    sky = Image.open(TILE).convert("RGBA").getpixel((0, 0))
    logo = Image.open(LOGO).convert("RGBA")
    kite = kite_alone(logo)
    logo = logo.crop(logo.getbbox())
    text = (0, 0, 0)  # the logo's lettering is black
    contrast = (luminance(sky) + 0.05) / (luminance(text) + 0.05)
    print(f"sky #{sky[0]:02X}{sky[1]:02X}{sky[2]:02X}, black lettering contrast {contrast:.2f}:1 (needs 4.5:1)")
    art(1440, 2160, logo, sky, 1300).save(os.path.join(OUT, "poster-1440x2160.png"))
    art(2160, 2160, logo, sky, 1900).save(os.path.join(OUT, "box-2160x2160.png"))
    hero = art(3840, 2160, kite, sky, 2300)
    hero.save(os.path.join(OUT, "hero-3840x2160.png"))
    hero.resize((1920, 1080), Image.LANCZOS).save(os.path.join(OUT, "hero-1920x1080.png"))
    icon = Image.open(ICON).convert("RGB")
    for size in (300, 150, 71):
        icon.resize((size, size), Image.LANCZOS).save(os.path.join(OUT, f"tile-{size}x{size}.png"))
    print("written to", os.path.normpath(OUT))


if __name__ == "__main__":
    main()
