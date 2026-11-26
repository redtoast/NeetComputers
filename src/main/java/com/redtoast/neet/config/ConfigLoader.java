package com.redtoast.neet.config;

import net.minecraft.client.MinecraftClient;
import net.minecraft.server.MinecraftServer;

import java.io.File;
import java.io.FileWriter;
import java.util.Scanner;

public class ConfigLoader {
    private static final ConfigurationTable serverConfigurationTable = new ConfigurationTable(new ConfigOption[]{
            new IntegerConfigOption(1, "processing-threads"),
            new CompatibilityConfigOption(true, "cct-compatibility"),
            new BooleanConfigOption(false, "print-to-console"),
            new BooleanConfigOption(false, "log-system-notifications"),
            new BooleanConfigOption(true, "shift-click-to-clear-displays"),
            new BooleanConfigOption(true, "allow-internet-access"),
            new RateConfigOption(4000, "internet-rate-limit"),
            new IntegerConfigOption(15, "internet-outgoing-buffer-size"),
            new IntegerConfigOption(5, "internet-max-sockets"),
            new RateConfigOption(10 * 1024 * 1024, "disk-capacity")
    });
    private static final ConfigurationTable clientConfigurationTable = new ConfigurationTable(new ConfigOption[]{
            new BooleanConfigOption(true, "computers-emit-light")
    });

    public static Object getServerConfig(String key) {
        return serverConfigurationTable.getSetting(key);
    }

    public static void loadServerConfig(MinecraftServer server) {
        File config = server.getPath("config").resolve("NEET-server.properties").toFile();
        serverConfigurationTable.reset();
        if (config.exists()){
            try {
                Scanner scanner = new Scanner(config);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isEmpty() && line.charAt(0) != '#') {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            serverConfigurationTable.attemptPush(parts[0], parts[1]);
                        }
                    }
                }
                scanner.close();
            }catch (Exception ignored){}
        }else{
            try {
                config.createNewFile();
                FileWriter writer = new FileWriter(config, false);
                writer.write("""
                        #allows players to shift click peripheral displays to clear them
                        shift-click-to-clear-displays = true
                        #determines how many threads the server will create to manage computing, more threads is optimal on CPU's with extra cores to spare
                        processing-threads = 1
                        #allows the default core print function to echo to the game console, meant for debugging
                        print-to-console = false
                        #allows NEET error / utility messages to print to console
                        log-system-notifications = false
                        #allows computers to connect to the internet
                        allow-internet-access = true
                        #internet rate limit per second, 0 disables the limit (in bytes not bits)
                        internet-rate-limit = 4kb
                        #amount of requests a computer can have in buffer at once
                        internet-outgoing-buffer-size = 15
                        #amount of sockets that can be live on a computer at once
                        internet-max-sockets = 5
                        #maximum size of a disk, in bytes, accepts kb/mb/gb/tb suffixes
                        disk-capacity = 10mb
                        #allows experimental compatibility features
                        CCT-compatibility = true
                        """);
                writer.close();
            }catch (Exception ignored){}
        }
    }

    public static Object getClientConfig(String key) {
        return clientConfigurationTable.getSetting(key);
    }

    public static void loadClientConfig(MinecraftClient client) {
        File config = client.getResourcePackDir().resolve("../config/NEET-client.properties").toFile();
        clientConfigurationTable.reset();
        if (config.exists()){
            try {
                Scanner scanner = new Scanner(config);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isEmpty() && line.charAt(0) != '#') {
                        String[] parts = line.split("=");
                        if (parts.length == 2) {
                            clientConfigurationTable.attemptPush(parts[0], parts[1]);
                        }
                    }
                }
                scanner.close();
            }catch (Exception ignored){}
        }else{
            try {
                config.createNewFile();
                FileWriter writer = new FileWriter(config, false);
                writer.write("""
                        #when true, active computers will emit faint light
                        computers-emit-light = true
                        """);
                writer.close();
            }catch (Exception ignored){}
        }
    }
}
