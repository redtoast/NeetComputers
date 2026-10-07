package com.redtoast.simulation;

import com.redtoast.Computer;
import com.redtoast.neet.NeetComputersServer;
import com.redtoast.simulation.annotations.*;
import com.redtoast.simulation.base.*;
import com.redtoast.simulation.cache.LoaderCache;
import com.redtoast.simulation.cache.PackedFunctionCache;
import com.redtoast.simulation.cache.StaticFunctionCache;
import com.redtoast.simulation.parameterErrors.ParameterException;
import com.redtoast.simulation.value.Value;
import com.redtoast.simulation.value.ValueTypes.Exception;
import com.redtoast.simulation.value.ValueTypes.*;
import dan200.computercraft.api.lua.LuaException;
import net.minecraft.util.crash.CrashException;
import net.minecraft.util.crash.CrashReport;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class APILoader {
    private final Runtime ParentRuntime;
    private static final Logger profiler = LoggerFactory.getLogger("NeetComputers: Profiler");
    public static final Logger errorLogger = LoggerFactory.getLogger("NeetComputers: Runtime Java Errors");
    private static final Hashtable<Class<? extends Exposable>, LoaderCache> cache = new Hashtable<>();
    private static final LinkedList<APIRegistry> APIs = new LinkedList<>();

    public static class LoaderError extends Throwable {
        public final String message;
        public LoaderError(String message){
            this.message = message;
        }
    }

    /**
     * APILoader instances are created by individual Runtime's
     */
    public APILoader(Computer computer){
        ParentRuntime = computer.getRuntime();
        ParentRuntime.loader = this;
    }

    public static boolean preemptiveCache(Class<? extends Exposable> clazz){
        if (cache.containsKey(clazz)) return false;
        try{
            Hashtable<String, StaticFunctionCache> functions = new Hashtable<>();
            Hashtable<String, PackedFunctionCache> packedFunctions = new Hashtable<>();
            Method[] buffer = clazz.getMethods();
            for (Method method : buffer){
                if (method.isAnnotationPresent(Exposed.class)){
                    Parameters ruleset = Parameters.deduceParameters(method.getParameters());
                    String funcname = method.getAnnotation(Exposed.class).nameOverride().isBlank() ? method.getName() : method.getAnnotation(Exposed.class).nameOverride();
                    StaticFunctionCache function = new StaticFunctionCache(method, ruleset , funcname);
                    if (functions.containsKey(funcname)){
                        Set<StaticFunctionCache> set = Set.of(functions.get(funcname), function);
                        packedFunctions.put(funcname, new PackedFunctionCache(funcname, new LinkedList<>(set)));
                        functions.remove(funcname);
                    }else {
                        if (packedFunctions.containsKey(funcname)){
                            packedFunctions.get(funcname).functions.add(function);
                        }else{
                            functions.put(funcname, function);
                        }
                    }
                }
            }
            cache.put(clazz, new LoaderCache(functions.values(), packedFunctions.values()));
        }catch (IllegalArgumentException loaderError){
            throw new CrashException(new CrashReport(loaderError.getMessage(), loaderError));
        }
        return true;
    }

    //added a APIRegistery object to the static list of API's to instanciate
    public static void register(APIRegistry registry){
        APIs.add(registry);
    }

    public void load(Computer computer, LangThread thread){
        try{
            for (APIRegistry registry : APIs){
                if (registry.predicate(computer)){
                    API api = registry.Create(computer);
                    Function[] functions = translateAPI(api, ParentRuntime);
                    String label = api.getLabel();

                    Table apiTable = new Table();
                    for (Function func : functions){
                        if (func.getName()!=null){
                            apiTable.put(func.getName(), func.asValue());
                        } else {
                            throw new LoaderError("Issue encountered loading api '"+label+"': nameless function (try .setName on runtime implemented functions)");
                        }
                    }
                    api.postProcessing(apiTable);
                    thread.insert(label, apiTable.asValue());
                }
            }
        }catch (LoaderError loaderError){
            throw new CrashException(new CrashReport(loaderError.message, loaderError));
        }
    }

    public static Table TableizeAPI(Exposable exposable, @Nullable Runtime runtime){
        try{
            Function[] functions = translateAPI(exposable, runtime);
            Table apiTable = new Table();
            for (Function func : functions){
                if (func.getName()!=null){
                    apiTable.put(func.getName(), func.asValue());
                }else{
                    throw new LoaderError("Issue encountered loading api '"+exposable.getClass().getName()+"': nameless function (try .setName on runtime implemented functions)");
                }
            }
            return exposable.postProcessing(apiTable);
        }catch (LoaderError loaderError){
            throw new CrashException(new CrashReport(loaderError.message, loaderError));
        }
    }

    public static void profilerFunction(long startTime, String function, @Nullable Context context){
        short timeSpent = (short) (System.currentTimeMillis() - startTime);
        if (timeSpent>100){
            if (NeetComputersServer.DO_LOGGING) profiler.warn(function +" exceeded 100 milliseconds ("+timeSpent+")");
            if (context!=null && context.runtime.getThread()!=null){
                context.runtime.getThread().taxJavaLag((short) 1000);
            }
        }
        if (context!=null && context.runtime.getThread()!=null){
            context.runtime.getThread().taxJavaLag(timeSpent);
        }
        if (timeSpent>10 && context!=null && context.runtime.getThread()!=null){
            context.runtime.getThread().yield();
        }
    }

    public record Context(Runtime runtime, LanguageGeneric language){}

    public static Function sandboxFunction(Method method, Object obj, Parameters ruleset, Runtime runtime){
        String funcname = method.isAnnotationPresent(Exposed.class) ? method.getAnnotation(Exposed.class).nameOverride().isBlank() ? method.getName() : method.getAnnotation(Exposed.class).nameOverride() : null;
        Function temp = new Function(false, ruleset) {
            @Override
            public Value call(Value<?>[] parameters) {
                try {
                    if (runtime!=null && obj instanceof Exposable exposable){
                        if (runtime.isDead()) return Value.asError("Attempt to call function from killed runtime (how did you get here)");
                        exposable.onCall(runtime, method);
                    }
                    long timeStarted = System.currentTimeMillis();
                    Context context = runtime!=null ? new Context(runtime, runtime.getThread().getLang()) : null;
                    Object retun = method.invoke(obj, ruleset.cast(parameters));
                    if (context!=null) profilerFunction(timeStarted, funcname + ruleset, context);
                    return processReturn(retun);
                }catch (InvocationTargetException e){
                    if (e.getTargetException() instanceof ExposedError error) {
                        return Value.asError(error.getMessage());
                    }
                    if (e.getTargetException() instanceof LuaException error) {
                        return Value.asError(error.getMessage());
                    }
                    if (e.getTargetException() instanceof ParameterException parameterException) {
                        return Value.of(runtime==null ? "Language Missing #"+parameterException.getPosition()+":\n"+getRules() : runtime.getThread().getLang().generateError(parameterException)+":\n"+getRules());
                    }
                    printJavaError(e.getTargetException());
                    return Value.asError(describeUnexpectedError(e.getTargetException()));
                }catch (java.lang.Exception e){
                    printJavaError(e);
                    return Value.asError(describeUnexpectedError(e));
                }
            }
        };
        temp.setName(funcname);
        return temp;
    }

    public static Value<?> processReturn(Object obj) {
        if (obj==null){
            return Value.NULL;
        }else{
            return Value.of(obj);
        }
    }

    public static Function[] packFunctions(Hashtable<String, LinkedList<Function>> functions, Runtime runtime){
        Function[] output = new Function[functions.size()];
        AtomicInteger i = new AtomicInteger();
        functions.forEach((key, values) -> {
            if (values.size()==1){
                output[i.get()] = Parameters.attachErrors(values.getFirst(), runtime);
            }else{
                output[i.get()] = Parameters.attachSelectiveErrors(values, key, runtime);
            }
            i.getAndIncrement();
        });
        return output;
    }

    public static String describeException(Throwable error){
        StringBuilder builder = new StringBuilder();
        Throwable current = error;
        while (current != null){
            if (!builder.isEmpty()) builder.append(" <- caused by <- ");
            builder.append(current.getClass().getSimpleName());
            if (current.getMessage() != null && !current.getMessage().isBlank()){
                builder.append(" (").append(current.getMessage()).append(')');
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    public static String describeUnexpectedError(Throwable error){
        return "Internal error: " + describeException(error);
    }

    public static String generateJavaErrorLog(Throwable error){
        StringBuilder builder = new StringBuilder();
        builder.append("Internal error: ").append(describeException(error)).append('\n');

        Throwable deepest = error;
        while (deepest.getCause() != null) deepest = deepest.getCause();

        StackTraceElement[] trace = deepest.getStackTrace();
        if (trace.length == 0){
            builder.append("  (no stack trace available)");
        } else {
            builder.append("Where:\n");
            int shown = Math.min(trace.length, 10);
            for (int i = 0; i < shown; i++){
                StackTraceElement frame = trace[i];
                builder.append("  at ").append(frame.getClassName()).append('.').append(frame.getMethodName());
                if (frame.getFileName() != null){
                    builder.append(" (").append(frame.getFileName());
                    if (frame.getLineNumber() > 0) builder.append(':').append(frame.getLineNumber());
                    builder.append(')');
                }
                builder.append('\n');
            }
            if (trace.length > shown){
                builder.append("  ... ").append(trace.length - shown).append(" more frame(s), see attached trace below\n");
            }
        }

        return builder.toString();
    }

    public static void printJavaError(Throwable error){
        if (NeetComputersServer.DO_LOGGING) errorLogger.warn(generateJavaErrorLog(error), error);
    }

    private static Function packCachedFunction(PackedFunctionCache functionCache, Exposable obj, Runtime runtime){
        return new Function(false, functionCache.name, Parameters.any()) {
            @Override
            public Value call(Value<?>[] parameters) {
                LinkedList<String> errors = new LinkedList<>();
                LinkedList<String> names = new LinkedList<>();
                for (StaticFunctionCache staticFunction : functionCache.functions){
                    Optional<String> retur = staticFunction.ruleset().canCast(parameters, runtime==null ? null : runtime.getThread().getLang());
                    if (!retur.isEmpty()){
                        errors.add(retur.get());
                        names.add(staticFunction.functionName() + staticFunction.ruleset());
                    }else{
                        Function function = sandboxFunction(staticFunction.method(), obj, staticFunction.ruleset(), runtime);
                        return function.invoke(parameters);
                    }
                }
                names.sort(String::compareTo);
                StringBuilder error = new StringBuilder(errors.get(new Random().nextInt(errors.size())));
                for (String string : names){
                    error.append('\n');
                    error.append(string);
                }
                return new Exception(error.toString()).asValue();
            }
        };
    }

    public static String[] getFunctions(Exposable obj){
        Class<?> _class = obj.getClass();
        LinkedList<String> names = new LinkedList<>();
        Method[] buffer = _class.getMethods();
        for (Method method : buffer) {
            if (method.isAnnotationPresent(Exposed.class)) {
                Exposed annotation = method.getAnnotation(Exposed.class);
                if (annotation.nameOverride().isBlank()){
                    if (!names.contains(method.getName())) names.add(method.getName());
                }else{
                    if (!names.contains(annotation.nameOverride())) names.add(annotation.nameOverride());
                }
            }
        }
        names.sort(String::compareTo);
        return names.toArray(new String[0]);
    }

    public static String getName(Method method){
        if (method.isAnnotationPresent(Exposed.class)) {
            Exposed annotation = method.getAnnotation(Exposed.class);
            if (annotation.nameOverride().isBlank()){
                return method.getName();
            }else{
                return annotation.nameOverride();
            }
        }
        return "helpmeimtrapedinanightmareofmyowncreation";
    }

    public static Value<?> searchAndCall(Exposable obj, Runtime runtime, String name, Value<?>... args) throws LoaderError {
        Class<?> _class = obj.getClass();
        if (cache.containsKey(_class)) {
            LoaderCache cachedObject = cache.get(_class);
            for (StaticFunctionCache functionCache : cachedObject.functions()){
                if (functionCache.functionName().equals(name)){
                    Optional<String> retur = functionCache.ruleset().canCast(args, runtime==null ? null : runtime.getThread().getLang());
                    if (retur.isEmpty()){
                        return sandboxFunction(functionCache.method(), obj, functionCache.ruleset(), runtime).invoke(args);
                    }else{
                        return Value.asError(retur.get());
                    }
                }
            }
            LinkedList<String> errors = new LinkedList<>();
            LinkedList<String> names = new LinkedList<>();
            for (PackedFunctionCache pFunctionCache : cachedObject.packedFunctions()){
                if (pFunctionCache.name.equals(name)){
                    for (StaticFunctionCache functionCache : pFunctionCache.functions){
                        Optional<String> retur = functionCache.ruleset().canCast(args, runtime==null ? null : runtime.getThread().getLang());
                        if (retur.isEmpty()){
                            return sandboxFunction(functionCache.method(), obj, functionCache.ruleset(), runtime).invoke(args);
                        }else{
                            errors.add(retur.get());
                            names.add(name + functionCache.ruleset());
                        }
                    }
                }
            }
            if (!errors.isEmpty()){
                names.sort(String::compareTo);
                StringBuilder error = new StringBuilder(errors.get(new Random().nextInt(errors.size())));
                for (String string : names){
                    error.append('\n');
                    error.append(string);
                }
                return new Exception(error.toString()).asValue();
            }else{
                return Value.asError("Cant Find Function '"+name+"'");
            }
        }else{
            Method[] buffer = _class.getMethods();
            LinkedList<String> errors = new LinkedList<>();
            LinkedList<String> names = new LinkedList<>();
            for (Method method : buffer){
                if (getName(method).equals(name)){
                    Parameters rules = Parameters.deduceParameters(method.getParameters());
                    Optional<String> retur = rules.canCast(args, runtime==null ? null : runtime.getThread().getLang());
                    if (retur.isEmpty()){
                        return sandboxFunction(method, obj, rules, runtime).invoke(args);
                    }else{
                        errors.add(retur.get());
                        names.add(name + rules);
                    }
                }
            }
            if (errors.isEmpty()){
                return Value.asError("Cant Find Function '"+name+"'");
            }else if (errors.size()==1){
                return Value.asError(errors.getFirst());
            }else{
                names.sort(String::compareTo);
                StringBuilder error = new StringBuilder(errors.get(new Random().nextInt(errors.size())));
                for (String string : names){
                    error.append('\n');
                    error.append(string);
                }
                return new Exception(error.toString()).asValue();
            }
        }
    }

    public static Function[] translateAPI(Exposable obj, @Nullable Runtime runtime) throws LoaderError {
        if (!cache.containsKey(obj.getClass())) {
            Class<?> _class = obj.getClass();
            Hashtable<String, LinkedList<Function>> functions = new Hashtable<>();
            Method[] buffer = _class.getMethods();
            for (Method method : buffer) {
                if (method.isAnnotationPresent(Exposed.class)) {
                    Parameters ruleset = Parameters.deduceParameters(method.getParameters());
                    Function function = sandboxFunction(method, obj, ruleset, runtime);
                    if (functions.containsKey(function.getName())) {
                        functions.get(function.getName()).add(function);
                    } else {
                        functions.put(function.getName(), new LinkedList<>(Set.of(function)));
                    }
                }
            }
            return packFunctions(functions, runtime);
        }else{
            LoaderCache cachedLoader = cache.get(obj.getClass());
            LinkedList<Function> functions = new LinkedList<>();
            for (StaticFunctionCache staticFunctionCache : cachedLoader.functions()){
                functions.add(sandboxFunction(staticFunctionCache.method(), obj, staticFunctionCache.ruleset(), runtime));
            }
            for (PackedFunctionCache packedFunctionCache : cachedLoader.packedFunctions()){
                functions.add(packCachedFunction(packedFunctionCache, obj, runtime));
            }
            functions.sort(Comparator.comparing(Function::getName));
            return functions.toArray(new Function[]{});
        }
    }
}
