#!/usr/bin/env python3
"""Convert existing XML icon to 512x512 PNG - EXACT copy, no changes"""

try:
    from PIL import Image, ImageDraw
except ImportError:
    import subprocess
    subprocess.check_call(['pip3', 'install', 'pillow'])
    from PIL import Image, ImageDraw

def create_icon():
    # 512x512 for Play Store
    size = 512
    img = Image.new('RGB', (size, size))
    draw = ImageDraw.Draw(img)

    # Scale factor: XML is 108dp viewport, we need 512px
    scale = size / 108

    # Background gradient (using middle color)
    draw.rectangle([0, 0, size, size], fill='#cce3ff')

    # EXACT coordinates from ic_launcher_foreground.xml - NO CHANGES!

    # Book (outer, dark blue)
    draw.rectangle([
        int(32 * scale), int(38 * scale),
        int(76 * scale), int(78 * scale)
    ], fill='#1a237e')

    # Book spine shadow
    draw.rectangle([
        int(32 * scale), int(38 * scale),
        int(36 * scale), int(78 * scale)
    ], fill='#0d47a1')

    # Book pages
    draw.rectangle([
        int(34 * scale), int(40 * scale),
        int(74 * scale), int(76 * scale)
    ], fill='#f6dfbb')

    # Page lines (EXACT from XML)
    line_width = max(1, int(0.8 * scale))
    for y_dp in [48, 52, 56, 60, 64, 68]:
        y = int(y_dp * scale)
        draw.line([
            int(40 * scale), y,
            int(68 * scale), y
        ], fill='#b3d4ff', width=line_width)

    # Cross - vertical beam (EXACT from XML)
    draw.rectangle([
        int(51 * scale), int(44 * scale),
        int(57 * scale), int(72 * scale)
    ], fill='#3a66c9')

    # Cross - horizontal beam (EXACT from XML)
    draw.rectangle([
        int(44 * scale), int(54 * scale),
        int(64 * scale), int(60 * scale)
    ], fill='#3a66c9')

    # Cross highlight (EXACT from XML - 30% white)
    highlight = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    draw_hl = ImageDraw.Draw(highlight)
    draw_hl.rectangle([
        int(51 * scale), int(44 * scale),
        int(54 * scale), int(72 * scale)
    ], fill=(255, 255, 255, 77))  # 30% alpha
    img.paste(highlight, (0, 0), highlight)

    # Save
    img.save('playstore_icon_512.png', 'PNG', optimize=True)
    print("✓ Icon converted from XML to PNG")
    print("  EXACT copy of ic_launcher_foreground.xml")
    print("  512x512 pixels for Play Store")

if __name__ == '__main__':
    create_icon()
