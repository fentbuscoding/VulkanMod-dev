package net.vulkanmod.vulkan.queue;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.nio.LongBuffer;
import java.util.ArrayDeque;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.VK_COMMAND_BUFFER_LEVEL_PRIMARY;
import static org.lwjgl.vulkan.VK13.VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT;
import static org.lwjgl.vulkan.VK13.VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
import static org.lwjgl.vulkan.VK13.VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT;
import static org.lwjgl.vulkan.VK13.VK_FENCE_CREATE_SIGNALED_BIT;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_FENCE_CREATE_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO;
import static org.lwjgl.vulkan.VK13.VK_STRUCTURE_TYPE_SUBMIT_INFO;
import static org.lwjgl.vulkan.VK13.VK_SUCCESS;
import static org.lwjgl.vulkan.VK13.vkAllocateCommandBuffers;
import static org.lwjgl.vulkan.VK13.vkBeginCommandBuffer;
import static org.lwjgl.vulkan.VK13.vkCreateCommandPool;
import static org.lwjgl.vulkan.VK13.vkCreateFence;
import static org.lwjgl.vulkan.VK13.vkCreateSemaphore;
import static org.lwjgl.vulkan.VK13.vkDestroyCommandPool;
import static org.lwjgl.vulkan.VK13.vkDestroyFence;
import static org.lwjgl.vulkan.VK13.vkDestroySemaphore;
import static org.lwjgl.vulkan.VK13.vkEndCommandBuffer;
import static org.lwjgl.vulkan.VK13.vkQueueSubmit;
import static org.lwjgl.vulkan.VK13.vkResetCommandPool;
import static org.lwjgl.vulkan.VK13.vkResetFences;

public class CommandPool {
    long id;

    private final List<CommandBuffer> commandBuffers = new ObjectArrayList<>();
    private final java.util.Queue<CommandBuffer> availableCmdBuffers = new ArrayDeque<>();

    private static final VkCommandBufferAllocateInfo ALLOC_INFO = VkCommandBufferAllocateInfo.calloc().sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO);
    private static final VkFenceCreateInfo FENCE_INFO = VkFenceCreateInfo.calloc().sType(VK_STRUCTURE_TYPE_FENCE_CREATE_INFO).flags(VK_FENCE_CREATE_SIGNALED_BIT);
    private static final VkSemaphoreCreateInfo SEMAPHORE_CREATE_INFO = VkSemaphoreCreateInfo.calloc().sType(VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO);

    CommandPool(int queueFamilyIndex) {
        this.createCommandPool(queueFamilyIndex);
    }

    public void createCommandPool(int queueFamily) {
        try (MemoryStack stack = stackPush()) {

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack);
            poolInfo.sType(VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO);
            poolInfo.queueFamilyIndex(queueFamily);
            poolInfo.flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);

            LongBuffer pCommandPool = stack.mallocLong(1);

            if (vkCreateCommandPool(Vulkan.getVkDevice(), poolInfo, null, pCommandPool) != VK_SUCCESS) {
                throw new RuntimeException("Failed to create command pool");
            }

            this.id = pCommandPool.get(0);
        }
    }

    public CommandBuffer getCommandBuffer(MemoryStack stack) {
        if (availableCmdBuffers.isEmpty()) {
            allocateCommandBuffers(stack);
        }

        CommandBuffer commandBuffer = availableCmdBuffers.poll();
        return commandBuffer;
    }

    private void allocateCommandBuffers(MemoryStack stack) {
        final int size = 10;

        ALLOC_INFO.commandPool(id).level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(size);
        PointerBuffer pCommandBuffer = stack.mallocPointer(size);
        vkAllocateCommandBuffers(Vulkan.getVkDevice(), ALLOC_INFO, pCommandBuffer);

        for (int i = 0; i < size; ++i) {
            LongBuffer pFence = stack.mallocLong(1);
            vkCreateFence(Vulkan.getVkDevice(), FENCE_INFO, null, pFence);

            LongBuffer pSemaphore = stack.mallocLong(1);
            vkCreateSemaphore(Vulkan.getVkDevice(), SEMAPHORE_CREATE_INFO, null, pSemaphore);

            VkCommandBuffer vkCommandBuffer = new VkCommandBuffer(pCommandBuffer.get(i), Vulkan.getVkDevice());
            CommandBuffer commandBuffer = new CommandBuffer(this, vkCommandBuffer, pFence.get(0), pSemaphore.get(0));
            commandBuffers.add(commandBuffer);
            availableCmdBuffers.add(commandBuffer);
        }
    }

    public void addToAvailable(CommandBuffer commandBuffer) {
        this.availableCmdBuffers.add(commandBuffer);
    }

    public void cleanUp() {
        for (CommandBuffer commandBuffer : commandBuffers) {
            vkDestroyFence(Vulkan.getVkDevice(), commandBuffer.fence, null);
            vkDestroySemaphore(Vulkan.getVkDevice(), commandBuffer.semaphore, null);
        }
        vkResetCommandPool(Vulkan.getVkDevice(), id, VK_COMMAND_POOL_RESET_RELEASE_RESOURCES_BIT);
        vkDestroyCommandPool(Vulkan.getVkDevice(), id, null);
    }

    public long getId() {
        return id;
    }

    public static class CommandBuffer {
        public final CommandPool commandPool;
        public final VkCommandBuffer handle;
        public final long fence;
        public final long semaphore;
    
        boolean submitted;
        boolean recording;
    
        public CommandBuffer(CommandPool commandPool, VkCommandBuffer handle, long fence, long semaphore) {
            this.commandPool = commandPool;
            this.handle = handle;
            this.fence = fence;
            this.semaphore = semaphore;
        }
    
        public VkCommandBuffer getHandle() {
            return handle;
        }
    
        public long getFence() {
            return fence;
        }
    
        public boolean isSubmitted() {
            return submitted;
        }
    
        public boolean isRecording() {
            return recording;
        }
    
        public void setSubmitted(boolean submitted) {
            this.submitted = submitted;
        }
    
        public void begin(MemoryStack stack) {
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack);
            beginInfo.sType(VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO);
            beginInfo.flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
    
            vkBeginCommandBuffer(this.handle, beginInfo);
    
            this.recording = true;
        }
    
        public long submitCommands(MemoryStack stack, VkQueue queue, boolean useSemaphore) {
            long fence = this.fence;
    
            vkEndCommandBuffer(this.handle);
    
            vkResetFences(Vulkan.getVkDevice(), this.fence);
    
            VkSubmitInfo submitInfo = VkSubmitInfo.calloc(stack);
            submitInfo.sType(VK_STRUCTURE_TYPE_SUBMIT_INFO);
            submitInfo.pCommandBuffers(stack.pointers(this.handle));
    
            if (useSemaphore) {
                submitInfo.pSignalSemaphores(stack.longs(this.semaphore));
            }
    
            vkQueueSubmit(queue, submitInfo, fence);
    
            this.recording = false;
            this.submitted = true;
            return fence;
        }
    
            public void reset() {
                this.submitted = false;
                this.recording = false;
                this.commandPool.addToAvailable(this);
            }
        } // Closing bracket for CommandBuffer class
    } // Closing bracket for CommandPool class
    