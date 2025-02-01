package net.vulkanmod.vulkan;

import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.GlobalMemory;
import oshi.software.os.OperatingSystem;

public class VulkanSystemInfo {
    public static final String cpuInfo;
    public static final String memoryInfo;
    public static final String osInfo;

    static {
        SystemInfo systemInfo = new SystemInfo();
        CentralProcessor centralProcessor = systemInfo.getHardware().getProcessor();
        GlobalMemory memory = systemInfo.getHardware().getMemory();
        OperatingSystem os = systemInfo.getOperatingSystem();

        cpuInfo = String.format("%s", centralProcessor.getProcessorIdentifier().getName()).replaceAll("\\s+", " ");
        memoryInfo = String.format("Total: %d MB, Available: %d MB", memory.getTotal() / (1024 * 1024), memory.getAvailable() / (1024 * 1024));
        osInfo = String.format("%s %s", os.getFamily(), os.getVersionInfo().getVersion());
    }
}
