package com.mineclawd.tensurahudnative.client;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

final class ReflectionUtil {
    private static final ConcurrentHashMap<String, Class<?>> CLASSES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Method> METHODS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Field> FIELDS = new ConcurrentHashMap<>();
    private static final Class<?> MISSING_CLASS = Missing.class;

    private ReflectionUtil() {
    }

    static Class<?> classOrNull(String name) {
        Class<?> value = CLASSES.computeIfAbsent(name, key -> {
            try {
                return Class.forName(key, false, ReflectionUtil.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError ignored) {
                return MISSING_CLASS;
            }
        });
        return value == MISSING_CLASS ? null : value;
    }

    static Object staticField(String className, String fieldName) {
        Class<?> type = classOrNull(className);
        if (type == null) {
            return null;
        }
        String key = type.getName() + "#" + fieldName;
        try {
            Field field = FIELDS.computeIfAbsent(key, ignored -> findField(type, fieldName));
            return field == MissingField.INSTANCE ? null : field.get(null);
        } catch (IllegalAccessException ignored) {
            return null;
        }
    }

    static boolean setField(Object target, String fieldName, Object value) {
        if (target == null) {
            return false;
        }
        Class<?> type = target.getClass();
        String key = type.getName() + "#" + fieldName;
        try {
            Field field = FIELDS.computeIfAbsent(key, ignored -> findField(type, fieldName));
            if (field == MissingField.INSTANCE) {
                return false;
            }
            field.set(target, value);
            return true;
        } catch (IllegalAccessException | IllegalArgumentException | LinkageError ignored) {
            return false;
        }
    }

    static Object callStatic(String className, String methodName, Object... args) {
        Class<?> type = classOrNull(className);
        return type == null ? null : invoke(type, null, methodName, args);
    }

    static Object call(Object target, String methodName, Object... args) {
        return target == null ? null : invoke(target.getClass(), target, methodName, args);
    }

    static Object callAny(Object target, String... methodNames) {
        if (target == null) {
            return null;
        }
        for (String name : methodNames) {
            Object value = call(target, name);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Object invoke(Class<?> owner, Object target, String methodName, Object... args) {
        boolean requireStatic = target == null;
        String cacheKey = methodKey(owner, methodName, args, requireStatic);
        Method method = METHODS.get(cacheKey);
        if (method == MissingMethod.INSTANCE) {
            return null;
        }
        if (method == null) {
            method = findCompatibleMethod(owner, methodName, args, requireStatic);
            METHODS.put(cacheKey, method == null ? MissingMethod.INSTANCE : method);
        }
        if (method == null || method == MissingMethod.INSTANCE) {
            return null;
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException | InvocationTargetException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    private static String methodKey(Class<?> owner, String name, Object[] args, boolean requireStatic) {
        StringBuilder key = new StringBuilder(owner.getName()).append('#').append(name).append('/').append(requireStatic);
        for (Object arg : args) {
            key.append(':').append(arg == null ? "null" : arg.getClass().getName());
        }
        return key.toString();
    }

    private static Method findCompatibleMethod(Class<?> owner, String methodName, Object[] args, boolean requireStatic) {
        List<Method> candidates = new ArrayList<>();
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            for (Method method : current.getDeclaredMethods()) {
                candidates.add(method);
            }
        }
        for (Method method : owner.getMethods()) {
            candidates.add(method);
        }
        for (Method method : candidates) {
            if (!method.getName().equals(methodName)
                    || method.getParameterCount() != args.length
                    || (requireStatic && !Modifier.isStatic(method.getModifiers()))) {
                continue;
            }
            Class<?>[] parameters = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameters.length; i++) {
                if (!isAssignable(parameters[i], args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }
            try {
                method.setAccessible(true);
            } catch (RuntimeException ignored) {
            }
            return method;
        }
        return null;
    }

    private static boolean isAssignable(Class<?> parameter, Object arg) {
        if (arg == null) {
            return !parameter.isPrimitive();
        }
        Class<?> actual = arg.getClass();
        if (parameter.isAssignableFrom(actual)) {
            return true;
        }
        if (!parameter.isPrimitive()) {
            return false;
        }
        return (parameter == int.class && actual == Integer.class)
                || (parameter == long.class && actual == Long.class)
                || (parameter == float.class && actual == Float.class)
                || (parameter == double.class && actual == Double.class)
                || (parameter == boolean.class && actual == Boolean.class)
                || (parameter == short.class && actual == Short.class)
                || (parameter == byte.class && actual == Byte.class)
                || (parameter == char.class && actual == Character.class);
    }

    private static Field findField(Class<?> owner, String name) {
        for (Class<?> current = owner; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException | RuntimeException ignored) {
            }
        }
        try {
            return owner.getField(name);
        } catch (NoSuchFieldException ignored) {
            return MissingField.INSTANCE;
        }
    }

    static Object unwrapOptional(Object value) {
        if (value instanceof Optional<?> optional) {
            return optional.orElse(null);
        }
        Object unwrapped = call(value, "orElse", (Object) null);
        return unwrapped == null ? value : unwrapped;
    }

    static String componentString(Object value) {
        if (value == null) {
            return "";
        }
        Object text = call(value, "getString");
        if (text != null && text != value) {
            return String.valueOf(text);
        }
        String raw = String.valueOf(value);
        return raw.contains("@") ? "" : raw;
    }

    static double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    static int integer(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    static boolean bool(Object value, boolean fallback) {
        return value instanceof Boolean bool ? bool : fallback;
    }

    private static final class Missing {
        @SuppressWarnings("unused")
        private static void missing() { }
    }

    private static final class MissingMethod {
        private static final Method INSTANCE;
        static {
            try {
                INSTANCE = Missing.class.getDeclaredMethod("missing");
            } catch (NoSuchMethodException impossible) {
                throw new ExceptionInInitializerError(impossible);
            }
        }
    }

    private static final class MissingField {
        private static final Field INSTANCE;
        static {
            try {
                INSTANCE = Boolean.class.getField("TYPE");
            } catch (NoSuchFieldException impossible) {
                throw new ExceptionInInitializerError(impossible);
            }
        }
    }
}
