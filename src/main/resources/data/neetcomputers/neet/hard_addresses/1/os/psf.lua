-- psf.lua
-- NeetComputers PSF1 & PSF2 font renderer

-- Copyright 2026 SpartanSoftware
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- MODULE API
--   psf.open(path, disk = 0)         Returns a Font object, or nil + errmsg
--
-- FONT OBJECT API
--   font:print(x, y, text)           Draw text in white
--   font:print(x, y, text, r, g, b)  Draw text in given color
--   font:measure(text)               Width of string in pixels
--   font:lineHeight()                Font height in pixels
--   font:setSpacing(w)               Set px width of glyph spacing
--   font:close()                     Free all layers

local psf = {}

local function readUInt(header, n)
    local bytes = header.read(n)
    if not bytes or #bytes < n then return nil end
    local value = 0
    for i = 1, n do
        value = value + string.byte(bytes, i) * (256 ^ (i - 1))
    end
    return math.floor(value)
end

local function parseGlyph(bytes, w, h)
    local bytesPerRow = math.floor((w + 7) / 8)
    local pixels = {}
    for y = 1, h do
        pixels[y] = {}
        for x = 1, w do
            local byteIdx = (y - 1) * bytesPerRow + math.floor((x - 1) / 8) + 1
            local bitPos  = 7 - ((x - 1) % 8)
            local b       = string.byte(bytes, byteIdx) or 0
            pixels[y][x]  = (math.floor(b / (2 ^ bitPos)) % 2 == 1)
        end
    end
    return pixels
end

local OFF_PIXEL = "\0\0\0\0"

local function getGlyphBuffer(glyph, r, g, b)
    local cache = glyph._bufCache
    if not cache then
        cache = {}
        glyph._bufCache = cache
    end

    local key = r * 65536 + g * 256 + b
    local buf = cache[key]
    if buf then return buf end

    local onPixel = string.char(r, g, b, 255)
    local pixels  = glyph.pixels
    local chunks  = {}
    local n       = 0

    for y = 1, glyph.height do
        local row = pixels[y]
        for x = 1, glyph.width do
            n = n + 1
            chunks[n] = row[x] and onPixel or OFF_PIXEL
        end
    end

    buf = table.concat(chunks)
    cache[key] = buf
    return buf
end

local function parsePSF1(header, glyphs)
    local mode     = readUInt(header, 1)
    local charsize = readUInt(header, 1)
    if not charsize then return false, "truncated PSF1 header" end

    local count = (mode and (mode % 4 >= 2)) and 512 or 256
    for i = 0, count - 1 do
        local raw = header.read(charsize)
        if not raw or #raw < charsize then break end
        glyphs[i] = { width = 8, height = charsize,
                      pixels = parseGlyph(raw, 8, charsize) }
    end
    return true, 8, charsize
end

local function parsePSF2(header, glyphs)
    readUInt(header, 4)
    local headersize    = readUInt(header, 4)
    readUInt(header, 4)
    local numglyph      = readUInt(header, 4)
    local bytesperglyph = readUInt(header, 4)
    local h             = readUInt(header, 4)
    local w             = readUInt(header, 4)
    if not w or not h then return false, "truncated PSF2 header" end

    local consumed = 32
    if headersize > consumed then
        header.seek('cur', headersize - consumed)
    end

    for i = 0, numglyph - 1 do
        local raw = header.read(bytesperglyph)
        if not raw or #raw < bytesperglyph then break end
        glyphs[i] = { width = w, height = h,
                      pixels = parseGlyph(raw, w, h) }
    end
    return true, w, h
end

local Font = {}
Font.__index = Font

function Font:print(x, y, text, r, g, b)
    assert(not self._closed, "psf: font has been closed")

    r = r or 255
    g = g or 255
    b = b or 255

    local sw      = screen.getSize()
    local cur     = x
    local spacing = self._spacing

    for i = 1, #text do
        if cur >= sw then break end
        local code  = string.byte(text, i)
        local glyph = self._glyphs[code]
        if glyph then
            local buf = getGlyphBuffer(glyph, r, g, b)
            screen.writeData(cur, y, buf, glyph.width)
            cur = cur + glyph.width + spacing
        end
    end
end

function Font:measure(text)
    local w     = 0
    local count = 0
    for i = 1, #text do
        local g = self._glyphs[string.byte(text, i)]
        if g then
            w     = w + g.width
            count = count + 1
        end
    end
    if count > 1 then
        w = w + self._spacing * (count - 1)
    end
    return w
end

function Font:lineHeight()
    return self._height
end

function Font:close()
    if self._closed then return end
    for _, glyph in pairs(self._glyphs) do
        glyph._bufCache = nil
    end
    self._glyphs  = {}
    self._closed  = true
end

function Font:setSpacing(px)
    self._spacing = px
end

function psf.open(path, disk)
    disk = disk or 0

    if not files.exists(path, disk) then
        return nil, "file not found: " .. path
    end

    local ok, header = pcall(files.open, path, 'rb', disk)
    if not ok or not header then
        return nil, "could not open: " .. path
    end

    local magic = header.read(2)
    if not magic or #magic < 2 then
        header.close()
        return nil, "could not read magic bytes"
    end

    local b1, b2 = string.byte(magic, 1), string.byte(magic, 2)
    local glyphs = {}
    local ok, w, h

    if b1 == 0x36 and b2 == 0x04 then
        ok, w, h = parsePSF1(header, glyphs)
    elseif b1 == 0x72 and b2 == 0xb5 then
        header.read(2)
        ok, w, h = parsePSF2(header, glyphs)
    else
        header.close()
        return nil, string.format("not a PSF file (magic: %02x %02x)", b1, b2)
    end

    header.close()

    if not ok then
        return nil, w
    end

    return setmetatable({
        _glyphs   = glyphs,
        _width    = w,
        _height   = h,
        _spacing  = 0,
        _closed   = false,
    }, Font)
end

return psf
