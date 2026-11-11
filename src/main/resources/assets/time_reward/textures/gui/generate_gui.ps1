# Generate backpack_gui.png - Minecraft-style container GUI texture
# 256x256 PNG with header, scrollable slot area, and player inventory section

Add-Type -AssemblyName System.Drawing

$width = 256
$height = 256
$outputPath = Join-Path $PSScriptRoot "backpack_gui.png"

$bmp = New-Object System.Drawing.Bitmap($width, $height)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::None
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half

# ── Color Palette (Minecraft vanilla chest-inspired) ──
$Colors = @{
    bg          = [System.Drawing.Color]::FromArgb(198, 198, 182)  # #C6C6B6 base beige
    borderDark  = [System.Drawing.Color]::FromArgb(55, 55, 55)    # #373737 outer border
    borderMid   = [System.Drawing.Color]::FromArgb(85, 85, 85)    # #555555 slot border / mid frame
    slotInner   = [System.Drawing.Color]::FromArgb(139, 139, 110) # #8B8B6E slot recess
    slotLight   = [System.Drawing.Color]::FromArgb(166, 166, 140) # #A6A68C slot lighter center
    headerBg    = [System.Drawing.Color]::FromArgb(184, 184, 165) # #B8B8A5 header band
    headerTop   = [System.Drawing.Color]::FromArgb(172, 172, 153) # #ACAC99 header top part
    divider     = [System.Drawing.Color]::FromArgb(60, 60, 45)    # #3C3C2D divider lines
    invBg       = [System.Drawing.Color]::FromArgb(192, 192, 176) # #C0C0B0 inventory section bg
    bevelLight  = [System.Drawing.Color]::FromArgb(220, 220, 200) # #DCDCC8 bevel highlight
}

function Brush($c) { return [System.Drawing.SolidBrush]::new($c) }
function Pen($c)   { return [System.Drawing.Pen]::new($c) }

$brushes = @{}
$Colors.GetEnumerator() | ForEach-Object { $brushes[$_.Key] = Brush $_.Value }

# ── 1. Fill base background ──
$g.FillRectangle($brushes.bg, 0, 0, $width, $height)

# ── 2. Draw outer frame border ──
# Top border
$g.FillRectangle($brushes.borderDark, 0, 0, $width, 1)
$g.FillRectangle($brushes.borderMid,  0, 1, $width, 1)
# Bottom border
$g.FillRectangle($brushes.borderDark, 0, 254, $width, 2)
$g.FillRectangle($brushes.borderMid,  0, 253, $width, 1)
# Left border
$g.FillRectangle($brushes.borderDark, 0, 0, 1, $height)
$g.FillRectangle($brushes.borderMid,  1, 0, 1, $height)
# Right border
$g.FillRectangle($brushes.borderDark, 255, 0, 1, $height)
$g.FillRectangle($brushes.borderMid,  254, 0, 1, $height)

# ── 3. Header area (y=2 to y=16) ──
$g.FillRectangle($brushes.headerTop, 2, 2, 252, 6)  # top part slightly darker
$g.FillRectangle($brushes.headerBg,  2, 8, 252, 8)  # main header band
# Header bottom divider
$g.FillRectangle($brushes.divider,   2, 16, 252, 1)
$g.FillRectangle($brushes.borderMid, 0, 17, $width, 1)

# Add a subtle highlight line below the divider
$g.FillRectangle($brushes.bevelLight, 2, 17, 252, 1)

# ── 4. Left/right inner bevel lines (gives 3D inset look) ──
$g.FillRectangle($brushes.borderMid, 2, 2, 1, 252)
$g.FillRectangle($brushes.borderMid, 253, 2, 1, 252)

# ── 5. Draw slot grid ──
# Each slot: 18x18 px with 1px dark border and 16x16 lighter interior
# Grid starts at x=7, y=17, 12 columns
# We draw slots in two sections: main area and player inventory area

$slotSize = 18
$gridStartX = 7
$gridStartY = 17
$cols = 12

function Draw-Slot {
    param($g, $x, $y, $brushes)
    # Slot outer border (1px) - gives recessed look
    $g.FillRectangle($brushes.borderMid, $x, $y, 18, 1)
    $g.FillRectangle($brushes.borderMid, $x, $y+17, 18, 1)
    $g.FillRectangle($brushes.borderMid, $x, $y, 1, 18)
    $g.FillRectangle($brushes.borderMid, $x+17, $y, 1, 18)
    # Slot inner area (16x16)
    $g.FillRectangle($brushes.slotInner, $x+1, $y+1, 16, 16)
    # Slightly lighter center pixel (subtle depth effect)
    $g.FillRectangle($brushes.slotLight, $x+2, $y+2, 14, 14)
    # Corner highlights for 3D effect
    $g.FillRectangle($brushes.bevelLight, $x+1, $y+1, 1, 1)
}

# ── Main slot rows (y=17 through y=143, 8 rows) ──
for ($row = 0; $row -lt 8; $row++) {
    $slotY = $gridStartY + $row * $slotSize
    for ($col = 0; $col -lt $cols; $col++) {
        $slotX = $gridStartX + $col * $slotSize
        Draw-Slot $g $slotX $slotY $brushes
    }
}

# ── Divider before player inventory section ──
$invDividerY = 17 + 8 * $slotSize  # = 161
$g.FillRectangle($brushes.divider, 2, $invDividerY, 252, 1)
$g.FillRectangle($brushes.bevelLight, 2, $invDividerY+1, 252, 1)
# Fill the gap before player slots with slightly different bg
$g.FillRectangle($brushes.invBg, 2, $invDividerY+2, 252, 3)

# ── Player inventory slots ──
# Player inventory: 3 rows (y offsets: invDividerY+5, +23, +41)
# Hotbar: 1 row (y offset: invDividerY+59)
$invStartY = $invDividerY + 5
for ($row = 0; $row -lt 3; $row++) {
    $slotY = $invStartY + $row * $slotSize
    for ($col = 0; $col -lt $cols; $col++) {
        $slotX = $gridStartX + $col * $slotSize
        Draw-Slot $g $slotX $slotY $brushes
    }
}

# Hotbar row (row index 3, offset by 3*18 = 54 from invStartY)
$hotbarY = $invStartY + 3 * $slotSize + 4  # +4 for gap
for ($col = 0; $col -lt $cols; $col++) {
    $slotX = $gridStartX + $col * $slotSize
    Draw-Slot $g $slotX $hotbarY $brushes
}

# Fill remaining space below hotbar with inventory bg
$remainingStart = $hotbarY + $slotSize
$g.FillRectangle($brushes.invBg, 2, $remainingStart, 252, 255 - $remainingStart)

# ── Save ──
$bmp.Save($outputPath, [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()

Write-Output "Generated: $outputPath"
Write-Output "Size: $($width)x$($height)px"
Get-Item $outputPath | Select-Object Length, FullName
