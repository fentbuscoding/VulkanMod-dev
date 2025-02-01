# ...existing code...

# Add FSR source files
set(FSR_SOURCES
    ${CMAKE_SOURCE_DIR}/fsr/ffx_fsr1.cpp
    ${CMAKE_SOURCE_DIR}/fsr/ffx_fsr1.h
)

# ...existing code...

add_executable(VulkanMod-dev
    ${SOURCES}
    ${FSR_SOURCES}
)

# ...existing code...
