package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import net.vulkanmod.interfaces.VertexFormatMixed;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.nio.LongBuffer;
import java.util.List;

import static org.lwjgl.system.MemoryStack.stackGet;
import static org.lwjgl.system.MemoryStack.stackPush;
import static org.lwjgl.vulkan.VK13.*;

public class GraphicsPipeline extends Pipeline {
    private final Object2LongMap<PipelineState> graphicsPipelines = new Object2LongOpenHashMap<>();

    private final VertexFormat vertexFormat;
    private final VertexInputDescription vertexInputDescription;

    private long vertShaderModule = 0;
    private long fragShaderModule = 0;

    GraphicsPipeline(Builder builder) {
        super(builder.shaderPath);
        this.buffers = builder.UBOs;
        this.manualUBO = builder.manualUBO;
        this.imageDescriptors = builder.imageDescriptors;
        this.pushConstants = builder.pushConstants;
        this.vertexFormat = builder.vertexFormat;

        this.vertexInputDescription = new VertexInputDescription(this.vertexFormat);

        createDescriptorSetLayout();
        createPipelineLayout();
        createShaderModules(builder.vertShaderSPIRV, builder.fragShaderSPIRV);

        if (builder.renderPass != null)
            graphicsPipelines.computeIfAbsent(PipelineState.DEFAULT,
                    this::createGraphicsPipeline);

        createDescriptorSets(Renderer.getFramesNum());

        PIPELINES.add(this);
    }

    public long getHandle(PipelineState state) {
        return graphicsPipelines.computeIfAbsent(state, this::createGraphicsPipeline);
    }

    private long createGraphicsPipeline(PipelineState state) {
        try (MemoryStack stack = stackPush()) {
            ByteBuffer entryPoint = stack.UTF8("main");

            VkPipelineShaderStageCreateInfo.Buffer shaderStages = VkPipelineShaderStageCreateInfo.calloc(2, stack);

            VkPipelineShaderStageCreateInfo vertShaderStageInfo = shaderStages.get(0);
            vertShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                               .stage(VK_SHADER_STAGE_VERTEX_BIT)
                               .module(vertShaderModule)
                               .pName(entryPoint);

            VkPipelineShaderStageCreateInfo fragShaderStageInfo = shaderStages.get(1);
            fragShaderStageInfo.sType(VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO)
                               .stage(VK_SHADER_STAGE_FRAGMENT_BIT)
                               .module(fragShaderModule)
                               .pName(entryPoint);

            // ===> VERTEX STAGE <===
            VkPipelineVertexInputStateCreateInfo vertexInputInfo = VkPipelineVertexInputStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VERTEX_INPUT_STATE_CREATE_INFO)
                    .pVertexBindingDescriptions(vertexInputDescription.bindingDescriptions)
                    .pVertexAttributeDescriptions(vertexInputDescription.attributeDescriptions);

            // ===> ASSEMBLY STAGE <===
            final int topology = PipelineState.AssemblyRasterState.decodeTopology(state.assemblyRasterState);
            VkPipelineInputAssemblyStateCreateInfo inputAssembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_INPUT_ASSEMBLY_STATE_CREATE_INFO)
                    .topology(topology)
                    .primitiveRestartEnable(false);

