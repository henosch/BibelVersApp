#!/usr/bin/env python3
"""Generate 512x512 Play Store icon following 2025 guidelines"""

try:
    from PIL import Image, ImageDraw
except ImportError:
    raise SystemExit("Pillow (PIL) ist nicht installiert. Bitte `pip install pillow` ausführen.")

def create_icon():
    # Create 512x512 image (FULL SQUARE per Play Store guidelines)
    size = 512
    img = Image.new('RGB', (size, size))  # RGB, no transparency
    draw = ImageDraw.Draw(img)

    # Solid background (gradient colors from your app, using middle tone)
    # Play Store will add rounded corners automatically
    bg_color = '#cce3ff'
    draw.rectangle([0, 0, size, size], fill=bg_color)

    # Scale to fill most of the icon (safe zone consideration)
    # Book should be larger and centered
    center = size // 2
    book_width = int(size * 0.65)  # 65% of icon
    book_height = int(size * 0.60)

    # Book position (centered)
    book_x1 = center - book_width // 2
    book_y1 = center - book_height // 2
    book_x2 = book_x1 + book_width
    book_y2 = book_y1 + book_height

    # Book outer (dark blue) - NO SHADOW per guidelines
    draw.rectangle([book_x1, book_y1, book_x2, book_y2], fill='#1a237e')

    # Book spine
    spine_width = int(book_width * 0.08)
    draw.rectangle([book_x1, book_y1, book_x1 + spine_width, book_y2], fill='#0d47a1')

    # Book pages (cream)
    margin = int(book_width * 0.04)
    pages_x1 = book_x1 + margin
    pages_y1 = book_y1 + margin
    pages_x2 = book_x2 - margin
    pages_y2 = book_y2 - margin
    draw.rectangle([pages_x1, pages_y1, pages_x2, pages_y2], fill='#f6dfbb')

    # Page lines
    line_margin = int(book_width * 0.15)
    line_x1 = pages_x1 + line_margin
    line_x2 = pages_x2 - line_margin
    line_spacing = int(book_height * 0.08)
    line_start_y = pages_y1 + int(book_height * 0.15)

    for i in range(7):
        y = line_start_y + i * line_spacing
        if y < pages_y2 - line_margin:
            draw.line([line_x1, y, line_x2, y], fill='#b3d4ff', width=3)

    # Cross dimensions (well-proportioned)
    cross_width = int(book_width * 0.15)
    cross_v_height = int(book_height * 0.55)
    cross_h_width = int(book_width * 0.40)
    cross_h_height = int(book_height * 0.12)

    # Cross vertical beam
    cross_v_x1 = center - cross_width // 2
    cross_v_y1 = pages_y1 + int(book_height * 0.08)
    cross_v_x2 = cross_v_x1 + cross_width
    cross_v_y2 = cross_v_y1 + cross_v_height
    draw.rectangle([cross_v_x1, cross_v_y1, cross_v_x2, cross_v_y2], fill='#3a66c9')

    # Cross horizontal beam
    cross_h_x1 = center - cross_h_width // 2
    cross_h_y1 = center - cross_h_height // 2 - int(book_height * 0.05)
    cross_h_x2 = cross_h_x1 + cross_h_width
    cross_h_y2 = cross_h_y1 + cross_h_height
    draw.rectangle([cross_h_x1, cross_h_y1, cross_h_x2, cross_h_y2], fill='#3a66c9')

    # Save with high quality
    img.save('playstore_icon_512.png', 'PNG', optimize=True)
    print("✓ Play Store icon created: playstore_icon_512.png")
    print("  Following 2025 guidelines:")
    print("  - 512x512 pixels")
    print("  - Full square (no rounded corners)")
    print("  - No shadows")
    print("  - Solid background")

    import os
    size_kb = os.path.getsize('playstore_icon_512.png') / 1024
    print(f"  Size: {size_kb:.1f} KB")

if __name__ == '__main__':
    create_icon()
