from PIL import Image, ImageDraw

BG = (10, 10, 11)      # #0A0A0B
ACID = (228, 255, 84)  # #E4FF54

def icon_bell2(size=512):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    pad = size // 12
    r = size // 8
    # outer rounded square border
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=r, fill=BG, outline=ACID, width=size//64)
    
    cx, cy = size//2, size//2
    # bell parameters
    top_r = size * 0.22
    body_w = size * 0.42
    body_h = size * 0.40
    line_w = size // 22
    
    # top arch
    arch_top = cy - body_h * 0.35
    arch_rect = [cx - top_r, arch_top - top_r, cx + top_r, arch_top + top_r]
    draw.arc(arch_rect, start=0, end=180, fill=ACID, width=line_w)
    
    # left side
    l_top = (cx - top_r, arch_top)
    l_bottom = (cx - body_w//2, cy + body_h//2)
    draw.line([l_top, l_bottom], fill=ACID, width=line_w)
    
    # right side
    r_top = (cx + top_r, arch_top)
    r_bottom = (cx + body_w//2, cy + body_h//2)
    draw.line([r_top, r_bottom], fill=ACID, width=line_w)
    
    # bottom bar
    bottom_y = cy + body_h//2
    draw.line([(cx - body_w//2 - size*0.03, bottom_y), (cx + body_w//2 + size*0.03, bottom_y)], fill=ACID, width=line_w)
    
    # clapper
    clap_r = size // 26
    draw.ellipse([cx-clap_r, bottom_y, cx+clap_r, bottom_y+clap_r*2], fill=ACID)
    
    return img

def icon_bell_solid(size=512):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    pad = size // 12
    r = size // 8
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=r, fill=ACID, outline=ACID, width=size//64)
    
    cx, cy = size//2, size//2
    # draw bell in black
    top_r = size * 0.20
    body_w = size * 0.38
    body_h = size * 0.36
    line_w = size // 18
    
    arch_top = cy - body_h * 0.35
    arch_rect = [cx - top_r, arch_top - top_r, cx + top_r, arch_top + top_r]
    draw.arc(arch_rect, start=0, end=180, fill=(0,0,0), width=line_w)
    
    l_top = (cx - top_r, arch_top)
    l_bottom = (cx - body_w//2, cy + body_h//2)
    draw.line([l_top, l_bottom], fill=(0,0,0), width=line_w)
    
    r_top = (cx + top_r, arch_top)
    r_bottom = (cx + body_w//2, cy + body_h//2)
    draw.line([r_top, r_bottom], fill=(0,0,0), width=line_w)
    
    bottom_y = cy + body_h//2
    draw.line([(cx - body_w//2 - size*0.03, bottom_y), (cx + body_w//2 + size*0.03, bottom_y)], fill=(0,0,0), width=line_w)
    
    clap_r = size // 28
    draw.ellipse([cx-clap_r, bottom_y, cx+clap_r, bottom_y+clap_r*2], fill=(0,0,0))
    
    return img

icon_bell2().save('D:/ObjectCode/Vigil/design/icons/icon_bell2.png')
icon_bell_solid().save('D:/ObjectCode/Vigil/design/icons/icon_bell_solid.png')
print('generated v2')
