-- os/shell.lua
-- The NeetOS shell

local Parse    = NeetOS.Parse
local Colors   = NeetOS.Colors
local Util     = NeetOS.Util
local Editor   = NeetOS.Editor
local Config   = NeetOS.Config

local Shell = {}
Shell.__index = Shell

local builtins = {}
local usage = {}

local function hasHelpFlag(tokens)
    for _, t in ipairs(tokens) do
        if t == "--" then return false end
        if t == "-h" or t == "--help" then return true end
    end
    return false
end

function Shell.new(term, fs)
    local self = setmetatable({
        term = term,
        fs = fs,
        history = {},
        historyIndex = nil,
        running = true,
    }, Shell)
    return self
end

function Shell:printPrompt()
    local term = self.term
    local before, after = Config.prompt:match("^(.-)%%cwd%%(.*)$")

    term:setTextColor(Colors.defaultFg)
    if before then
        term:write(before)
        term:setTextColor(Config.theme.prompt or "lime")
        term:write(self.fs:pwd())
        term:setTextColor(Colors.defaultFg)
        term:write(after)
    else
        -- No %cwd% token in a custom prompt; just print it as-is.
        term:write(Config.prompt)
    end

    self.promptCol, self.promptRow = term:getCursorPos()
end

function Shell:readLine()
    return Util.readLine(self.term, { history = self.history })
end

