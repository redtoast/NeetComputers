package com.redtoast.simulation.FS;

import com.google.gson.*;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.base.LanguageGeneric;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;

public class DiskTable {
    public final LinkedList<Partition> partitions = new LinkedList<>();
    public String entrypoint = null;
    public @Nullable LanguageGeneric language = null;
    public final boolean isLua;

    public DiskTable(String json) throws DiskError, JsonSyntaxException{
        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()){
            throw new DiskError("Json must be object");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("entrypoint")){
            if (!object.get("entrypoint").isJsonPrimitive() || !object.getAsJsonPrimitive("entrypoint").isString()){
                throw new DiskError("Build config 'entrypoint' expected string");
            }
            entrypoint = object.get("entrypoint").getAsString();
            if (object.has("language")){
                if (!object.get("language").isJsonPrimitive() || !object.getAsJsonPrimitive("language").isString()){
                    throw new DiskError("Build config 'language' expected string");
                }
                isLua = object.get("language").getAsString().equals("Lua");
                language = NeetComputersServer.getLanguage(object.get("language").getAsString());
            }else{
                isLua = true;
                language = NeetComputersServer.getLanguage("Lua");
            }
        }else
            isLua = false;
        if (!object.has("partitions")){
            throw new DiskError("Build config expected partitions, got null");
        }
        if (!object.get("partitions").isJsonArray()){
            throw new DiskError("Build config 'partitions' must be array");
        }
        JsonArray partitionsArray = object.getAsJsonArray("partitions");
        for (JsonElement partition : partitionsArray.asList()){
            if (!partition.isJsonObject()){
                throw new DiskError("Build config expected partition as object, got '" + partition + '\'');
            }
            JsonObject partobject = partition.getAsJsonObject();
            if (!partobject.has("path")){
                throw new DiskError("Build config expected partition 'path', got null");
            }
            String path = partobject.get("path").getAsString();
            boolean readonly = false;
            boolean hidden = false;
            String source = "";

            if (partobject.has("readonly") && !partobject.get("readonly").isJsonPrimitive() || !partobject.getAsJsonPrimitive("readonly").isBoolean()){
                throw new DiskError("Build config expected 'readonly' boolean, got "+partobject.get("readonly"));
            }else if(partobject.has("readonly")){
                readonly = partobject.getAsJsonPrimitive("readonly").getAsBoolean();
            }

            if (partobject.has("hidden") && !partobject.get("hidden").isJsonPrimitive() || !partobject.getAsJsonPrimitive("hidden").isBoolean()){
                throw new DiskError("Build config expected 'hidden' boolean, got "+partobject.get("hidden"));
            }else if(partobject.has("hidden")){
                hidden = partobject.getAsJsonPrimitive("hidden").getAsBoolean();
            }

            if (partobject.has("source") && !partobject.get("source").isJsonPrimitive()){
                throw new DiskError("Build config expected 'source' String/hard-address, got "+partobject.get("source"));
            }else if(partobject.has("source")){
                if (partobject.getAsJsonPrimitive("source").isNumber()){
                    int number = partobject.getAsJsonPrimitive("source").getAsInt();
                    /*for legacy pre 0.5.6 source values*/
                    source = switch (number) {
                        case -1 -> "neetos";
                        case -797 -> "meowintosh";
                        case -6661 -> "xes_bios";
                        case -6662 -> "xes_xes";
                        case -6663 -> "xes_system";
                        default -> throw new DiskError("Build config tried to convert legacy address, got "+partobject.get("source"));
                    };
                }else{
                    source = partobject.getAsJsonPrimitive("source").getAsString();
                }
            }

            partitions.add(new Partition(path, readonly, hidden, source));
        }
        if (isBootable()) {
            String path = FileHelper.normalize(entrypoint);
            if (!FileHelper.isAbsolute(path) || !FileHelper.validatePathStatic(path)){
                throw new DiskError("Build config expected 'entrypoint' <partition path>:<address path>.<file extension>");
            }

            boolean check = false;
            String root = path.split(":")[0];
            for (Partition partition : partitions){
                if (partition.path().equals(root)) {
                    check = true;
                    break;
                }
            }
            if (!check) throw new DiskError("Build config 'entrypoint' doesn't refer to an assigned partition");
        }
    }

    public String toJson() {
        JsonObject object = new JsonObject();
        if (isBootable()) {
            object.addProperty("entrypoint", entrypoint);
            object.addProperty("language", language.getName());
        }
        JsonArray partitions = new JsonArray();
        for (Partition partition : this.partitions) {
            JsonObject partitionObj = new JsonObject();
            partitionObj.addProperty("path", partition.path());
            if (partition.source().isEmpty()) partitionObj.addProperty("source", partition.source());
            partitionObj.addProperty("readonly", partition.readOnly());
            partitionObj.addProperty("hidden", partition.hidden());
            partitions.add(partitionObj);
        }
        object.add("partitions", partitions);
        return object.toString();
    }

    public boolean isBootable() {
        return entrypoint!=null && language!=null;
    }
}