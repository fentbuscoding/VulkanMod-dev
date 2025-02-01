package net.vulkanmod.vulkan;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.queue.CommandPool;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;

import java.nio.LongBuffer;

import static org.lwjgl.vulkan.VK13.*;

public class Synchronization {
    private static final int ALLOCATION_SIZE = 50;

    public static final Synchronization INSTANCE = new Synchronization(ALLOCATION_SIZE);

    private final LongBuffer fences;
    private int idx = 0;

    private ObjectArrayList<CommandPool.CommandBuffer> commandBuffers = new ObjectArrayList<>();

    Synchronization(int allocSize) {
        this.fences = MemoryUtil.memAllocLong(allocSize);
    }

    public synchronized void addCommandBuffer(CommandPool.CommandBuffer commandBuffer) {
        this.addFence(commandBuffer.getFence());
        this.commandBuffers.add(commandBuffer);
    }

    public synchronized void addFence(long fence) {
        if (idx == ALLOCATION_SIZE)
            waitFences();

        fences.put(idx, fence);
        idx++;
    }

    public synchronized void waitFences() {
        if (idx == 0)
            return;

        VkDevice device = Vulkan.getVkDevice();
        fences.limit(idx);

        int result = vkWaitForFences(device, fences, true, 0);
        if (result == VK_TIMEOUT) {
            vkWaitForFences(device, fences, true, VUtil.UINT64_MAX);
        }

        this.commandBuffers.forEach(CommandPool.CommandBuffer::reset);
        this.commandBuffers.clear();

        fences.limit(ALLOCATION_SIZE);
        idx = 0;
    }

    public static void waitFence(long fence) {
        VkDevice device = Vulkan.getVkDevice();

        vkWaitForFences(device, fence, true, VUtil.UINT64_MAX);
    }

    public static boolean checkFenceStatus(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        return vkGetFenceStatus(device, fence) == VK_SUCCESS;
    }

    public void cleanup() {
        MemoryUtil.memFree(fences);
    }

    public static long createFence(VkDevice device, boolean signaled) {
        VkFenceCreateInfo fenceCreateInfo = VkFenceCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO)
                .flags(signaled ? VK_FENCE_CREATE_SIGNALED_BIT : 0);

        LongBuffer pFence = MemoryUtil.memAllocLong(1);
        int err = vkCreateFence(device, fenceCreateInfo, null, pFence);
        long fence = pFence.get(0);
        MemoryUtil.memFree(pFence);
        fenceCreateInfo.free();

        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create fence: " + VUtil.translateVulkanResult(err));
        }

        return fence;
    }

    public static long createSemaphore(VkDevice device) {
        VkSemaphoreCreateInfo semaphoreCreateInfo = VkSemaphoreCreateInfo.calloc()
                .sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

        LongBuffer pSemaphore = MemoryUtil.memAllocLong(1);
        int err = vkCreateSemaphore(device, semaphoreCreateInfo, null, pSemaphore);
        long semaphore = pSemaphore.get(0);
        MemoryUtil.memFree(pSemaphore);
        semaphoreCreateInfo.free();

        if (err != VK_SUCCESS) {
            throw new RuntimeException("Failed to create semaphore: " + VUtil.translateVulkanResult(err));
        }

        return semaphore;
    }

    public static void resetFence(long fence) {
        VkDevice device = Vulkan.getVkDevice();
        vkResetFences(device, fence);
    }
}
