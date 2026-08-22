-- os/colors.lua
-- NeetOS colour palette.

local Colors = {}

Colors.palette = {
    white       = {240, 240, 240},
    orange      = {242, 178,  51},
    magenta     = {229, 127, 216},
    lightBlue   = {153, 178, 242},
    yellow      = {222, 222, 108},
    lime        = {127, 204,  25},
    pink        = {242, 178, 204},
    gray        = { 76,  76,  76},
    lightGray   = {153, 153, 153},
    cyan        = { 76, 153, 178},
    purple      = {178, 102, 229},
    blue        = { 51, 102, 204},
    brown       = {102,  76,  51},
    green       = { 87, 166,  78},
    red         = {204,  76,  76},
    black       = { 17,  17,  17},
}

Colors.order = {
    "white", "orange", "magenta", "lightBlue", "yellow", "lime", "pink",
    "gray", "lightGray", "cyan", "purple", "blue", "brown", "green", "red",
    "black",
}

Colors.defaultFg = "white"
Colors.defaultBg = "black"

function Colors.rgb(name)
    local c = Colors.palette[name]
    if not c then
        return 240, 240, 240
    end
    return c[1], c[2], c[3]
end

function Colors.pack(r, g, b, a)
    return string.char(r, g, b, a or 255)
end

function Colors.rgba(name, a)
    local r, g, b = Colors.rgb(name)
    return Colors.pack(r, g, b, a)
end

NeetOS.Colors = Colors
return Colors
