-- os/fs.lua
-- A small convenience layer on top of NEET's built-in `files` API.

local Fs = {}
Fs.__index = Fs

local function splitPath(full)
    local partition, rest = full:match("^([%w_%-]+):(/.*)$")
    return partition, rest
end

local function splitSegments(path)
    local segs = {}
    for seg in path:gmatch("[^/]+") do
        segs[#segs + 1] = seg
    end
    return segs
end

local function normalize(partition, path)
    local segsIn = splitSegments(path)
    local segsOut = {}
    for _, seg in ipairs(segsIn) do
        if seg == "." then
            -- no-op
        elseif seg == ".." then
            if #segsOut > 0 then table.remove(segsOut) end
        else
            segsOut[#segsOut + 1] = seg
        end
    end
    local out = partition .. ":/" .. table.concat(segsOut, "/")
    return out
end

function Fs.new()
    local self = setmetatable({ disk = 0 }, Fs)
    self:resetCwd()
    return self
end

local function partitionName(entry)
    if type(entry) == "table" then return entry.name end
    return entry
end

function Fs:resetCwd()
    local raw = files.getPartitions(self.disk) or {}
    local parts = raw[1] or raw
    local chosen = nil
    for _, entry in ipairs(parts) do
        local name = partitionName(entry)
        if name == "bios" then chosen = name break end
    end
    chosen = chosen or partitionName(parts[1]) or "bios"
    self.cwd = chosen .. ":/"
end

function Fs:setDisk(disk)
    self.disk = disk
    self:resetCwd()
end

function Fs:pwd()
    return self.cwd
end

function Fs:resolve(input)
    if input == nil or input == "" then
        return self.cwd
    end

    local partition, rest = splitPath(input)
    if partition then
        return normalize(partition, rest)
    end

    local curPartition, curRest = splitPath(self.cwd)

    if input:sub(1, 1) == "/" then
        return normalize(curPartition, input)
    end

    local combined = curRest
    if combined:sub(-1) ~= "/" then combined = combined .. "/" end
    combined = combined .. input
    return normalize(curPartition, combined)
end

function Fs:cd(input)
    local target = self:resolve(input)
    if not files.exists(target, self.disk) then
        return false, "no such path: " .. target
    end
    if not files.isDir(target, self.disk) then
        return false, "not a directory: " .. target
    end
    self.cwd = target
    if self.cwd:sub(-1) ~= "/" then self.cwd = self.cwd .. "/" end
    return true
end

function Fs:exists(input) return files.exists(self:resolve(input), self.disk) end
function Fs:isFile(input) return files.isFile(self:resolve(input), self.disk) end
function Fs:isDir(input) return files.isDir(self:resolve(input), self.disk) end

function Fs:list(input)
    local target = self:resolve(input)
    if not files.exists(target, self.disk) then
        return nil, "no such path: " .. target
    end
    if not files.isDir(target, self.disk) then
        return nil, "not a directory: " .. target
    end
    return files.getChildren(target, self.disk)
end

function Fs:mkdir(input)
    return files.makeDir(self:resolve(input), self.disk)
end

function Fs:delete(input)
    return files.delete(self:resolve(input), self.disk)
end

function Fs:readAll(input)
    local target = self:resolve(input)
    local header, err = files.open(target, "r", self.disk)
    if not header then return nil, err or ("could not open " .. target) end
    local data = header.read("a")
    header.close()
    return data or ""
end

function Fs:writeAll(input, contents)
    local target = self:resolve(input)
    local header, err = files.open(target, "w", self.disk)
    if not header then return false, err or ("could not open " .. target) end
    header.write(contents)
    header.flush()
    header.close()
    return true
end

function Fs:copy(srcInput, dstInput)
    local data, err = self:readAll(srcInput)
    if not data then return false, err end
    return self:writeAll(dstInput, data)
end

function Fs:deleteRecursive(input)
    local target = self:resolve(input)
    if not files.exists(target, self.disk) then
        return false, "no such path: " .. target
    end
    if self:isDir(target) then
        local children, err = self:list(target)
        if not children then return false, err end
        local prefix = target:sub(-1) == "/" and target or (target .. "/")
        for _, child in ipairs(children) do
            local ok, cerr = self:deleteRecursive(prefix .. child)
            if not ok then return false, cerr end
        end
    end
    return files.delete(target, self.disk)
end

function Fs:copyRecursive(srcInput, dstInput)
    local src = self:resolve(srcInput)
    local dst = self:resolve(dstInput)
    if not files.exists(src, self.disk) then
        return false, "no such path: " .. src
    end
    if self:isDir(src) then
        if not files.exists(dst, self.disk) then
            if not self:mkdir(dst) then
                return false, "could not create " .. dst
            end
        end
        local children, err = self:list(src)
        if not children then return false, err end
        local srcPrefix = src:sub(-1) == "/" and src or (src .. "/")
        local dstPrefix = dst:sub(-1) == "/" and dst or (dst .. "/")
        for _, child in ipairs(children) do
            local ok, cerr = self:copyRecursive(srcPrefix .. child, dstPrefix .. child)
            if not ok then return false, cerr end
        end
        return true
    end
    return self:copy(src, dst)
end

function Fs:move(srcInput, dstInput)
    local src = self:resolve(srcInput)
    local ok, err = self:copyRecursive(src, dstInput)
    if not ok then return false, err end
    return self:deleteRecursive(src)
end

NeetOS.Fs = Fs
return Fs
