import dalvik.system.DexClassLoader;
import java.io.File;

/** Run with app_process on Android to load the bundle against Manager's actual dependencies. */
public final class AndroidBundleSmoke {
    public static void main(String[] args) throws Exception {
        File optimized = new File(args[2]);
        optimized.mkdirs();
        ClassLoader manager = new DexClassLoader(args[0], optimized.getPath(), null, ClassLoader.getSystemClassLoader());
        ClassLoader patches = new DexClassLoader(args[1], optimized.getPath(), null, manager);
        Class<?> entry = patches.loadClass("net.permissionbrick.ha.HomeAssistantPatchKt");
        Object patch = entry.getMethod("getHomeAssistantPatch").invoke(null);
        Object name = patch.getClass().getMethod("getName").invoke(patch);
        if (!"Add Home Assistant to device picker".equals(name)) throw new AssertionError(name);
        System.out.println("PASS: Android DEX bundle loads against ReVanced Manager 2.6.0: " + name);
    }
}
