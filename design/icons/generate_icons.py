from PIL import Image, ImageDraw, ImageFont
import math

BG = (10, 10, 11)      # #0A0A0B
ACID = (228, 255, 84)  # #E4FF54
INK = (234, 234, 231)  # #EAEAE7

def rounded_rect(draw, xy, radius, fill):
    x0, y0, x1, y1 = xy
    draw.rounded_rectangle(xy, radius=radius, fill=fill)

def icon_bell(size=512):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    pad = size // 12
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=size//8, fill=BG, outline=ACID, width=size//64)
    
    # bell shape
    cx, cy = size//2, size//2
    s = size * 0.55
    # bell body
    bell_w, bell_h = s, s * 1.1
    x0, y0 = cx - bell_w//2, cy - bell_h//2 + size//32
    x1, y1 = x0 + bell_w, y0 + bell_h
    # draw arc top
    draw.arc([x0, y0 - bell_h//6, x1, y0 + bell_h//3], start=0, end=180, fill=ACID, width=size//32)
    # sides
    draw.line([(x0, y0 + bell_h//12), (x0 - bell_w//8, y1)], fill=ACID, width=size//32)
    draw.line([(x1, y0 + bell_h//12), (x1 + bell_w//8, y1)], fill=ACID, width=size//32)
    # bottom bar
    draw.line([(x0 - bell_w//8, y1), (x1 + bell_w//8, y1)], fill=ACID, width=size//32)
    # clapper
    r = size // 24
    draw.ellipse([cx-r, y1-r//2, cx+r, y1+r*3//2], fill=ACID)
    return img

def icon_v(size=512):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    pad = size // 12
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=size//8, fill=BG, outline=ACID, width=size//64)
    
    s = size * 0.5
    cx, cy = size//2, size//2
    # V shape
    pts = [
        (cx - s//2, cy - s//2),
        (cx, cy + s//2),
        (cx + s//2, cy - s//2),
    ]
    draw.line([pts[0], pts[1]], fill=ACID, width=size//18)
    draw.line([pts[1], pts[2]], fill=ACID, width=size//18)
    # underscore dot
    draw.ellipse([cx + s//2 - size//28, cy + s//2 - size//28, cx + s//2 + size//28, cy + s//2 + size//28], fill=ACID)
    return img

def icon_eye(size=512):
    img = Image.new('RGB', (size, size), BG)
    draw = ImageDraw.Draw(img)
    pad = size // 12
    draw.rounded_rectangle([pad, pad, size-pad, size-pad], radius=size//8, fill=BG, outline=ACID, width=size//64)
    
    cx, cy = size//2, size//2
    w, h = size*0.55, size*0.35
    # eye outline
    draw.arc([cx-w//2, cy-h//2, cx+w//2, cy+h//2], start=0, end=180, fill=ACID, width=size//24)
    draw.arc([cx-w//2, cy-h//2, cx+w//2, cy+h//2], start=180, end=360, fill=ACID, width=size//24)
    # pupil
    r = size // 18
    draw.ellipse([cx-r, cy-r, cx+r, cy+r], fill=ACID)
    return img

icon_bell().save('D:/ObjectCode/Vigil/design/icons/icon_bell.png')
icon_v().save('D:/ObjectCode/Vigil/design/icons/icon_v.png')
icon_eye().save('D:/ObjectCode/Vigil/design/icons/icon_eye.png')
print('generated')
