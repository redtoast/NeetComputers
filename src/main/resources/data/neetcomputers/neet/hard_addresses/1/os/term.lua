-- os/term.lua
-- NeetOS terminal.

local Colors = NeetOS.Colors

local Term = {}
Term.__index = Term

local ROW_PADDING = 1

function Term.new(font)
    local sw, sh = screen.getSize()

    local charW = font:measure("0")
    if not charW or charW == 0 then charW = 8 end
    local charH = font:lineHeight() + ROW_PADDING

    local self = setmetatable({
        font        = font,
        screenW     = sw,
        screenH     = sh,
        charW       = charW,
        charH       = charH,
        cols        = math.max(1, math.floor(sw / charW)),
        rows        = math.max(1, math.floor(sh / charH)),
        fg          = Colors.defaultFg,
        bg          = Colors.defaultBg,
        cx          = 1,
        cy          = 1,
        pendingWrap = false,
        blinkOn     = true,
        blinkVisible = true,
        lastBlink   = chip.getTime(),
        dirty       = {},
        lastCursorX = nil,
        lastCursorY = nil,
    }, Term)

    self:clear()
    return self
end

local function blankCell(self)
    return { ch = " ", fg = self.fg, bg = self.bg }
end

local function resetCell(self, cell)
    cell.ch = " "
    cell.fg = self.fg
    cell.bg = self.bg
end

function Term:markDirty(y, x)
    local row = self.dirty[y]
    if not row then row = {}; self.dirty[y] = row end
    row[x] = true
end

function Term:markAllDirty()
    for y = 1, self.rows do
        local row = self.dirty[y]
        if not row then row = {}; self.dirty[y] = row end
        for x = 1, self.cols do row[x] = true end
    end
end

function Term:clear()
    if not self.buf then
        self.buf = {}
        self.dirty = {}
        for y = 1, self.rows do
            local row = {}
            for x = 1, self.cols do
                row[x] = blankCell(self)
            end
            self.buf[y] = row
        end
    else
        for y = 1, self.rows do
            local row = self.buf[y]
            for x = 1, self.cols do
                resetCell(self, row[x])
            end
        end
    end
    self:markAllDirty()
    self.cx, self.cy = 1, 1
    self.pendingWrap = false
end

function Term:getSize()
    return self.cols, self.rows
end

function Term:setCursorPos(x, y)
    self.cx = math.max(1, math.min(x, self.cols))
    self.cy = math.max(1, math.min(y, self.rows))
    self.pendingWrap = false
end

function Term:getCursorPos()
    return self.cx, self.cy
end

function Term:setCursorBlink(state)
    self.blinkOn = state
    self.blinkVisible = true
end

function Term:setTextColor(name)
    if Colors.palette[name] then self.fg = name end
end

function Term:setBackgroundColor(name)
    if Colors.palette[name] then self.bg = name end
end

function Term:getTextColor() return self.fg end
function Term:getBackgroundColor() return self.bg end

function Term:scroll(n)
    n = n or 1
    for _ = 1, n do
        local row = table.remove(self.buf, 1)
        for x = 1, self.cols do resetCell(self, row[x]) end
        self.buf[#self.buf + 1] = row
    end

    self:markAllDirty()
end

function Term:newline()
    self.cx = 1
    self.cy = self.cy + 1
    self.pendingWrap = false
    if self.cy > self.rows then
        self:scroll(self.cy - self.rows)
        self.cy = self.rows
    end
end

function Term:putChar(ch)
    if self.pendingWrap then
        self.cx = 1
        self.cy = self.cy + 1
        self.pendingWrap = false
        if self.cy > self.rows then
            self:scroll(self.cy - self.rows)
            self.cy = self.rows
        end
    end

    local cell = self.buf[self.cy][self.cx]
    cell.ch, cell.fg, cell.bg = ch, self.fg, self.bg
    self:markDirty(self.cy, self.cx)

    if self.cx >= self.cols then
        self.pendingWrap = true
    else
        self.cx = self.cx + 1
    end
end

function Term:write(text)
    text = tostring(text)
    for i = 1, #text do
        local c = text:sub(i, i)
        if c == "\n" then
            self:newline()
        elseif c == "\r" then
        else
            self:putChar(c)
        end
    end
end

function Term:writeLine(text)
    self:write(text)
    self:newline()
    self:redraw()
end

function Term:clearLine(y)
    y = y or self.cy
    for cx = 1, self.cols do
        resetCell(self, self.buf[y][cx])
        self:markDirty(y, cx)
    end
end

function Term:clearToLineEnd(x, y)
    y = y or self.cy
    x = x or self.cx
    for cx = x, self.cols do
        resetCell(self, self.buf[y][cx])
        self:markDirty(y, cx)
    end
end

function Term:clearToEnd(x, y)
    for cx = x, self.cols do
        resetCell(self, self.buf[y][cx])
        self:markDirty(y, cx)
    end
    for cy = y + 1, self.rows do
        for cx = 1, self.cols do
            resetCell(self, self.buf[cy][cx])
            self:markDirty(cy, cx)
        end
    end
end

function Term:advancePos(x, y, n)
    local pending = false
    for _ = 1, n do
        if pending then
            x = 1
            y = y + 1
            if y > self.rows then y = self.rows end
            pending = false
        end
        if x >= self.cols then
            pending = true
        else
            x = x + 1
        end
    end
    return x, y
end

function Term:backspace()
    if self.cx > 1 then
        self.cx = self.cx - 1
        resetCell(self, self.buf[self.cy][self.cx])
        self:markDirty(self.cy, self.cx)
    elseif self.cy > 1 then
        self.cy = self.cy - 1
        self.cx = self.cols
        resetCell(self, self.buf[self.cy][self.cx])
        self:markDirty(self.cy, self.cx)
    end
end

function Term:updateBlink()
    if not self.blinkOn then return false end
    local now = chip.getTime()
    if now - self.lastBlink >= 0.5 then
        self.lastBlink = now
        self.blinkVisible = not self.blinkVisible
        return true
    end
    return false
end

function Term:redraw()
    if self.lastCursorX then
        self:markDirty(self.lastCursorY, self.lastCursorX)
    end
    self:markDirty(self.cy, self.cx)

    for y, row in pairs(self.dirty) do
        local bufRow = self.buf[y]
        local py = (y - 1) * self.charH
        for x in pairs(row) do
            local cell = bufRow[x]
            screen.fill((x - 1) * self.charW, py,
                        x * self.charW - 1, py + self.charH - 1,
                        Colors.rgba(cell.bg))
            if cell.ch ~= " " then
                local fr, fg, fb = Colors.rgb(cell.fg)
                self.font:print((x - 1) * self.charW + 1, py + 1, cell.ch, fr, fg, fb)
            end
        end
    end
    self.dirty = {}

    if self.blinkOn and self.blinkVisible then
        local px = (self.cx - 1) * self.charW
        local py = (self.cy - 1) * self.charH
        screen.fill(px, py + self.charH - 2, px + self.charW - 1, py + self.charH - 1,
                    Colors.rgba(self.fg))
        self.lastCursorX, self.lastCursorY = self.cx, self.cy
    else
        self.lastCursorX, self.lastCursorY = nil, nil
    end

    screen.draw()
end

NeetOS.Term = Term
return Term
