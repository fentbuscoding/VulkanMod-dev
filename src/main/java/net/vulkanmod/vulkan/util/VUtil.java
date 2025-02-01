package net.vulkanmod.vulkan.util;

import net.vulkanmod.vulkan.memory.Buffer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.Collection;

import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.vulkan.VK13.*;

public class VUtil {
    public static final boolean CHECKS = true;

    public static final int UINT32_MAX = 0xFFFFFFFF;
    public static final long UINT64_MAX = 0xFFFFFFFFFFFFFFFFL;

    public static final Unsafe UNSAFE;

    static {
        Field f = null;
        try {
            f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            UNSAFE = (Unsafe) f.get(null);

        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    public static PointerBuffer asPointerBuffer(Collection<String> collection) {

        MemoryStack stack = stackGet();

        PointerBuffer buffer = stack.mallocPointer(collection.size());

        collection.stream()
                .map(stack::UTF8)
                .forEach(buffer::put);

        return buffer.rewind();
    }

    public static void memcpy(ByteBuffer src, long dstPtr) {
        MemoryUtil.memCopy(MemoryUtil.memAddress0(src), dstPtr, src.capacity());
    }

    public static void memcpy(ByteBuffer src, Buffer dst, long size) {
        if (CHECKS) {
            if (size > dst.getBufferSize() - dst.getUsedBytes()) {
                throw new IllegalArgumentException("Upload size is greater than available dst buffer size");
            }
        }

        final long srcPtr = MemoryUtil.memAddress(src);
        final long dstPtr = dst.getDataPtr() + dst.getUsedBytes();

        MemoryUtil.memCopy(srcPtr, dstPtr, size);
    }

    public static void memcpy(Buffer src, ByteBuffer dst, long size) {
        if (CHECKS) {
            if (size > dst.remaining()) {
                throw new IllegalArgumentException("Upload size is greater than available dst buffer size");
            }
        }

        final long srcPtr = src.getDataPtr();
        final long dstPtr = MemoryUtil.memAddress(dst);

        MemoryUtil.memCopy(srcPtr, dstPtr, size);
    }

    public static int align(int x, int align) {
        int r = x % align;
        return r == 0 ? x : x + align - r;
    }

    public static String translateVulkanResult(int result) {
        switch (result) {
            case VK_SUCCESS:
                return "Command successfully completed";
            case VK_NOT_READY:
                return "A fence or query has not yet completed";
            case VK_TIMEOUT:
                return "A wait operation has not completed in the specified time";
            case VK_EVENT_SET:
                return "An event is signaled";
            case VK_EVENT_RESET:
                return "An event is unsignaled";
            case VK_INCOMPLETE:
                return "A return array was too small for the result";
            case VK_ERROR_OUT_OF_HOST_MEMORY:
                return "A host memory allocation has failed";
            case VK_ERROR_OUT_OF_DEVICE_MEMORY:
                return "A device memory allocation has failed";
            case VK_ERROR_INITIALIZATION_FAILED:
                return "Initialization of an object could not be completed for implementation-specific reasons";
            case VK_ERROR_DEVICE_LOST:
                return "The logical or physical device has been lost";
            case VK_ERROR_MEMORY_MAP_FAILED:
                return "Mapping of a memory object has failed";
            case VK_ERROR_LAYER_NOT_PRESENT:
                return "A requested layer is not present or could not be loaded";
            case VK_ERROR_EXTENSION_NOT_PRESENT:
                return "A requested extension is not supported";
            case VK_ERROR_FEATURE_NOT_PRESENT:
                return "A requested feature is not supported";
            case VK_ERROR_INCOMPATIBLE_DRIVER:
                return "The requested version of Vulkan is not supported by the driver or is otherwise incompatible";
            case VK_ERROR_TOO_MANY_OBJECTS:
                return "Too many objects of the type have already been created";
            case VK_ERROR_FORMAT_NOT_SUPPORTED:
                return "A requested format is not supported on this device";
            case VK_ERROR_FRAGMENTED_POOL:
                return "A pool allocation has failed due to fragmentation of the pool’s memory";
            default:
                return "Unknown error";
        }
    }
}
