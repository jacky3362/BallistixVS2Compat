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

        Method byChunk = findMethod(loadedShips.getClass(), "getByChunkPos", int.class, int.class);
        Object ship = invokeInstance(loadedShips, byChunk, chunkX, chunkZ);
        if (ship != null) {
            return ship;
        }

        Object allShips = invokeInstance(shipWorld, findMethod(shipWorld.getClass(), "getAllShips"));
        if (allShips == null) {
            return null;
        }

        return invokeInstance(allShips, byChunk, chunkX, chunkZ);
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

        Method direct = findMethod(ship.getClass(), "positionToWorld", Vec3.class);
        Vec3 directResult = toVec3(invokeInstance(ship, direct, position));
        if (directResult != null) {
            return directResult;
        }

        Object matrix = invokeInstance(ship, findMethod(ship.getClass(), "getShipToWorld"));
        Vec3 matrixResult = transformWithMatrix(matrix, position);
        if (matrixResult != null) {
            return matrixResult;
        }

        Object transform = invokeInstance(ship, findMethod(ship.getClass(), "getTransform"));
        if (transform != null) {
            Object transformMatrix = invokeInstance(transform, findMethod(transform.getClass(), "getShipToWorld"));
            matrixResult = transformWithMatrix(transformMatrix, position);
            if (matrixResult != null) {
                return matrixResult;
            }
        }

        return position;
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
        if (matrix == null || position == null) {
            return null;
        }

        if (JOML_VECTOR3D != null) {
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
                    Vec3 fromResult = toVec3(result);
                    if (fromResult != null) {
                        return fromResult;
                    }
                    Vec3 fromOutput = toVec3(output);
                    if (fromOutput != null) {
                        return fromOutput;
                    }
                }

                Method transformPositionVec = findMethod(matrix.getClass(), "transformPosition", JOML_VECTOR3D);
                if (transformPositionVec != null) {
                    Object result = invokeInstance(matrix, transformPositionVec, output);
                    Vec3 fromResult = toVec3(result);
                    if (fromResult != null) {
                        return fromResult;
                    }
                    return toVec3(output);
                }
            } catch (Throwable ignored) {

            }
        }

        return null;
    }

    private static Class<?> loadClass(String className) {
        ClassLoader contextLoader = Thread.currentThread().getContextClassLoader();
        if (contextLoader != null) {
            try {
                return Class.forName(className, false, contextLoader);
            } catch (Throwable ignored) {

            }
        }

        try {
            return Class.forName(className, false, VS2ReflectionBridge.class.getClassLoader());
        } catch (Throwable ex) {
            return null;
        }
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
            return null;
        }
        return null;
    }
}
