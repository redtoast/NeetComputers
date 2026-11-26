package com.redtoast.neet;

import com.redtoast.NXLua.LuaBridge;
import com.redtoast.NXLua.LuaMaster;
import com.redtoast.simulation.APILoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class BinaryLoader {
    public static boolean loaded = false;
    public static boolean linked = false;

    public static void load(ResourceManager datahandling, Path gamePath) {
        if (loaded) {
            if (linked) NeetComputersServer.registerLanguage(new LuaMaster());
            return;
        }
        loaded = true;
        Map<String, Fork> forks = new Hashtable<>();
        datahandling.findResources("binaries", arg -> {
            if (arg.toString().matches("^neetcomputers:binaries\\/[a-z_0-1\\.]*?-[a-z0-9_\\.]*?\\/bridge\\.(?:dll|so|dylib)$")) {
                String[] bits = arg.toString().split("/");
                String[] canidates = bits[1].split("-");
                String[] osCanidates = canidates[0].split("\\.");
                String[] archCanidates = canidates[1].split("\\.");
                String fileType = bits[2].split("\\.")[1];
                forks.put(bits[1], new Fork(osCanidates, archCanidates, bits[1], fileType));
            }
            return true;
        });
        String os = System.getProperty("os.name").toLowerCase().replaceAll(" ", "_");
        String arch = System.getProperty("os.arch").toLowerCase().replaceAll(" ", "_");
        AtomicReference<Fork> leading = new AtomicReference<>();
        AtomicInteger score = new AtomicInteger(9999);
        forks.forEach((filter, fork) -> {
            if (Arrays.asList(fork.archs).contains(arch)){
                int score2 = 9999;
                for (String name : fork.names) {
                    if (!name.isBlank() && os.matches('^'+name+".*")) {
                        int localScore = os.length() - name.length();
                        if (localScore < score2) score2 = localScore;
                    }
                }
                if (score2 < 10 && score2 < score.get()) {
                    leading.set(fork);
                    score.set(score2);
                }
            }
        });
        try {
            FileUtils.deleteQuietly(gamePath.toFile());
            gamePath.toFile().mkdirs();
            if (leading.get()==null) {
                NeetComputersServer.LOGGER.error("Unable to find NeetComputer lua runtime for platform {}-{}", os, arch);
            }else{
                Fork leader = leading.get();
                String path = "binaries/"+leader.path+"/bridge."+leader.type;
                Path bridge = gamePath.resolve("bridge."+leader.type);
                copy(path, bridge);
                System.load(bridge.toString());
                LuaBridge testInstance = new LuaBridge() {
                    @Override
                    public void print(String text) {
                        NeetComputersServer.LOGGER.info(text);
                    }

                    @Override
                    public void error(String error) {

                    }

                    @Override
                    public void shutDown() {

                    }

                    @Override
                    public void log(String message) {

                    }

                    @Override
                    public boolean isAlive() {
                        return true;
                    }
                };
                if (testInstance.getVersion() != 0){
                    NeetComputersServer.LOGGER.error("Failed to load NeetComputer lua runtime, bridge version incompatible {}-{}", os, arch);
                    testInstance.close();
                    return;
                }
                testInstance.init("print('NXLua loaded successfully!')", 1, 20);
                testInstance.tick();
                testInstance.close();
                linked = true;
                NeetComputersServer.registerLanguage(new LuaMaster());
            }
        } catch (Throwable error) {
            if (error instanceof UnsatisfiedLinkError) {
                NeetComputersServer.LOGGER.error("Failed to link NeetComputer lua runtime for platform {}-{}", os, arch);
                return;
            }
            NeetComputersServer.LOGGER.error("Failed to load NeetComputer lua runtime for platform {}-{} due to error", os, arch);
            APILoader.printJavaError(error);
        }
    }

    private static void copy(String from, Path to) throws IOException {
        byte[] reader = NeetComputersServer.datahandling.getResource(Identifier.of("neetcomputers", from)).get().getInputStream().readAllBytes();
        FileOutputStream outputStream = new FileOutputStream(to.toFile(),false);
        outputStream.write(reader);
        outputStream.close();
    }

    private record Fork(String[] names, String[] archs, String path, String type) {}
}