function Shell:tryRunProgram(name, args)
    local candidates = {}
    if name:match("[:/]") then
        candidates[#candidates + 1] = name
    else
        candidates[#candidates + 1] = name
        candidates[#candidates + 1] = name .. ".lua"
        for _, dir in ipairs(Config.path) do
            local prefix = dir:sub(-1) == "/" and dir or (dir .. "/")
            candidates[#candidates + 1] = prefix .. name .. ".lua"
        end
    end

    for _, candidate in ipairs(candidates) do
        if self.fs:exists(candidate) and self.fs:isFile(candidate) then
            local src, err = self.fs:readAll(candidate)
            if not src then
                self.term:writeLine("neetos: " .. tostring(err))
                return true
            end
            local chunk, cerr = load(src, "=" .. candidate)
            if not chunk then
                self.term:writeLine("neetos: syntax error: " .. tostring(cerr))
                return true
            end
            local ok, runErr = pcall(chunk, table.unpack(args))
            if not ok then
                self.term:writeLine("neetos: " .. tostring(runErr))
            end
            return true
        end
    end
    return false
end

function Shell:execute(line)
    local tokens = Parse.tokenize(line)
    if #tokens == 0 then return end

    local name = table.remove(tokens, 1)

    local seen = {}
    while Config.aliases[name] and not seen[name] do
        seen[name] = true
        local expansion = Parse.tokenize(Config.aliases[name])
        if #expansion == 0 then break end
        name = table.remove(expansion, 1)
        for i = #expansion, 1, -1 do
            table.insert(tokens, 1, expansion[i])
        end
    end

    local handler = builtins[name]
    if handler then
        if usage[name] and hasHelpFlag(tokens) then
            self.term:writeLine(usage[name])
            return
        end
        local ok, err = pcall(handler, self, tokens)
        if not ok then
            self.term:writeLine("neetos: " .. tostring(err))
        end
        return
    end

    if not self:tryRunProgram(name, tokens) then
        self.term:writeLine(name .. ": command not found")
    end
end

function Shell:run()
    local term = self.term
    term:writeLine("NeetOS")
    term:writeLine("Type 'help' for a list of commands.")
    term:redraw()

    while self.running do
        self:printPrompt()
        local line = self:readLine()
        term:newline()

        line = Util.trim(line)
        if line ~= "" then
            if self.history[#self.history] ~= line then
                self.history[#self.history + 1] = line
                if #self.history > Config.historySize then
                    table.remove(self.history, 1)
                end
            end
            self:execute(line)
        end
        term:redraw()
    end
end

usage["help"] = "usage: help\nAny builtin also accepts -h / --help for its own usage line."

builtins["help"] = function(shell, args)
    local term = shell.term
    local names = {}
    for name in pairs(builtins) do names[#names + 1] = name end
    table.sort(names)
    term:writeLine("Builtin commands:")
    local cols = term:getSize()
    local line = "  "
    for _, name in ipairs(names) do
        if #line + #name + 2 > cols then
            term:writeLine(line)
            line = "  "
        end
        line = line .. name .. "  "
    end
    if line ~= "  " then term:writeLine(line) end
    term:writeLine("Run a .lua file from disk with: run <path>")
    term:writeLine("Any command --help shows its usage.")
end

usage["clear"] = "usage: clear"

builtins["clear"] = function(shell)
    shell.term:clear()
end

usage["pwd"] = "usage: pwd"

builtins["pwd"] = function(shell)
    shell.term:writeLine(shell.fs:pwd())
end

usage["cd"] = "usage: cd [path]"

builtins["cd"] = function(shell, args)
    local ok, err = shell.fs:cd(args[1] or "/")
    if not ok then shell.term:writeLine("cd: " .. tostring(err)) end
end

usage["ls"] = "usage: ls [-l] [-a] [-1] [path...]\n" ..
              "  -l  long listing (show entry type)\n" ..
              "  -a  show entries starting with '.'\n" ..
              "  -1  one entry per line"

local function lsOne(shell, path, opts, showHeader)
    local list, err = shell.fs:list(path)
    if not list then
        shell.term:writeLine("ls: " .. tostring(err))
        return
    end

    if showHeader then
        shell.term:writeLine((path or shell.fs:pwd()) .. ":")
    end

    table.sort(list)
    local entries = {}
    for _, entry in ipairs(list) do
        if opts.a or opts.all or entry:sub(1, 1) ~= "." then
            entries[#entries + 1] = entry
        end
    end

    if #entries == 0 then
        shell.term:writeLine("(empty)")
        return
    end

    local function decorate(entry)
        local full = (path and (path .. "/") or "") .. entry
        return entry .. (shell.fs:isDir(full) and "/" or "")
    end

    if opts.l or opts.long then
        for _, entry in ipairs(entries) do
            local full = (path and (path .. "/") or "") .. entry
            local isDir = shell.fs:isDir(full)
            shell.term:writeLine((isDir and "d " or "- ") .. entry .. (isDir and "/" or ""))
        end
    elseif opts["1"] then
        for _, entry in ipairs(entries) do
            shell.term:writeLine(decorate(entry))
        end
    else
        local cols = shell.term:getSize()
        local line = "  "
        for _, entry in ipairs(entries) do
            local name = decorate(entry)
            if #line + #name + 2 > cols then
                shell.term:writeLine(line)
                line = "  "
            end
            line = line .. name .. "  "
        end
        if line ~= "  " then shell.term:writeLine(line) end
    end
end

builtins["ls"] = function(shell, args)
    local opts, positional = Parse.options(args)
    if #positional == 0 then
        lsOne(shell, nil, opts, false)
        return
    end
    local multiple = #positional > 1
    for idx, path in ipairs(positional) do
        if multiple and idx > 1 then shell.term:writeLine("") end
        lsOne(shell, path, opts, multiple)
    end
end

usage["mkdir"] = "usage: mkdir [-p] <path...>\n" ..
                  "  -p  create parent directories as needed"

builtins["mkdir"] = function(shell, args)
    local opts, positional = Parse.options(args)
    if #positional == 0 then shell.term:writeLine(usage["mkdir"]); return end

    for _, p in ipairs(positional) do
        if opts.p or opts.parents then
            local target = shell.fs:resolve(p)
            local partition, rest = target:match("^([%w_%-]+):(/.*)$")
            local built = partition .. ":"
            local ok = true
            for seg in rest:gmatch("[^/]+") do
                built = built .. "/" .. seg
                if not shell.fs:exists(built) then
                    ok = shell.fs:mkdir(built) and ok
                end
            end
            if not ok then shell.term:writeLine("mkdir: failed to create " .. p) end
        elseif not shell.fs:mkdir(p) then
            shell.term:writeLine("mkdir: failed to create " .. p)
        end
    end
end

usage["rm"] = "usage: rm [-r] [-f] [-v] <path...>\n" ..
              "  -r  remove directories recursively\n" ..
              "  -f  ignore nonexistent paths, suppress errors\n" ..
              "  -v  print each path removed"

builtins["rm"] = function(shell, args)
    local opts, positional = Parse.options(args)
    if #positional == 0 then shell.term:writeLine(usage["rm"]); return end

    local force = opts.f or opts.force
    local recursive = opts.r or opts.recursive
    for _, p in ipairs(positional) do
        if not shell.fs:exists(p) then
            if not force then
                shell.term:writeLine("rm: cannot remove '" .. p .. "': no such file or directory")
            end
        elseif shell.fs:isDir(p) and not recursive then
            shell.term:writeLine("rm: cannot remove '" .. p .. "': is a directory (use -r)")
        else
            local ok, err = recursive and shell.fs:deleteRecursive(p) or shell.fs:delete(p)
            if not ok and not force then
                shell.term:writeLine("rm: " .. tostring(err or ("failed to delete " .. p)))
            elseif ok and (opts.v or opts.verbose) then
                shell.term:writeLine("removed '" .. p .. "'")
            end
        end
    end
end

local function confirmOverwrite(shell, dst)
    if not shell.fs:exists(dst) or shell.fs:isDir(dst) then return true end
    shell.term:write("overwrite '" .. dst .. "'? (y/n) ")
    shell.term:redraw()
    local answer = Util.readLine(shell.term, {})
    shell.term:newline()
    return Util.trim(answer):lower() == "y"
end

usage["cp"] = "usage: cp [-r] [-v] [-i] <src> <dst>\n" ..
              "  -r  copy directories recursively\n" ..
              "  -v  print each file copied\n" ..
              "  -i  prompt before overwriting an existing file"

builtins["cp"] = function(shell, args)
    local opts, positional = Parse.options(args)
    local src, dst = positional[1], positional[2]
    if not src or not dst then shell.term:writeLine(usage["cp"]); return end

    if shell.fs:isDir(src) and not (opts.r or opts.recursive) then
        shell.term:writeLine("cp: -r not specified; omitting directory '" .. src .. "'")
        return
    end
    if (opts.i or opts.interactive) and not confirmOverwrite(shell, dst) then return end

    local ok, err = (opts.r or opts.recursive)
        and shell.fs:copyRecursive(src, dst) or shell.fs:copy(src, dst)
    if not ok then
        shell.term:writeLine("cp: " .. tostring(err))
    elseif opts.v or opts.verbose then
        shell.term:writeLine("'" .. src .. "' -> '" .. dst .. "'")
    end
end

usage["mv"] = "usage: mv [-v] [-i] <src> <dst>\n" ..
              "  -v  print each file moved\n" ..
              "  -i  prompt before overwriting an existing file"

builtins["mv"] = function(shell, args)
    local opts, positional = Parse.options(args)
    local src, dst = positional[1], positional[2]
    if not src or not dst then shell.term:writeLine(usage["mv"]); return end

    if (opts.i or opts.interactive) and not confirmOverwrite(shell, dst) then return end

    local ok, err = shell.fs:move(src, dst)
    if not ok then
        shell.term:writeLine("mv: " .. tostring(err))
    elseif opts.v or opts.verbose then
        shell.term:writeLine("'" .. src .. "' -> '" .. dst .. "'")
    end
end

usage["cat"] = "usage: cat [-n] <path...>\n" ..
               "  -n  number output lines"

builtins["cat"] = function(shell, args)
    local opts, positional = Parse.options(args)
    if #positional == 0 then shell.term:writeLine(usage["cat"]); return end

    local lineNo = 1
    for _, p in ipairs(positional) do
        local data, err = shell.fs:readAll(p)
        if not data then
            shell.term:writeLine("cat: " .. tostring(err))
        elseif opts.n or opts.number then
            local lines = Util.split(data, "\n")
            if data:sub(-1) == "\n" then lines[#lines] = nil end
            for _, l in ipairs(lines) do
                shell.term:writeLine(string.format("%6d\t%s", lineNo, l))
                lineNo = lineNo + 1
            end
        else
            shell.term:write(data)
            if data:sub(-1) ~= "\n" then shell.term:newline() end
        end
    end
end

usage["edit"] = "usage: edit <path>"

builtins["edit"] = function(shell, args)
    if not args[1] then shell.term:writeLine(usage["edit"]); return end
    local target = shell.fs:resolve(args[1])
    Editor.run(shell.term, shell.fs, target)
    shell.term:clear()
    shell.term:writeLine("NeetOS")
end

usage["run"] = "usage: run <path> [args...]"

builtins["run"] = function(shell, args)
    local path = table.remove(args, 1)
    if not path then shell.term:writeLine(usage["run"]); return end
    if not shell:tryRunProgram(path, args) then
        shell.term:writeLine("run: no such program: " .. path)
    end
end

usage["echo"] = "usage: echo [-n] [-e] [text...]\n" ..
                "  -n  do not output the trailing newline\n" ..
                "  -e  interpret backslash escapes (\\n \\t \\\\)"

local ECHO_ESCAPES = { n = "\n", t = "\t", ["\\"] = "\\" }

builtins["echo"] = function(shell, args)
    local n, e = false, false
    local i = 1
    while args[i] and args[i]:match("^%-[ne]+$") do
        if args[i]:find("n") then n = true end
        if args[i]:find("e") then e = true end
        i = i + 1
    end
    local rest = {}
    for j = i, #args do rest[#rest + 1] = args[j] end
    local text = table.concat(rest, " ")
    if e then
        text = text:gsub("\\(.)", function(c) return ECHO_ESCAPES[c] or ("\\" .. c) end)
    end
    if n then
        shell.term:write(text)
        shell.term:redraw()
    else
        shell.term:writeLine(text)
    end
end

usage["history"] = "usage: history [-c] [n]\n" ..
                    "  -c  clear history\n" ..
                    "  n   show only the last n entries"

builtins["history"] = function(shell, args)
    local opts, positional = Parse.options(args)
    if opts.c or opts.clear then
        for i = #shell.history, 1, -1 do shell.history[i] = nil end
        return
    end
    local n = tonumber(positional[1])
    local start = 1
    if n then start = math.max(1, #shell.history - n + 1) end
    for i = start, #shell.history do
        shell.term:writeLine(string.format("%3d  %s", i, shell.history[i]))
    end
end

usage["time"] = "usage: time"

builtins["time"] = function(shell)
    shell.term:writeLine(string.format("uptime:    %.1fs", chip.getTime()))
    shell.term:writeLine(string.format("unix time: %d", math.floor(chip.getUnixTime())))
    shell.term:writeLine(string.format("lunar time: %d", chip.getLunarTime()))
end

usage["disks"] = "usage: disks"

builtins["disks"] = function(shell)
    local n = files.getNumberOfDisks() or 0
    if n == 0 then
        shell.term:writeLine("no disks attached")
        return
    end
    for d = 0, n - 1 do
        local ok, id = pcall(files.getDiskID, d)
        shell.term:writeLine(string.format("disk %d  id=%s", d, ok and tostring(id) or "?"))
    end
end

usage["disk"] = "usage: disk [number]  (no argument prints the current disk)"

builtins["disk"] = function(shell, args)
    if not args[1] then
        shell.term:writeLine("current disk: " .. shell.fs.disk)
        return
    end
    local n = tonumber(args[1])
    if not n then shell.term:writeLine(usage["disk"]); return end
    shell.fs:setDisk(math.floor(n))
    shell.term:writeLine("switched to disk " .. n .. ", cwd is now " .. shell.fs:pwd())
end

usage["eject"] = "usage: eject <disk number>"

builtins["eject"] = function(shell, args)
    local n = tonumber(args[1])
    if not n then shell.term:writeLine(usage["eject"]); return end
    if not files.removeDisk(math.floor(n)) then
        shell.term:writeLine("eject: failed (home disk can't be ejected)")
    end
end

usage["partitions"] = "usage: partitions"

builtins["partitions"] = function(shell)
    local raw = files.getPartitions(shell.fs.disk) or {}
    local parts = raw[1] or raw
    for _, entry in ipairs(parts) do
        local info, name
        if type(entry) == "table" then
            info, name = entry, entry.name
        else
            name = entry
            info = files.getPartition(name, shell.fs.disk) or {}
        end
        local flags = {}
        if info.readonly then flags[#flags + 1] = "readonly" end
        if info.hidden then flags[#flags + 1] = "hidden" end
        shell.term:writeLine("  " .. tostring(name) .. (#flags > 0 and (" (" .. table.concat(flags, ", ") .. ")") or ""))
    end
end

usage["mkpart"] = "usage: mkpart <name>"

builtins["mkpart"] = function(shell, args)
    if not args[1] then shell.term:writeLine(usage["mkpart"]); return end
    if not files.createPartition(args[1], shell.fs.disk) then
        shell.term:writeLine("mkpart: could not create partition " .. args[1])
    end
end

usage["rmpart"] = "usage: rmpart <name>"

builtins["rmpart"] = function(shell, args)
    if not args[1] then shell.term:writeLine(usage["rmpart"]); return end
    if not files.deletePartition(args[1], shell.fs.disk) then
        shell.term:writeLine("rmpart: could not delete partition " .. args[1])
    end
end

usage["bootinfo"] = "usage: bootinfo"

builtins["bootinfo"] = function(shell)
    local path = files.getBootPath(shell.fs.disk)
    shell.term:writeLine(path and ("boot entrypoint: " .. path) or "disk is not bootable")
end

usage["setboot"] = "usage: setboot <entrypoint path>"

builtins["setboot"] = function(shell, args)
    if not args[1] then shell.term:writeLine(usage["setboot"]); return end
    if not files.setBoot(args[1], shell.fs.disk) then
        shell.term:writeLine("setboot: failed (path must exist on the home disk)")
    end
end

usage["peripherals"] = "usage: peripherals"

builtins["peripherals"] = function(shell)
    local ids = io.getPeripherals() or {}
    if #ids == 0 then
        shell.term:writeLine("no peripherals attached")
        return
    end
    for _, id in ipairs(ids) do
        local tag = io.getTag(id)
        shell.term:writeLine(string.format("  %s  [%s]%s", id, io.getType(id),
            (tag and tag ~= "") and (" tag=" .. tag) or ""))
    end
end

local function isIncomplete(err)
    return type(err) == "string" and err:sub(-5) == "<eof>"
end

local function compileLua(source, chunkName)
    local chunk = load("return " .. source, chunkName)
    if chunk then return chunk end
    return load(source, chunkName)
end

local function evalLua(source, chunkName)
    local chunk, cerr = compileLua(source, chunkName)
    if not chunk then
        return nil, cerr, true
    end

    local results = table.pack(pcall(chunk))
    if not results[1] then
        return nil, results[2], false
    end

    local parts = {}
    for i = 2, results.n do
        local v = results[i]
        parts[#parts + 1] = (type(v) == "table") and Util.serialize(v) or tostring(v)
    end
    return parts
end

usage["lua"] = "usage: lua [expression]\n" ..
               "No expression: starts an interactive REPL.\n" ..
               "  .exit or Escape  quit the REPL\n" ..
               "  an unfinished block (do/if/function/...) prompts '>>' for more input"

builtins["lua"] = function(shell, args)
    local term = shell.term

    if #args > 0 then
        local parts, err = evalLua(table.concat(args, " "), "=lua")
        if not parts then
            term:writeLine("lua: " .. tostring(err))
        elseif #parts > 0 then
            term:writeLine(table.concat(parts, "\t"))
        end
        return
    end

    term:writeLine("NeetOS Lua REPL. Type .exit or press Escape to quit.")

    local history = {}
    local buffer = nil

    while true do
        term:setTextColor(Colors.defaultFg)
        term:write(buffer and ">> " or "lua> ")
        term:redraw()

        local line = Util.readLine(term, { history = history, cancelOnEscape = true })
        term:newline()

        if line == nil then break end

        local trimmed = Util.trim(line)
        if not buffer and (trimmed == ".exit" or trimmed == "exit" or trimmed == "quit") then
            break
        end

        if buffer or trimmed ~= "" then
            if not buffer and history[#history] ~= line then
                history[#history + 1] = line
            end

            local source = buffer and (buffer .. "\n" .. line) or line
            local parts, err, isCompileErr = evalLua(source, "=lua")

            if not parts and isCompileErr and isIncomplete(err) then
                buffer = source
            else
                buffer = nil
                if not parts then
                    term:writeLine("lua: " .. tostring(err))
                elseif #parts > 0 then
                    term:writeLine(table.concat(parts, "\t"))
                end
            end
        end

        term:redraw()
    end
end

usage["dump"] = "usage: dump <expression>  (like lua, but always serializes the result)"

builtins["dump"] = function(shell, args)
    local expr = table.concat(args, " ")
    if expr == "" then
        shell.term:writeLine(usage["dump"])
        return
    end
    local chunk, cerr = load("return " .. expr)
    if not chunk then
        shell.term:writeLine("dump: syntax error: " .. tostring(cerr))
        return
    end
    local ok, result = pcall(chunk)
    if not ok then
        shell.term:writeLine("dump: " .. tostring(result))
        return
    end
    shell.term:writeLine(Util.serialize(result))
end

usage["shutdown"] = "usage: shutdown"

builtins["shutdown"] = function(shell)
    shell.term:writeLine("Shutting down...")
    shell.term:redraw()
    chip.shutdown()
end

usage["reboot"] = "usage: reboot"

builtins["reboot"] = function(shell)
    chip.reboot()
end

usage["exit"] = "usage: exit"

builtins["exit"] = function(shell)
    shell.running = false
end

usage["alias"] = "usage: alias [name=command]  (no arguments lists all aliases)"

builtins["alias"] = function(shell, args)
    if #args == 0 then
        local names = {}
        for name in pairs(Config.aliases) do names[#names + 1] = name end
        table.sort(names)
        if #names == 0 then
            shell.term:writeLine("no aliases set")
            return
        end
        for _, name in ipairs(names) do
            shell.term:writeLine(name .. " = " .. Config.aliases[name])
        end
        return
    end

    local spec = table.concat(args, " ")
    local name, value = spec:match("^(%S+)%s*=%s*(.+)$")
    if not name then
        shell.term:writeLine(usage["alias"])
        return
    end
    Config.aliases[name] = value
end

usage["unalias"] = "usage: unalias <name>"

builtins["unalias"] = function(shell, args)
    if not args[1] then shell.term:writeLine(usage["unalias"]); return end
    if not Config.aliases[args[1]] then
        shell.term:writeLine("unalias: no such alias: " .. args[1])
        return
    end
    Config.aliases[args[1]] = nil
end

NeetOS.Shell = Shell
return Shell
