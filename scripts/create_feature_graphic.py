#!/usr/bin/env python3
"""Create 1024x500 Feature Graphic for Google Play Store"""

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError:
    import subprocess
    subprocess.check_call(['pip3', 'install', 'pillow'])
    from PIL import Image, ImageDraw, ImageFont

def create_feature_graphic():
    # Play Store Feature Graphic size
    width = 1024
    height = 500

    img = Image.new('RGB', (width, height))
    draw = ImageDraw.Draw(img)

    # Background - gradient like app (light blue)
    for y in range(height):
        # Gradient from #e6f2ff to #b3d4ff
        r = int(230 - (230 - 179) * y / height)
        g = int(242 - (242 - 212) * y / height)
        b = int(255 - (255 - 255) * y / height)
        draw.line([(0, y), (width, y)], fill=(r, g, b))

    # App Title
    try:
        title_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 72)
        subtitle_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 36)
        text_font = ImageFont.truetype("/System/Library/Fonts/Helvetica.ttc", 28)
    except:
        title_font = ImageFont.load_default()
        subtitle_font = ImageFont.load_default()
        text_font = ImageFont.load_default()

    # Title
    title = "BibelVers"
    title_bbox = draw.textbbox((0, 0), title, font=title_font)
    title_width = title_bbox[2] - title_bbox[0]
    title_x = (width - title_width) // 2

    # Draw title with shadow
    draw.text((title_x + 3, 53), title, fill='#1a237e', font=title_font)
    draw.text((title_x, 50), title, fill='#3a66c9', font=title_font)

    # Subtitle
    subtitle = "Täglicher Bibelvers & Kotel Stream"
    subtitle_bbox = draw.textbbox((0, 0), subtitle, font=subtitle_font)
    subtitle_width = subtitle_bbox[2] - subtitle_bbox[0]
    subtitle_x = (width - subtitle_width) // 2
    draw.text((subtitle_x, 140), subtitle, fill='#1a237e', font=subtitle_font)

    # Book with Cross (left side, smaller)
    book_size = 180
    book_x = 80
    book_y = height - book_size - 60

    # Book
    draw.rectangle([book_x, book_y, book_x + book_size, book_y + book_size],
                   fill='#1a237e', outline='#0d47a1', width=3)

    # Pages
    margin = int(book_size * 0.05)
    draw.rectangle([book_x + margin, book_y + margin,
                   book_x + book_size - margin, book_y + book_size - margin],
                   fill='#f6dfbb')

    # Cross on book
    cross_width = int(book_size * 0.12)
    cross_v_height = int(book_size * 0.50)
    cross_h_width = int(book_size * 0.35)
    cross_h_height = int(book_size * 0.10)

    center_x = book_x + book_size // 2
    center_y = book_y + book_size // 2

    # Vertical beam
    draw.rectangle([center_x - cross_width // 2, center_y - cross_v_height // 2,
                   center_x + cross_width // 2, center_y + cross_v_height // 2],
                   fill='#3a66c9')

    # Horizontal beam (upper third)
    draw.rectangle([center_x - cross_h_width // 2, center_y - cross_v_height // 2 + int(cross_v_height * 0.25),
                   center_x + cross_h_width // 2, center_y - cross_v_height // 2 + int(cross_v_height * 0.25) + cross_h_height],
                   fill='#3a66c9')

    # Kotel Wall (right side)
    wall_x = width - 320
    wall_y = height - 200
    wall_width = 300
    wall_height = 180

    # Wall bricks pattern
    brick_color = '#f6dfbb'
    mortar_color = '#d4b896'

    brick_h = 30
    brick_w = 60

    for row in range(int(wall_height / brick_h)):
        y = wall_y + row * brick_h
        offset = (brick_w // 2) if row % 2 else 0
        for col in range(int((wall_width + brick_w) / brick_w)):
            x = wall_x + col * brick_w - offset
            if x < wall_x + wall_width and x >= wall_x - brick_w:
                draw.rectangle([x, y, x + brick_w - 2, y + brick_h - 2],
                             fill=brick_color, outline=mortar_color)

    # White cross on wall
    cross_wall_x = wall_x + wall_width // 2
    cross_wall_y = wall_y + wall_height // 2
    cross_wall_width = 15
    cross_wall_v_height = 100
    cross_wall_h_width = 70
    cross_wall_h_height = 15

    # Vertical
    draw.rectangle([cross_wall_x - cross_wall_width // 2, cross_wall_y - cross_wall_v_height // 2,
                   cross_wall_x + cross_wall_width // 2, cross_wall_y + cross_wall_v_height // 2],
                   fill='white')

    # Horizontal
    draw.rectangle([cross_wall_x - cross_wall_h_width // 2,
                   cross_wall_y - cross_wall_v_height // 2 + int(cross_wall_v_height * 0.25),
                   cross_wall_x + cross_wall_h_width // 2,
                   cross_wall_y - cross_wall_v_height // 2 + int(cross_wall_v_height * 0.25) + cross_wall_h_height],
                   fill='white')

    # Features text (center)
    features = [
        "✓ Täglicher Bibelvers",
        "✓ Kotel Livestreams",
        "✓ Keine Werbung oder Tracker",
        "✓ Kostenlos"
    ]

    feature_y = book_y - 40
    feature_x = book_x + book_size + 40
    for feature in features:
        draw.text((feature_x, feature_y), feature, fill='#1a237e', font=text_font)
        feature_y += 40

    # Save
    img.save('playstore_feature_graphic.png', 'PNG', optimize=True)
    print("✓ Feature Graphic created: playstore_feature_graphic.png")
    print("  Size: 1024x500 pixels")

    import os
    size_kb = os.path.getsize('playstore_feature_graphic.png') / 1024
    print(f"  File size: {size_kb:.1f} KB")

if __name__ == '__main__':
    create_feature_graphic()
