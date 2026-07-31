package shit.zen.asm;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.Type;

/**
 * Accumulator for the (receiver, args) tuple captured at a wrapped invoke site, paired with a
 * cached {@link MethodHandle} so the wrapper can re-invoke the original implementation on
 * demand. The instances are short-lived — one per wrapped call.
 */
public final class MethodWrapper {
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final Map<MethodKey, MethodHandle> CACHE = new ConcurrentHashMap<>();

    private final MethodHandle handle;
    private final List<Object> params = new LinkedList<>();

    private MethodWrapper(MethodHandle handle) {
        this.handle = handle;
    }

    public static MethodWrapper getInstance(String classOwner, String methodName, String methodDesc) throws Exception {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = MethodWrapper.class.getClassLoader();
        }
        MethodKey key = new MethodKey(loader, classOwner, methodName, methodDesc);
        MethodHandle cached = CACHE.get(key);
        if (cached != null) {
            return new MethodWrapper(cached);
        }
        Class<?> clazz = Class.forName(classOwner.replace('/', '.'), false, loader);
        MethodHandle handle = lookup(clazz, methodName, methodDesc);
        if (handle == null) {
            throw new NoSuchMethodException("Method " + methodName + methodDesc + " not found on " + classOwner);
        }
        MethodHandle existing = CACHE.putIfAbsent(key, handle);
        return new MethodWrapper(existing != null ? existing : handle);
    }

    private static MethodHandle lookup(Class<?> clazz, String methodName, String methodDesc) throws Exception {
        Method method = findMethod(clazz, methodName, methodDesc, new HashSet<>());
        if (method == null) {
            return null;
        }
        if (!method.trySetAccessible()) {
            return MethodHandles.publicLookup().unreflect(method);
        }
        return LOOKUP.unreflect(method);
    }

    private static Method findMethod(Class<?> clazz, String methodName, String methodDesc,
                                     Set<Class<?>> visited) {
        if (clazz == null || !visited.add(clazz)) {
            return null;
        }
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName) && Type.getMethodDescriptor(method).equals(methodDesc)) {
                return method;
            }
        }

        Method inherited = findMethod(clazz.getSuperclass(), methodName, methodDesc, visited);
        if (inherited != null) {
            return inherited;
        }
        for (Class<?> interfaceClass : clazz.getInterfaces()) {
            inherited = findMethod(interfaceClass, methodName, methodDesc, visited);
            if (inherited != null) {
                return inherited;
            }
        }
        return null;
    }

    private record MethodKey(ClassLoader loader, String owner, String name, String descriptor) {
    }

    public List<Object> getMethodParams() {
        return params;
    }

    /**
     * Bytecode entry point: pushes one argument onto the wrapper. Args are accumulated in reverse
     * (the call site pushes them last-arg first), so we always insert at index 0 — the final
     * list ends up in original calling order.
     */
    public MethodWrapper addParam(Object param) {
        params.add(0, param);
        return this;
    }

    public Object call(Object instance) throws Throwable {
        if (instance == null) {
            if (params.isEmpty()) {
                return handle.invoke();
            }
            return handle.invokeWithArguments(params);
        }
        if (params.isEmpty()) {
            return handle.invoke(instance);
        }
        params.add(0, instance);
        return handle.invokeWithArguments(params);
    }
}
