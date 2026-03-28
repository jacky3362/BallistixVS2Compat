package ballistixvs2compat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class VS2ReflectionBridge {

    private static final String VS_MOD_CLASS = "org.valkyrienskies.mod.common.ValkyrienSkiesMod";

    private static final Class<?> VS_MOD;
    private static final Class<?> JOML_VECTOR3D;

    private static final Method GET_VS_CORE;

    private static final boolean AVAILABLE;

    static {
        VS_MOD = loadClass(VS_MOD_CLASS);
        JOML_VECTOR3D = loadClass("org.joml.Vector3d");

        GET_VS_CORE = findMethod(VS_MOD, "getVsCore");

        AVAILABLE = VS_MOD != null && GET_VS_CORE != null;
    }

    private VS2ReflectionBridge() {

    }

    public static boolean isAvailable() {
        return AVAILABLE;
    }

    public static Object getShipManagingPos(Level level, BlockPos pos) {
        if (!AVAILABLE || level == null || pos == null) {
            return null;
        }

        Object shipWorld = getShipWorld(level);
        if (shipWorld == null) {
            return null;
        }

        Object loadedShips = invokeInstance(shipWorld, findMethod(shipWorld.getClass(), "getLoadedShips"));
        if (loadedShips == null) {
            return null;
        }

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        Object ship = invokeByChunk(loadedShips, chunkX, chunkZ);
        if (ship != null) {
            return ship;
        }

        Object allShips = invokeInstance(shipWorld, findMethod(shipWorld.getClass(), "getAllShips"));
        return invokeByChunk(allShips, chunkX, chunkZ);
    }

    public static Vec3 toWorldCoordinates(Level level, Vec3 position) {
        if (!AVAILABLE || level == null || position == null) {
            return position;
        }

        Object ship = getShipManagingPos(level, BlockPos.containing(position));
        if (ship == null) {
            return position;
        }

        return toWorldCoordinates(ship, position);
    }

    public static Vec3 toWorldCoordinates(Object ship, Vec3 position) {
        if (!AVAILABLE || ship == null || position == null) {
            return position;
        }

        Vec3 directResult = firstVec(
                invokeInstance(ship, findMethod(ship.getClass(), "positionToWorld", Vec3.class), position));
        if (directResult != null) {
            return directResult;
        }

        Vec3 matrixResult = transformWithMatrix(
                invokeInstance(ship, findMethod(ship.getClass(), "getShipToWorld")),
                position);
        if (matrixResult != null) {
            return matrixResult;
        }

        Object transform = invokeInstance(ship, findMethod(ship.getClass(), "getTransform"));
        Vec3 transformed = transformWithMatrix(
                transform == null ? null : invokeInstance(transform, findMethod(transform.getClass(), "getShipToWorld")),
                position);

        return transformed == null ? position : transformed;
    }

    public static Iterable<Vec3> positionToNearbyShips(Level level, Vec3 position) {
        // Kept disabled on server-fork environments to avoid any client-linked VS utility class loading.
        return Collections.emptyList();
    }

    private static Object getShipWorld(Level level) {
        Object core = invokeStatic(GET_VS_CORE);
        if (core == null) {
            return null;
        }

        Object hooks = invokeInstance(core, findMethod(core.getClass(), "getHooks"));
        if (hooks != null) {
            Object shipWorld = level.isClientSide
                    ? invokeInstance(hooks, findMethod(hooks.getClass(), "getCurrentShipClientWorld"))
                    : invokeInstance(hooks, findMethod(hooks.getClass(), "getCurrentShipServerWorld"));

            if (shipWorld != null) {
                return shipWorld;
            }
        }

        return level.isClientSide
                ? invokeInstance(core, findMethod(core.getClass(), "getDummyShipWorldClient"))
                : invokeInstance(core, findMethod(core.getClass(), "getDummyShipWorldServer"));
    }

    private static Vec3 transformWithMatrix(Object matrix, Vec3 position) {
        if (matrix == null || position == null || JOML_VECTOR3D == null) {
            return null;
        }

        try {
            Object output = JOML_VECTOR3D.getConstructor(double.class, double.class, double.class)
                    .newInstance(position.x, position.y, position.z);

            Method transformPositionXYZ = findMethod(
                    matrix.getClass(),
                    "transformPosition",
                    double.class,
                    double.class,
                    double.class,
                    JOML_VECTOR3D);

            if (transformPositionXYZ != null) {
                Object result = invokeInstance(matrix, transformPositionXYZ, position.x, position.y, position.z, output);
                Vec3 fromResult = firstVec(result, output);
                if (fromResult != null) {
                    return fromResult;
                }
            }

            Method transformPositionVec = findMethod(matrix.getClass(), "transformPosition", JOML_VECTOR3D);
            if (transformPositionVec != null) {
                Object result = invokeInstance(matrix, transformPositionVec, output);
                return firstVec(result, output);
            }
        } catch (Throwable ignored) {

        }

        return null;
    }

    private static Class<?> loadClass(String className) {
        return tryLoad(className, Thread.currentThread().getContextClassLoader(), VS2ReflectionBridge.class.getClassLoader());
    }

    private static Method findMethod(Class<?> holder, String name, Class<?>... params) {
        if (holder == null) {
            return null;
        }

        try {
            Method method = holder.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object invokeStatic(Method method, Object... args) {
        if (method == null) {
            return null;
        }

        try {
            if (!Modifier.isStatic(method.getModifiers())) {
                return null;
            }
            return method.invoke(null, args);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Object invokeInstance(Object target, Method method, Object... args) {
        if (target == null || method == null) {
            return null;
        }

        try {
            return method.invoke(target, args);
        } catch (Throwable ex) {
            return null;
        }
    }

    private static Vec3 toVec3(Object value) {
        if (value instanceof Vec3 vec) {
            return vec;
        }

        if (value == null) {
            return null;
        }

        Double x = readCoordinate(value, "x");
        Double y = readCoordinate(value, "y");
        Double z = readCoordinate(value, "z");

        if (x == null || y == null || z == null) {
            x = readCoordinate(value, "getX");
            y = readCoordinate(value, "getY");
            z = readCoordinate(value, "getZ");
        }

        if (x == null || y == null || z == null) {
            return null;
        }

        return new Vec3(x, y, z);
    }

    private static Double readCoordinate(Object value, String name) {
        try {
            Method getter = value.getClass().getMethod(name);
            Object result = getter.invoke(value);
            if (result instanceof Number number) {
                return number.doubleValue();
            }
        } catch (Throwable ex) {
        }
        return null;
    }

    private static Class<?> tryLoad(String className, ClassLoader... loaders) {
        if (loaders == null) {
            return null;
        }
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                return Class.forName(className, false, loader);
            } catch (Throwable ignored) {
            }
        }
        return null;
    }

    private static Object invokeByChunk(Object shipContainer, int chunkX, int chunkZ) {
        if (shipContainer == null) {
            return null;
        }
        Method byChunk = findMethod(shipContainer.getClass(), "getByChunkPos", int.class, int.class);
        return invokeInstance(shipContainer, byChunk, chunkX, chunkZ);
    }

    private static Vec3 firstVec(Object... candidates) {
        if (candidates == null) {
            return null;
        }
        for (Object candidate : candidates) {
            Vec3 vec = toVec3(candidate);
            if (vec != null) {
                return vec;
            }
        }
        return null;
    }
}
