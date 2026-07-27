package shit.zen.utils.render;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import shit.zen.ClientBase;

/**
 * Reflective Iris isolation for world overlays.
 *
 * <p>Iris remaps vanilla {@code RenderPipeline}s while
 * {@code shouldOverrideShaders() = isRenderingWorld && isMainBound} and
 * {@code !ImmediateState.bypass}. We force {@code ImmediateState.bypass=true}
 * (and {@code setIsMainBound(false)}) around our draws so Iris cannot hijack
 * LINES / debug geometry, then restore previous flags.
 */
public final class IrisCompatibility extends ClientBase {
    private static boolean initialized;
    private static boolean present;
    private static Object capturedRenderingState;
    private static Method getGbufferModelView;
    private static Method getGbufferProjection;
    private static Method isRenderingShadowPass;
    private static Method isShaderPackInUse;
    private static Object irisApiInstance;
    private static Field immediateBypassField;
    private static Field temporaryIgnorePassField;
    private static Object pipelineManager;
    private static Method getPipelineNullable;
    private static Method setIsMainBound;
    private static boolean reflectionWarningLogged;

    private static int isolationDepth;
    private static boolean savedBypass;
    private static boolean savedIgnorePass;
    private static Object isolatedPipeline;

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        ClassLoader loader = Minecraft.class.getClassLoader();
        try {
            Class.forName("net.irisshaders.iris.Iris", false, loader);

            Class<?> stateClass = Class.forName(
                    "net.irisshaders.iris.uniforms.CapturedRenderingState", false, loader);
            Field instanceField = stateClass.getField("INSTANCE");
            capturedRenderingState = instanceField.get(null);
            getGbufferModelView = stateClass.getMethod("getGbufferModelView");
            try {
                getGbufferProjection = stateClass.getMethod("getGbufferProjection");
            } catch (NoSuchMethodException ignored) {
                getGbufferProjection = null;
            }

            try {
                Class<?> apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi", false, loader);
                Method getInstance = apiClass.getMethod("getInstance");
                irisApiInstance = getInstance.invoke(null);
                isRenderingShadowPass = apiClass.getMethod("isRenderingShadowPass");
                isShaderPackInUse = apiClass.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                irisApiInstance = null;
            }

            try {
                Class<?> immediate = Class.forName(
                        "net.irisshaders.iris.vertices.ImmediateState", false, loader);
                immediateBypassField = immediate.getField("bypass");
                try {
                    temporaryIgnorePassField = immediate.getField("temporarilyIgnorePass");
                } catch (NoSuchFieldException ignored) {
                    temporaryIgnorePassField = null;
                }
            } catch (ReflectiveOperationException | LinkageError ignored) {
                immediateBypassField = null;
            }

            try {
                Class<?> irisClass = Class.forName("net.irisshaders.iris.Iris", false, loader);
                Method getPipelineManager = irisClass.getMethod("getPipelineManager");
                pipelineManager = getPipelineManager.invoke(null);
                getPipelineNullable = pipelineManager.getClass().getMethod("getPipelineNullable");
            } catch (ReflectiveOperationException | LinkageError ignored) {
                pipelineManager = null;
                getPipelineNullable = null;
            }

            try {
                Class<?> pipelineIface = Class.forName(
                        "net.irisshaders.iris.pipeline.WorldRenderingPipeline", false, loader);
                setIsMainBound = pipelineIface.getMethod("setIsMainBound", boolean.class);
            } catch (ReflectiveOperationException | LinkageError ignored) {
                setIsMainBound = null;
            }

            present = true;
            logger.info("Iris detected; overlays isolate with ImmediateState.bypass.");
        } catch (ClassNotFoundException ignored) {
            present = false;
            logger.info("Iris not detected; using the vanilla level render hook.");
        } catch (ReflectiveOperationException | LinkageError exception) {
            present = false;
            logger.warn("Iris was found but its rendering state API could not be resolved.", exception);
        }
    }

    public static boolean isPresent() {
        return present;
    }

    public static boolean isShaderPackInUse() {
        if (!present || isShaderPackInUse == null || irisApiInstance == null) {
            return false;
        }
        try {
            Object result = isShaderPackInUse.invoke(irisApiInstance);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Failed to query Iris shader-pack state.", exception);
            return false;
        }
    }

    public static boolean isRenderingShadowPass() {
        if (!present || isRenderingShadowPass == null || irisApiInstance == null) {
            return false;
        }
        try {
            Object result = isRenderingShadowPass.invoke(irisApiInstance);
            return result instanceof Boolean bool && bool;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Failed to query Iris shadow-pass state.", exception);
            return false;
        }
    }

    public static Matrix4f getGbufferModelView() {
        return copyMatrix(getGbufferModelView);
    }

    public static Matrix4f getGbufferProjection() {
        return copyMatrix(getGbufferProjection);
    }

    private static Matrix4f copyMatrix(Method getter) {
        if (!present || capturedRenderingState == null || getter == null) {
            return null;
        }
        try {
            Object matrix = getter.invoke(capturedRenderingState);
            return matrix instanceof Matrix4fc matrix4fc ? new Matrix4f(matrix4fc) : null;
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Failed to read Iris captured matrix.", exception);
            return null;
        }
    }

    /**
     * Force Iris to leave our geometry alone for the duration of a world overlay pass.
     * Nestable; always pair with {@link #endOverlayIsolation()}.
     */
    public static void beginOverlayIsolation() {
        if (!present) {
            return;
        }
        if (isolationDepth++ > 0) {
            return;
        }
        try {
            if (immediateBypassField != null) {
                savedBypass = immediateBypassField.getBoolean(null);
                immediateBypassField.setBoolean(null, true);
            }
            if (temporaryIgnorePassField != null) {
                savedIgnorePass = temporaryIgnorePassField.getBoolean(null);
                temporaryIgnorePassField.setBoolean(null, true);
            }
            // Keep isMainBound false so shouldOverrideShaders stays off even if
            // isRenderingWorld is still true on some Iris builds / inject points.
            if (pipelineManager != null && getPipelineNullable != null && setIsMainBound != null) {
                isolatedPipeline = getPipelineNullable.invoke(pipelineManager);
                if (isolatedPipeline != null) {
                    setIsMainBound.invoke(isolatedPipeline, false);
                }
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Failed to enter Iris overlay isolation.", exception);
        }
    }

    public static void endOverlayIsolation() {
        if (!present || isolationDepth <= 0) {
            return;
        }
        if (--isolationDepth > 0) {
            return;
        }
        try {
            // Do not force isMainBound back to true after finalize — Iris already
            // ended the world pass. Only restore ImmediateState flags.
            if (temporaryIgnorePassField != null) {
                temporaryIgnorePassField.setBoolean(null, savedIgnorePass);
            }
            if (immediateBypassField != null) {
                immediateBypassField.setBoolean(null, savedBypass);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            warnOnce("Failed to leave Iris overlay isolation.", exception);
        } finally {
            isolatedPipeline = null;
        }
    }

    public static void withOverlayIsolation(Runnable draw) {
        beginOverlayIsolation();
        try {
            draw.run();
        } finally {
            endOverlayIsolation();
        }
    }

    private static void warnOnce(String message, Throwable exception) {
        if (!reflectionWarningLogged) {
            reflectionWarningLogged = true;
            logger.warn(message, exception);
        }
    }

    private IrisCompatibility() {
    }
}
