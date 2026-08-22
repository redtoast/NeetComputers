-- os/parse.lua
-- Splits a shell command line into arguments

local Parse = {}

function Parse.tokenize(line)
    local tokens = {}
    local i, n = 1, #line

    local function skipSpaces()
        while i <= n and line:sub(i, i):match("%s") do i = i + 1 end
    end

    skipSpaces()
    while i <= n do
        local quote = line:sub(i, i)
        local tok
        if quote == '"' or quote == "'" then
            i = i + 1
            local start = i
            while i <= n and line:sub(i, i) ~= quote do i = i + 1 end
            tok = line:sub(start, i - 1)
            i = i + 1
        else
            local start = i
            while i <= n and not line:sub(i, i):match("%s") do i = i + 1 end
            tok = line:sub(start, i - 1)
        end
        tokens[#tokens + 1] = tok
        skipSpaces()
    end

    return tokens
end

function Parse.options(args, valueFlags)
    valueFlags = valueFlags or {}
    local opts = {}
    local positional = {}
    local i = 1
    while i <= #args do
        local a = args[i]
        if a == "--" then
            for j = i + 1, #args do positional[#positional + 1] = args[j] end
            break
        elseif a:sub(1, 2) == "--" then
            local key, val = a:sub(3):match("^([^=]+)=(.*)$")
            if key then
                opts[key] = val
            else
                local name = a:sub(3)
                if valueFlags[name] then
                    i = i + 1
                    opts[name] = args[i]
                else
                    opts[name] = true
                end
            end
        elseif a:sub(1, 1) == "-" and #a > 1 then
            local j = 2
            while j <= #a do
                local c = a:sub(j, j)
                if valueFlags[c] then
                    local rest = a:sub(j + 1)
                    if rest ~= "" then
                        opts[c] = rest
                    else
                        i = i + 1
                        opts[c] = args[i]
                    end
                    break
                else
                    opts[c] = true
                    j = j + 1
                end
            end
        else
            positional[#positional + 1] = a
        end
        i = i + 1
    end
    return opts, positional
end

NeetOS.Parse = Parse
return Parse
