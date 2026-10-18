package com.redtoast.simulation.FS;

import com.redtoast.simulation.FS.FileImplementations.*;

public class FileHelper {
    public static Filepath getFile(DiskSystem fs, String path){
        String normalizedPath = normalize(path);
        if (validatePathStatic(normalizedPath)){
            String[] components = normalizedPath.split(":");
            if (fs.partitionExists(components[0])){
                Partition partition = fs.getPartition(components[0]);
                if (path.toLowerCase().equals(path)){
                    DataFilepath dataFilepath = new DataFilepath(partition.source(), normalizedPath, fs);
                    if (partition.readOnly()){
                        return dataFilepath;
                    }else{
                        RealFilepath realFilepath = new RealFilepath(fs.basePath.resolve(components[0]), normalizedPath, fs);
                        return new LayeredFilepath(realFilepath, dataFilepath, fs);
                    }
                }else{
                    return new RealFilepath(fs.basePath.resolve(components[0]), normalizedPath, fs);
                }
            }else{
                return new NullFilepath(normalizedPath);
            }
        }else{
            return new NullFilepath(normalizedPath);
        }
    }

    public static String normalize(String path){
        String[] secs = path.split(":");
        if (secs.length==0){
            return ":\\";
        }
        if (secs.length==1){
            return path+"\\";
        }
        StringBuilder buffer = new StringBuilder(secs[secs.length-1]);
        buffer.insert(0, '\\');
        while (buffer.indexOf("/")!=-1){
            buffer.replace(buffer.indexOf("/"), buffer.indexOf("/")+1, "\\");
        }
        while (buffer.indexOf("\\\\")!=-1){
            buffer.replace(buffer.indexOf("\\\\"), buffer.indexOf("\\\\")+2, "\\");
        }
        for (int i = 0; i < secs.length - 1; i++) {
            buffer.insert(0, ':');
            buffer.insert(0, secs[i]);
        }
        return buffer.toString();
    }

    public static String deAbsolutize(String path){
        String[] parts = path.split(":");
        return parts[parts.length-1];
    }

    public static boolean isAbsolute(String path){
        String[] parts = path.split(":");
        if (parts.length<2){
            return false;
        }
        return !parts[0].isBlank();
    }

    public static boolean validatePathStatic(String path){
        path = path.replace('\\', '/');
        if (path.charAt(path.length()-1)!='/') path += '/';
        return path.matches("^([a-zA-Z]+\\:\\/?)([a-zA-Z_\\-\\s0-9\\.]*[a-zA-Z_\\-\\s0-9]\\/)*$");
    }

    public static OpeningMode getMode(String mode){
        if (mode.length()>2) return new OpeningMode(false, false, false, false, false,true);
        if (mode.isBlank()) return new OpeningMode(false, false, false, false, false,true);
        mode = mode.toLowerCase();
        if (mode.equals("r")){
            return new OpeningMode(true, false, false, false, false,false);
        }else if (mode.equals("w")){
            return new OpeningMode(false, true, true, false, true,false);
        }else if(mode.equals("a")){
            return new OpeningMode(false, true, false, false, true,false);
        }else if(mode.equals("r+")){
            return new OpeningMode(true, true, false, false, false,false);
        }else if(mode.equals("w+")){
            return new OpeningMode(true, true, true, false, true,false);
        }else if(mode.equals("a+")){
            return new OpeningMode(true, true, false, false, true,false);
        }else if(mode.equals("rb")){
            return new OpeningMode(true, false, false, true, false,false);
        }else if(mode.equals("wb")){
            return new OpeningMode(false, true, true, true, true,false);
        }else if(mode.equals("ab")){
            return new OpeningMode(false, true, false, true, true,false);
        }else if(mode.equals("rb+") || mode.equals("r+b")){
            return new OpeningMode(true, true, false, true, false,false);
        }else if(mode.equals("wb+") || mode.equals("w+b")){
            return new OpeningMode(true, true, true, true, true,false);
        }else if(mode.equals("ab+") || mode.equals("a+b")){
            return new OpeningMode(true, true, false, true, true,false);
        }else{
            return new OpeningMode(false, false, false, false, false,true);
        }
    }
}