            // ===> VIEWPORT & SCISSOR
            VkPipelineViewportStateCreateInfo viewportState = VkPipelineViewportStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_VIEWPORT_STATE_CREATE_INFO)
                    .viewportCount(1)
                    .scissorCount(1);

            // ===> RASTERIZATION STAGE <===
            final int polygonMode = PipelineState.AssemblyRasterState.decodePolygonMode(state.assemblyRasterState);
            final int cullMode = PipelineState.AssemblyRasterState.decodeCullMode(state.assemblyRasterState);
            VkPipelineRasterizationStateCreateInfo rasterizer = VkPipelineRasterizationStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_RASTERIZATION_STATE_CREATE_INFO)
                    .depthClampEnable(false)
                    .rasterizerDiscardEnable(false)
                    .polygonMode(polygonMode)
                    .lineWidth(1.0f)
                    .cullMode(cullMode)
                    .frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE)
                    .depthBiasEnable(true);

            // ===> MULTISAMPLING <===
            VkPipelineMultisampleStateCreateInfo multisampling = VkPipelineMultisampleStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_MULTISAMPLE_STATE_CREATE_INFO)
                    .sampleShadingEnable(false)
                    .rasterizationSamples(VK_SAMPLE_COUNT_1_BIT);

            // ===> DEPTH TEST <===
            VkPipelineDepthStencilStateCreateInfo depthStencil = VkPipelineDepthStencilStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DEPTH_STENCIL_STATE_CREATE_INFO)
                    .depthTestEnable(PipelineState.DepthState.depthTest(state.depthState_i))
                    .depthWriteEnable(PipelineState.DepthState.depthMask(state.depthState_i))
                    .depthCompareOp(PipelineState.DepthState.decodeDepthFun(state.depthState_i))
                    .depthBoundsTestEnable(false)
                    .minDepthBounds(0.0f)
                    .maxDepthBounds(1.0f)
                    .stencilTestEnable(false);

            // ===> COLOR BLENDING <===
            VkPipelineColorBlendAttachmentState.Buffer colorBlendAttachment = VkPipelineColorBlendAttachmentState.calloc(1, stack)
                    .colorWriteMask(state.colorMask_i)
                    .blendEnable(PipelineState.BlendState.enable(state.blendState_i))
                    .srcColorBlendFactor(PipelineState.BlendState.getSrcRgbFactor(state.blendState_i))
                    .dstColorBlendFactor(PipelineState.BlendState.getDstRgbFactor(state.blendState_i))
                    .colorBlendOp(VK_BLEND_OP_ADD)
                    .srcAlphaBlendFactor(PipelineState.BlendState.getSrcAlphaFactor(state.blendState_i))
                    .dstAlphaBlendFactor(PipelineState.BlendState.getDstAlphaFactor(state.blendState_i))
                    .alphaBlendOp(VK_BLEND_OP_ADD);

            VkPipelineColorBlendStateCreateInfo colorBlending = VkPipelineColorBlendStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_COLOR_BLEND_STATE_CREATE_INFO)
                    .logicOpEnable(PipelineState.LogicOpState.enable(state.logicOp_i))
                    .logicOp(PipelineState.LogicOpState.decodeFun(state.logicOp_i))
                    .pAttachments(colorBlendAttachment)
                    .blendConstants(stack.floats(0.0f, 0.0f, 0.0f, 0.0f));

            // ===> DYNAMIC STATES <===
            VkPipelineDynamicStateCreateInfo dynamicStates = VkPipelineDynamicStateCreateInfo.calloc(stack)
                    .sType(VK_STRUCTURE_TYPE_PIPELINE_DYNAMIC_STATE_CREATE_INFO)
                    .pDynamicStates(stack.ints(
                            VK_DYNAMIC_STATE_DEPTH_BIAS, VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                            (topology == VK_PRIMITIVE_TOPOLOGY_LINE_LIST || polygonMode == VK_POLYGON_MODE_LINE) ? VK_DYNAMIC_STATE_LINE_WIDTH : VK_DYNAMIC_STATE_VIEWPORT
                    ));

            VkGraphicsPipelineCreateInfo.Buffer pipelineInfo = VkGraphicsPipelineCreateInfo.calloc(1, stack)
                    .sType(VK_STRUCTURE_TYPE_GRAPHICS_PIPELINE_CREATE_INFO)
                    .pStages(shaderStages)
                    .pVertexInputState(vertexInputInfo)
                    .pInputAssemblyState(inputAssembly)
                    .pViewportState(viewportState)
                    .pRasterizationState(rasterizer)
                    .pMultisampleState(multisampling)
                    .pDepthStencilState(depthStencil)
                    .pColorBlendState(colorBlending)
                    .pDynamicState(dynamicStates)
                    .layout(pipelineLayout)
                    .basePipelineHandle(VK_NULL_HANDLE)
                    .basePipelineIndex(-1);

            if (!Vulkan.DYNAMIC_RENDERING) {
                pipelineInfo.renderPass(state.renderPass.getId());
                pipelineInfo.subpass(0);
            } else {
                VkPipelineRenderingCreateInfoKHR renderingInfo = VkPipelineRenderingCreateInfoKHR.calloc(stack)
                        .sType(KHRDynamicRendering.VK_STRUCTURE_TYPE_PIPELINE_RENDERING_CREATE_INFO_KHR)
                        .pColorAttachmentFormats(stack.ints(state.renderPass.getFramebuffer().getFormat()))
                        .depthAttachmentFormat(state.renderPass.getFramebuffer().getDepthFormat());
                pipelineInfo.pNext(renderingInfo);
            }

            LongBuffer pGraphicsPipeline = stack.mallocLong(1);
            Vulkan.checkResult(vkCreateGraphicsPipelines(DeviceManager.vkDevice, PIPELINE_CACHE, pipelineInfo, null, pGraphicsPipeline),
                               "Failed to create graphics pipeline " + this.name);

            return pGraphicsPipeline.get(0);
        }
    }

    private void createShaderModules(SPIRVUtils.SPIRV vertSpirv, SPIRVUtils.SPIRV fragSpirv) {
        this.vertShaderModule = createShaderModule(vertSpirv.bytecode());
        this.fragShaderModule = createShaderModule(fragSpirv.bytecode());
    }

    public void cleanUp() {
        vkDestroyShaderModule(DeviceManager.vkDevice, vertShaderModule, null);
        vkDestroyShaderModule(DeviceManager.vkDevice, fragShaderModule, null);

        vertexInputDescription.cleanUp();

        destroyDescriptorSets();

        graphicsPipelines.forEach((state, pipeline) -> {
            vkDestroyPipeline(DeviceManager.vkDevice, pipeline, null);
        });
        graphicsPipelines.clear();

        vkDestroyDescriptorSetLayout(DeviceManager.vkDevice, descriptorSetLayout, null);
        vkDestroyPipelineLayout(DeviceManager.vkDevice, pipelineLayout, null);

        PIPELINES.remove(this);
        Renderer.getInstance().removeUsedPipeline(this);
    }

    static class VertexInputDescription {
        final VkVertexInputAttributeDescription.Buffer attributeDescriptions;
        final VkVertexInputBindingDescription.Buffer bindingDescriptions;

        VertexInputDescription(VertexFormat vertexFormat) {
            this.bindingDescriptions = getBindingDescription(vertexFormat);
            this.attributeDescriptions = getAttributeDescriptions(vertexFormat);
        }

        void cleanUp() {
            MemoryUtil.memFree(this.bindingDescriptions);
            MemoryUtil.memFree(this.attributeDescriptions);
        }
    }

    private static VkVertexInputBindingDescription.Buffer getBindingDescription(VertexFormat vertexFormat) {
        VkVertexInputBindingDescription.Buffer bindingDescription = VkVertexInputBindingDescription.calloc(1);

        bindingDescription.binding(0);
        bindingDescription.stride(vertexFormat.getVertexSize());
        bindingDescription.inputRate(VK_VERTEX_INPUT_RATE_VERTEX);

        return bindingDescription;
    }

    private static VkVertexInputAttributeDescription.Buffer getAttributeDescriptions(VertexFormat vertexFormat) {
        List<VertexFormatElement> elements = vertexFormat.getElements();

        int size = elements.size();

        VkVertexInputAttributeDescription.Buffer attributeDescriptions = VkVertexInputAttributeDescription.calloc(size);

        int offset = 0;

        for (int i = 0; i < size; ++i) {
            VkVertexInputAttributeDescription posDescription = attributeDescriptions.get(i);
            posDescription.binding(0);
            posDescription.location(i);

            VertexFormatElement formatElement = elements.get(i);
            VertexFormatElement.Usage usage = formatElement.usage();
            VertexFormatElement.Type type = formatElement.type();
            int elementCount = formatElement.count();

            switch (usage) {
                case POSITION -> {
                    switch (type) {
                        case FLOAT -> {
                            posDescription.format(VK_FORMAT_R32G32B32_SFLOAT);
                            posDescription.offset(offset);

                            offset += 12;
                        }
                        case SHORT -> {
                            posDescription.format(VK_FORMAT_R16G16B16A16_SINT);
                            posDescription.offset(offset);

                            offset += 8;
                        }
                        case BYTE -> {
                            posDescription.format(VK_FORMAT_R8G8B8A8_SINT);
                            posDescription.offset(offset);

                            offset += 4;
                        }
                    }

                }

                case COLOR -> {
                    posDescription.format(VK_FORMAT_R8G8B8A8_UNORM);
                    posDescription.offset(offset);

                    offset += 4;
                }

                case UV -> {
                    switch (type) {
                        case FLOAT -> {
                            posDescription.format(VK_FORMAT_R32G32_SFLOAT);
                            posDescription.offset(offset);

                            offset += 8;
                        }
                        case SHORT -> {
                            posDescription.format(VK_FORMAT_R16G16_SINT);
                            posDescription.offset(offset);

                            offset += 4;
                        }
                        case USHORT -> {
                            posDescription.format(VK_FORMAT_R16G16_UINT);
                            posDescription.offset(offset);

                            offset += 4;
                        }
                    }
                }

                case NORMAL -> {
                    posDescription.format(VK_FORMAT_R8G8B8A8_SNORM);
                    posDescription.offset(offset);

                    offset += 4;
                }

                case GENERIC -> {
                    if (type == VertexFormatElement.Type.SHORT && elementCount == 1) {
                        posDescription.format(VK_FORMAT_R16_SINT);
                        posDescription.offset(offset);

                        offset += 2;
                    }
                    else if (type == VertexFormatElement.Type.INT && elementCount == 1) {
                        posDescription.format(VK_FORMAT_R32_SINT);
                        posDescription.offset(offset);

                        offset += 4;
                    }
                    else {
                        throw new RuntimeException(String.format("Unknown format: %s", usage));
                    }
                }

                default -> throw new RuntimeException(String.format("Unknown format: %s", usage));
            }

            posDescription.offset(((VertexFormatMixed) (vertexFormat)).getOffset(i));
        }

        return attributeDescriptions.rewind();
    }
}
