package com.redtoast.simulation.FS;

import com.redtoast.neet.NeetComputersServer;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public record DataNode(String address, boolean isDirectory, String name, String path, DataNode[] nodes) {
    private static final Logger logger = LoggerFactory.getLogger("Neetcomputers-datascanning");
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

    public BufferedReader getTextReader() {
        try {
            return NeetComputersServer.datahandling.getResource(
                    Identifier.of("neetcomputers", "neet/hard_addresses/" + address + "/" + path)).get().getReader();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public InputStream getInputStream() {
        try {
            return NeetComputersServer.datahandling.getResource(
                    Identifier.of("neetcomputers", "neet/hard_addresses/" + address + "/" + path)).get().getInputStream();
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
                if (node.name().equals(parts[i])) {
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
        for (DataConstructorNode node : space) if (node.name().equals(index)) return node;
        space.add(new DataConstructorNode(address, index, new LinkedList<>()));
        return space.getLast();
    }

    public static void loadData(){
        Hashtable<String, List<DataConstructorNode>> table = new Hashtable<>();
        NeetComputersServer.datahandling.findResources("neet/hard_addresses", arg -> {
            String[] parts = arg.toString().split("/");
            if (!table.containsKey(parts[2])) table.put(parts[2], new ArrayList<>());
            List<DataConstructorNode> space = table.get(parts[2]);
            for (int i = 3; i < parts.length; i++){
                space = spaceGet(parts[2], space, parts[i]).nodes();
            }
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
    }
}
