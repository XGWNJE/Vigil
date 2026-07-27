from PIL import Image, ImageDraw, ImageFont

BG = (10, 10, 11)
ACID = (228, 255, 84)
FONT_PATH = 'C:/Users/Administrator/.agents/skills/skill-creator/skills/canvas-design/canvas-fonts/GeistMono-Regular.ttf'

def draw_bell(draw, cx, cy, scale, color, width):
    """Draw a minimalist bell outline centered at cx,cy with given scale."""
    # Bell proportions
    top_r = scale * 0.26
    body_w = scale * 0.50
    body_h = scale * 0.46
    arch_top = cy - body_h * 0.35
    
    # Top arch
    arch_rect = [cx - top_r, arch_top - top_r, cx + top_r, arch_top + top_r]
    draw.arc(arch_rect, start=0, end=180, fill=color, width=width)
    
    # Left side
    l_top = (cx - top_r, arch_top)
    l_bottom = (cx - body_w//2, cy + body_h//2)
    draw.line([l_top, l_bottom], fill=color, width=width)
    
    # Right side
    r_top = (cx + top_r, arch_top)
    r_bottom = (cx + body_w//2, cy + body_h//2)
    draw.line([r_top, r_bottom], fill=color, width=width)
    
    # Bottom bar
    bottom_y = cy + body_h//2
    bar_extend = scale * 0.04
    draw.line([(cx - body_w//2 - bar_extend, bottom_y), (cx + body_w//2 + bar_extend, bottom_y)], fill=color, width=width)
    
    # Clapper
    clap_r = scale // 24
    draw.ellipse([cx-clap_r, bottom_y, cx+clap_r, bottom_y + clap_r*2], fill=color)

def create_icon(size=1024, label=False):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    
    # Outer rounded square border (acid lime hairline)
    pad = size // 12
    corner_r = size // 8
    border_w = max(1, size // 80)
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=corner_r, fill=BG, outline=ACID, width=border_w)
    
    # Bell centered, slightly above middle if label exists
    cy = size * 0.47 if label else size // 2
    draw_bell(draw, size//2, cy, size*0.55, ACID, size//26)
    
    if label:
        try:
            font = ImageFont.truetype(FONT_PATH, size//14)
        except:
            font = ImageFont.load_default()
        text = "VIGIL_"
        bbox = draw.textbbox((0,0), text, font=font)
        tw = bbox[2] - bbox[0]
        draw.text(((size-tw)//2, size*0.74), text, font=font, fill=ACID)
    
    return img

# Generate versions
create_icon(1024, label=False).save('D:/ObjectCode/Vigil/design/icons/vigil_icon_final.png')
create_icon(1024, label=True).save('D:/ObjectCode/Vigil/design/icons/vigil_icon_with_text.png')
create_icon(512, label=False).save('D:/ObjectCode/Vigil/design/icons/vigil_icon_512.png')
print('final icons generated')
