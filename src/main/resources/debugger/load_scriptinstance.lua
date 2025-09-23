(function(__obj)
    local function clone(tbl) local copy = {} for k,v in pairs(tbl) do copy[k] = v end return copy end
    local debugLib = debug if type(debugLib) ~= 'table' or type(debugLib.getregistry) ~= 'function' then return nil end
    local rawGetMetatable = debugLib and debugLib.getmetatable or getmetatable
    local mt = rawGetMetatable(__obj) if type(mt) ~= 'table' then return nil end
    local getter = mt.__get_instance_data_table_ref
    if type(getter) ~= 'function' then return nil end
    local ok, ref = pcall(getter, __obj) if not ok or not ref then return nil end
    local registry = debugLib.getregistry() if type(registry) ~= 'table' then return nil end
    local tbl = registry[ref] if type(tbl) ~= 'table' then return nil end
    return clone(tbl)
end)({{BASE_EXPR}})