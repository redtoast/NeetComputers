-- programs/paint.lua
-- Tiny paint program
-- Click and drag to draw. Press keys 1-6 to change colour, C to clear,
-- Q to quit back to the shell.

local Colors = NeetOS.Colors
local Util = NeetOS.Util

local PALETTE = { "white", "red", "lime", "lightBlue", "yellow", "magenta" }
local colorIndex = 1

local sw, sh = screen.getSize()

local function drawColor()
    return Colors.rgba(PALETTE[colorIndex])
end

screen.fill(0, 0, sw - 1, sh - 1, Colors.rgba("black"))
screen.draw()

local running = true
while running do
    Util.yield()
    local redraw = false

    for _, e in ipairs(event.getQueue("User")) do
        local kind = e[1]
        if kind == "mouseClicked" or kind == "mouseDragged" then
            local x, y = e[2], e[3]
            screen.fill(x - 1, y - 1, x + 1, y + 1, drawColor())
            redraw = true
        elseif kind == "keyPressed" then
            local code, letter = e[2], e[3]
            local upperLetter = letter and letter:upper() or ""
            if upperLetter == "ESCAPE" or code == 27 or code == string.byte("q") or code == string.byte("Q") then
                running = false
            elseif code == string.byte("c") or code == string.byte("C") then
                screen.fill(0, 0, sw - 1, sh - 1, Colors.rgba("black"))
                redraw = true
            elseif code and code >= string.byte("1") and code <= string.byte(tostring(#PALETTE)) then
                colorIndex = code - string.byte("0")
            end
        end
    end

    if redraw then screen.draw() end
end

if NeetOS.term then
    NeetOS.term:clear()
    NeetOS.term:writeLine("NeetOS")
    NeetOS.term:redraw()
end
