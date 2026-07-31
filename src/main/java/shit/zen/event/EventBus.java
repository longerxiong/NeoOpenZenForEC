package shit.zen.event;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class EventBus {
    private static final Logger LOGGER = LogManager.getLogger(EventBus.class);
    private static final long FAILURE_REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30L);
    private final Map<Class<? extends EventMarker>, List<ListenerEntry>> listeners = new HashMap<>();

    public static final class ListenerEntry {
        private final Object listener;
        private final Method method;
        private final byte priority;
        private volatile FailureState failureState;

        private ListenerEntry(Object listener, Method method, byte priority) {
            this.listener = listener;
            this.method = method;
            this.priority = priority;
        }

        public Object listener() {
            return listener;
        }

        public Method method() {
            return method;
        }

        public byte priority() {
            return priority;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof ListenerEntry other)) return false;
            return listener.equals(other.listener) && method.equals(other.method) && priority == other.priority;
        }

        @Override
        public int hashCode() {
            int result = listener.hashCode();
            result = 31 * result + method.hashCode();
            return 31 * result + priority;
        }
    }

    public void register(Object object) {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!isValidListener(method)) continue;
            addListener(method, object);
        }
    }

    public void registerForClass(Object object, Class<? extends EventMarker> clazz) {
        for (Method method : object.getClass().getDeclaredMethods()) {
            if (!isListenerForClass(method, clazz)) continue;
            addListener(method, object);
        }
    }

    public void unregister(Object object) {
        for (List<ListenerEntry> list : listeners.values()) {
            list.removeIf(e -> e.listener().equals(object));
        }
    }

    public void unregisterForClass(Object object, Class<? extends EventMarker> clazz) {
        if (listeners.containsKey(clazz)) {
            listeners.get(clazz).removeIf(e -> e.listener().equals(object));
        }
    }

    private void addListener(Method method, Object object) {
        @SuppressWarnings("unchecked")
        Class<? extends EventMarker> clazz = (Class<? extends EventMarker>) method.getParameterTypes()[0];
        ListenerEntry entry = new ListenerEntry(object, method, method.getAnnotation(EventTarget.class).value());
        if (!entry.method().isAccessible()) {
            entry.method().setAccessible(true);
        }
        List<ListenerEntry> list = listeners.computeIfAbsent(clazz, k -> new CopyOnWriteArrayList<>());
        if (!list.contains(entry)) {
            list.add(entry);
            sortByPriority(clazz);
        }
    }

    public void callEventForClass(Class<? extends EventMarker> clazz) {
        Iterator<Map.Entry<Class<? extends EventMarker>, List<ListenerEntry>>> iterator = listeners.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getKey().equals(clazz)) {
                iterator.remove();
                break;
            }
        }
    }

    private void sortByPriority(Class<? extends EventMarker> clazz) {
        List<ListenerEntry> existing = listeners.get(clazz);
        CopyOnWriteArrayList<ListenerEntry> sorted = new CopyOnWriteArrayList<>();
        for (byte priority : EventPriority.PRIORITIES) {
            for (ListenerEntry entry : existing) {
                if (entry.priority() == priority) {
                    sorted.add(entry);
                }
            }
        }
        listeners.put(clazz, sorted);
    }

    private boolean isValidListener(Method method) {
        return method.getParameterTypes().length == 1 && method.isAnnotationPresent(EventTarget.class);
    }

    private boolean isListenerForClass(Method method, Class<? extends EventMarker> clazz) {
        return isValidListener(method) && method.getParameterTypes()[0].equals(clazz);
    }

    public EventMarker call(EventMarker eventMarker) {
        List<ListenerEntry> list = listeners.get(eventMarker.getClass());
        if (list == null) return eventMarker;
        if (eventMarker instanceof AbstractCancellable abstractCancellable) {
            for (ListenerEntry entry : list) {
                dispatchToListener(entry, eventMarker);
                if (abstractCancellable.isCancelled()) break;
            }
        } else {
            for (ListenerEntry entry : list) {
                dispatchToListener(entry, eventMarker);
            }
        }
        return eventMarker;
    }

    private void dispatchToListener(ListenerEntry entry, EventMarker eventMarker) {
        FailureState failureState = entry.failureState;
        if (failureState != null && !failureState.tryBeginRetry(System.nanoTime())) {
            return;
        }
        try {
            entry.method().invoke(entry.listener(), eventMarker);
            if (failureState != null) {
                entry.failureState = null;
            }
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            reportListenerFailure(entry, eventMarker, cause, failureState != null);
        } catch (Exception e) {
            reportListenerFailure(entry, eventMarker, e, failureState != null);
        }
    }

    private void reportListenerFailure(ListenerEntry entry, EventMarker eventMarker, Throwable cause,
                                       boolean retryFailure) {
        long now = System.nanoTime();
        if (!retryFailure) {
            synchronized (entry) {
                if (entry.failureState == null) {
                    entry.failureState = new FailureState(now + FAILURE_REPORT_INTERVAL_NANOS);
                } else {
                    entry.failureState.finishFailedRetry(now + FAILURE_REPORT_INTERVAL_NANOS);
                    retryFailure = true;
                }
            }
        } else {
            entry.failureState.finishFailedRetry(now + FAILURE_REPORT_INTERVAL_NANOS);
        }

        if (!retryFailure) {
            LOGGER.error("Listener {}#{} failed while handling {}",
                    entry.listener().getClass().getName(), entry.method().getName(),
                    eventMarker.getClass().getName(), cause);
            return;
        }
        LOGGER.warn("Listener {}#{} still fails while handling {}; muted for another 30 seconds. "
                        + "Latest cause: {}: {}",
                entry.listener().getClass().getName(), entry.method().getName(),
                eventMarker.getClass().getName(), cause.getClass().getName(), cause.getMessage());
    }

    private static final class FailureState {
        private long retryAfterNanos;
        private boolean retryInProgress;

        private FailureState(long retryAfterNanos) {
            this.retryAfterNanos = retryAfterNanos;
        }

        private synchronized boolean tryBeginRetry(long now) {
            if (retryInProgress || now < retryAfterNanos) {
                return false;
            }
            retryInProgress = true;
            return true;
        }

        private synchronized void finishFailedRetry(long nextRetryNanos) {
            retryAfterNanos = nextRetryNanos;
            retryInProgress = false;
        }
    }
}
