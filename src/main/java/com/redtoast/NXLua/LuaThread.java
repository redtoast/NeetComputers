package com.redtoast.NXLua;

import com.redtoast.neet.NeetComputersServer;
import com.redtoast.neet.config.ConfigLoader;
import com.redtoast.simulation.Runtime;
import com.redtoast.simulation.base.LangThread;
import com.redtoast.simulation.base.LanguageGeneric;
import com.redtoast.simulation.config.ComputerConfig;
import com.redtoast.simulation.value.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuaThread extends LangThread {
    private final Logger logger;
    private final LuaBridge bridge;
    private final LuaMaster translater;

    public LuaThread(String script, Runtime parentRuntime, ComputerConfig computerConfig) {
        logger = LoggerFactory.getLogger("Lua Runtime [" + parentRuntime.getParent().getUuid() + ']');
        LuaThread self = this;
        bridge = new LuaBridge() {
            @Override
            public void print(String text) {
                if (!(boolean) ConfigLoader.getServerConfig("print-to-console")) return;
                logger.info(text);
            }

            @Override
            public void error(String error) {
                self.error(error);
                kill(error);
                close();
            }

            @Override
            public void shutDown() {
                kill();
                close();
            }

            @Override
            public void log(String message) {
                self.log(message);
            }

            @Override
            public boolean isAlive() {
                if (parentRuntime.getParent().isCrashed()) {
                    shutDown();
                } else if (parentRuntime.shouldDie()) {
                    shutDown();
                    parentRuntime.getParent().stop();
                }
                return self.isAlive() && parentRuntime.getParent().isOn();
            }
        };
        translater = (LuaMaster) NeetComputersServer.getLanguage("Lua");
        parentRuntime.loader.load(parentRuntime.getParent(), this);

        bridge.setGlobal("bit32", Bit32Compat.build());

        bridge.init(script, computerConfig.instructionsPerBatch(), computerConfig.batchesPerTick());
    }

    @Override
    public LanguageGeneric getLang() {
        return translater;
    }

    @Override
    public String getErrorMessage() {
        if (super.getErrorMessage()==null) return super.getErrorMessage();
        return super.getErrorMessage().replaceFirst("^\\[string \"Lua\"]", "Lua");
    }

    @Override
    public void yield() {
        bridge.yield();
    }

    @Override
    public void tick() {
        bridge.tick();
    }

    @Override
    public String getSource() {
        return "[Lua]";
    }

    @Override
    public void insert(String key, Value value) {
        bridge.setGlobal(key, translater.fromValue(value));
    }
}
