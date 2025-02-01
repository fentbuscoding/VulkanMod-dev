// ...existing code...

void createGraphicsPipeline() {
    // ...existing code...

    VkShaderModule fxaaFragShaderModule = createShaderModule(readFile("shaders/fxaa.frag.spv"));

    VkPipelineShaderStageCreateInfo fxaaFragShaderStageInfo = {};
    fxaaFragShaderStageInfo.sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO;
    fxaaFragShaderStageInfo.stage = VK_SHADER_STAGE_FRAGMENT_BIT;
    fxaaFragShaderStageInfo.module = fxaaFragShaderModule;
    fxaaFragShaderStageInfo.pName = "main";

    VkPipelineShaderStageCreateInfo shaderStages[] = {vertShaderStageInfo, fxaaFragShaderStageInfo};

    // ...existing code...

    vkDestroyShaderModule(device, fxaaFragShaderModule, nullptr);
}

// ...existing code...
