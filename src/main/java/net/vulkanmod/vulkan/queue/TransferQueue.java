package net.vulkanmod.vulkan.queue;

import net.vulkanmod.vulkan.Synchronization;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.util.VUtil;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkBufferCopy;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDevice;

import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK10.vkCmdCopyBuffer;
import static org.lwjgl.vulkan.VK10.vkWaitForFences;

public class TransferQueue extends Queue {
    private static final VkDevice DEVICE = Vulkan.getVkDevice();
    private static final VkBufferCopy.Buffer COPY_REGION = VkBufferCopy.calloc(1);

    public TransferQueue(MemoryStack stack, int familyIndex) {
        super(stack, familyIndex);
    }

    public long copyBufferCmd(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            CommandPool.CommandBuffer commandBuffer = beginCommands();

            COPY_REGION.size(size);
            COPY_REGION.srcOffset(srcOffset);
            COPY_REGION.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, COPY_REGION);

            this.submitCommands(commandBuffer);
            Synchronization.INSTANCE.addCommandBuffer(commandBuffer);
            commandBuffer.setSubmitted(true); // Add this line

            return commandBuffer.fence;
        }
    }

    public void uploadBufferImmediate(long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {
            CommandPool.CommandBuffer commandBuffer = this.beginCommands();

            COPY_REGION.size(size);
            COPY_REGION.srcOffset(srcOffset);
            COPY_REGION.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer.getHandle(), srcBuffer, dstBuffer, COPY_REGION);

            this.submitCommands(commandBuffer);
            vkWaitForFences(DEVICE, commandBuffer.fence, true, VUtil.UINT64_MAX);
            commandBuffer.reset();
            commandBuffer.setSubmitted(true); // Add this line
        }
    }

    public static void uploadBufferCmd(VkCommandBuffer commandBuffer, long srcBuffer, long srcOffset, long dstBuffer, long dstOffset, long size) {

        try (MemoryStack stack = stackPush()) {

            COPY_REGION.size(size);
            COPY_REGION.srcOffset(srcOffset);
            COPY_REGION.dstOffset(dstOffset);

            vkCmdCopyBuffer(commandBuffer, srcBuffer, dstBuffer, COPY_REGION);
        }
    }

}
