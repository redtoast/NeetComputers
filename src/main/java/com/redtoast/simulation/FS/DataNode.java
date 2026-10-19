package com.redtoast.simulation.FS;

import com.redtoast.neet.NeetComputersServer;
import net.minecraft.util.Identifier;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public record DataNode(String address, boolean isDirectory, String name, String path, DataNode[] nodes) {
    public static Map<String, DataNode[]> nodeTree = null;

    public String[] getChildren() {
        String[] children = new String[nodes.length];
        for (int i = 0; i < nodes.length; i++) children[i] = nodes[i].name();
        return children;
    }

    public String[] getChildren(List<String> blacklist) {
        if (blacklist.isEmpty()) return getChildren();
        List<String> children = new LinkedList<>();
        for (DataNode node : nodes) if (!blacklist.contains(node.name())) children.add(node.name());
        return children.toArray(new String[] {});
    }

    public String getText() {
//        try {
//            return NeetComputersServer.datahandling.getResource(
//                    Identifier.of("neetcomputers", "neet/hard_addresses/" + address + "/" + path)).get().getReader();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        try {
            TarArchiveInputStream archive = new TarArchiveInputStream(NeetComputersServer.datahandling.getResource(Identifier.of("neetcomputers", "neet/hard_addresses/"+address)).get().getInputStream());
            TarArchiveEntry ze;
            while ((ze = archive.getNextEntry()) != null) {
                if (ze.getName().equalsIgnoreCase(path)) {
                    byte[] buffer = new byte[(int) ze.getSize()];
                    archive.read(buffer, 0, (int) ze.getSize());
                    return StringUtils.toEncodedString(buffer, null);
                }
            }
            throw new RuntimeException("Cant find path ("+address+':'+path+')');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] getBytes() {
//        try {
//            return NeetComputersServer.datahandling.getResource(
//                    Identifier.of("neetcomputers", "neet/hard_addresses/" + address + "/" + path)).get().getInputStream();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        try {
            TarArchiveInputStream archive = new TarArchiveInputStream(NeetComputersServer.datahandling.getResource(Identifier.of("neetcomputers", "neet/hard_addresses/"+address)).get().getInputStream());
            TarArchiveEntry ze;
            while ((ze = archive.getNextEntry()) != null) {
                if (ze.getName().equalsIgnoreCase(path)) {
                    byte[] buffer = new byte[(int) ze.getSize()];
                    archive.read(buffer, 0, (int) ze.getSize());
                    return buffer;
                }
            }
            throw new RuntimeException("Cant find path ("+address+':'+path+')');
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static @Nullable DataNode getNode(String address, String path) {
        assert nodeTree != null;
        if (!nodeTree.containsKey(address)) return null;
        if (path.isEmpty()) {
            return new DataNode(address, true, "", "/", nodeTree.get(address));
        }
        DataNode[] space = nodeTree.get(address);
        String[] parts = path.replaceFirst("[/\\\\]$","").split("[/\\\\]");
        boolean startingSlash = path.charAt(0) == '/' || path.charAt(0) == '\\';
        for (int i = startingSlash ? 1 : 0; i < parts.length; i++) {
            DataNode found = null;
            for (DataNode node : space) {
                if (node.name().equalsIgnoreCase(parts[i])) {
                    found = node;
                    break;
                }
            }
            if (found == null) return null;
            if (i == parts.length-1) return found;
            space = found.nodes();
        }
        return null;
    }

    private record DataConstructorNode(String address, String name, List<DataConstructorNode> nodes){
        DataNode process(String prefix){
            String path = prefix + name;
            DataNode[] subNodes = new DataNode[nodes.size()];
            for (int i = 0; i < subNodes.length; i++) {
                subNodes[i] = nodes.get(i).process(path+'/');
            }
            return new DataNode(address, !nodes.isEmpty(), name, path, subNodes);
        }
    }

    private static DataConstructorNode spaceGet(String address, List<DataConstructorNode> space, String index) {
        for (DataConstructorNode node : space)
            if (node.name().equals(index))
                return node;
        space.add(new DataConstructorNode(address, index, new LinkedList<>()));
        return space.getLast();
    }

    public static void loadData(){
        Hashtable<String, List<DataConstructorNode>> table = new Hashtable<>();
        NeetComputersServer.datahandling.findResources("neet/hard_addresses", arg -> {
            if (arg.toString().endsWith(".tar")) {
                String address = arg.toString().replaceFirst("neetcomputers:neet/hard_addresses/", "");
                TarArchiveInputStream archive;
                try {
                    archive = new TarArchiveInputStream(NeetComputersServer.datahandling.getResource(Identifier.of("neetcomputers", "neet/hard_addresses/"+address)).get().getInputStream());
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                List<DataConstructorNode> space = new ArrayList<>();
                try{
                    TarArchiveEntry ze;
                    while ((ze = archive.getNextEntry()) != null) {
                        String path = ze.getName();
                        if (!ze.isDirectory()) {
                            String[] parts = path.split("/");
                            List<DataConstructorNode> subSpace = space;
                            for (int i = 0; i < parts.length; i++){
                                //space = spaceGet(address, space, parts[i]).nodes();
                                subSpace = spaceGet(address, subSpace, parts[i]).nodes();
                            }
                        }
                    }
                    archive.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                table.put(address, space);
            }
//            String[] parts = arg.toString().split("/");
//            if (!table.containsKey(parts[2])) table.put(parts[2], new ArrayList<>());
//            List<DataConstructorNode> space = table.get(parts[2]);
//            for (int i = 3; i < parts.length; i++){
//                space = spaceGet(parts[2], space, parts[i]).nodes();
//            }
            return true;
        });
        Hashtable<String, DataNode[]> product = new Hashtable<>();
        table.forEach((key, nodes) -> {
            DataNode[] subNodes = new DataNode[nodes.size()];
            for (int i = 0; i < subNodes.length; i++) {
                subNodes[i] = nodes.get(i).process("");
            }
            product.put(key, subNodes);
        });
        nodeTree = Map.copyOf(product);
//        try{
//            TarArchiveInputStream zis = new TarArchiveInputStream(NeetComputersServer.datahandling.getResource(
//                    Identifier.of("neetcomputers", "neet/hard_addresses/neetos.tar")).get().getInputStream());
//
//            TarArchiveEntry ze;
//
//            while ((ze = zis.getNextEntry()) != null) {
//
//                System.out.format("File: "+ze.getName());
//            }
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
    }
}
