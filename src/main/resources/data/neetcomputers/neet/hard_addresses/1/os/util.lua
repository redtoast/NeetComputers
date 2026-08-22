-- os/util.lua
-- Small shared helpers used across NeetOS.

local Util = {}

function Util.yield()
    pcall(coroutine.yield)
end

function Util.trim(s)
    return (s:gsub("^%s+", ""):gsub("%s+$", ""))
end

function Util.split(s, sep)
    local out = {}
    for part in (s .. sep):gmatch("(.-)" .. sep) do
        out[#out + 1] = part
    end
    return out
end

function Util.startsWith(s, prefix)
    return s:sub(1, #prefix) == prefix
end

local function serializeValue(v, indent, seen)
    local t = type(v)
    if t == "string" then
        return string.format("%q", v)
    elseif t == "number" or t == "boolean" or t == "nil" then
        return tostring(v)
    elseif t == "table" then
        seen = seen or {}
        if seen[v] then
            return "<circular>"
        end
        seen[v] = true

        local arrayLen = 0
        while v[arrayLen + 1] ~= nil do
            arrayLen = arrayLen + 1
        end

        local parts = {}
        local pad = string.rep("  ", indent)
        local innerPad = string.rep("  ", indent + 1)

        for i = 1, arrayLen do
	    parts[#parts + 1] = innerPad .. serializeValue(v[i], indent + 1, seen)
        end

        local otherKeys = {}
        for k in pairs(v) do
            local isArrayIdx = type(k) == "number" and k >= 1 and k <= arrayLen
                and k == math.floor(k)
            if not isArrayIdx then
                otherKeys[#otherKeys + 1] = k
            end
        end
        table.sort(otherKeys, function(a, b)
            if type(a) == type(b) then
                local ok, result = pcall(function() return a < b end)
                if ok then return result end
                return tostring(a) < tostring(b)
            end
            return type(a) < type(b)
        end)

        for _, k in ipairs(otherKeys) do
            local keyStr
            if type(k) == "string" and k:match("^[%a_][%w_]*$") then
                keyStr = k
            else
                keyStr = "[" .. serializeValue(k, indent + 1, seen) .. "]"
            end
            parts[#parts + 1] = innerPad .. keyStr .. " = " .. serializeValue(v[k], indent + 1, seen)
        end

        seen[v] = nil

        if #parts == 0 then return "{}" end
        return "{\n" .. table.concat(parts, ",\n") .. "\n" .. pad .. "}"
    else
        return tostring(v)
    end
end

function Util.serialize(value)
    return serializeValue(value, 0, {})
end

function Util.readLine(term, opts)
    opts = opts or {}
    local Keyboard = NeetOS.Keyboard

    local buffer = opts.default or ""
    local cursor = #buffer
    local history = opts.history
    local historyIndex = nil
    local startCol, startRow = term:getCursorPos()

    local function render()
        term:clearToEnd(startCol, startRow)
        term:setCursorPos(startCol, startRow)
        if opts.replaceChar then
            term:write(string.rep(opts.replaceChar, #buffer))
        else
            term:write(buffer)
        end
        local dx, dy = term:advancePos(startCol, startRow, cursor)
        term:setCursorPos(dx, dy)
    end

    render()
    term:redraw()

    while true do
        Util.yield()
        local changed = term:updateBlink()

        for _, ev in ipairs(Keyboard.poll()) do
            if ev.kind == "char" then
                buffer = buffer:sub(1, cursor) .. ev.ch .. buffer:sub(cursor + 1)
                cursor = cursor + 1
                changed = true
            elseif ev.kind == "key" then
                if ev.key == "enter" then
                    return buffer
                elseif ev.key == "backspace" then
                    if cursor > 0 then
                        buffer = buffer:sub(1, cursor - 1) .. buffer:sub(cursor + 1)
                        cursor = cursor - 1
                        changed = true
                    end
                elseif ev.key == "delete" then
                    if cursor < #buffer then
                        buffer = buffer:sub(1, cursor) .. buffer:sub(cursor + 2)
                        changed = true
                    end
                elseif ev.key == "left" then
                    if cursor > 0 then cursor = cursor - 1; changed = true end
                elseif ev.key == "right" then
                    if cursor < #buffer then cursor = cursor + 1; changed = true end
                elseif ev.key == "home" then
                    cursor = 0; changed = true
                elseif ev.key == "end" then
                    cursor = #buffer; changed = true
                elseif ev.key == "up" and history then
                    if #history > 0 then
                        historyIndex = math.max(1, (historyIndex or (#history + 1)) - 1)
                        buffer = history[historyIndex]
                        cursor = #buffer
                        changed = true
                    end
                elseif ev.key == "down" and history then
                    if historyIndex then
                        historyIndex = historyIndex + 1
                        if historyIndex > #history then
                            historyIndex = nil
                            buffer = ""
                        else
                            buffer = history[historyIndex]
                        end
                        cursor = #buffer
                        changed = true
                    end
                elseif ev.key == "escape" and opts.cancelOnEscape then
                    return nil
                end
            end
        end

        if changed then
            render()
            term:redraw()
        end
    end
end

NeetOS.Util = Util
return Util
