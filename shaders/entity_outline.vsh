#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec3 inNormal;
layout(location = 2) in vec2 inTexCoord;

layout(location = 0) out vec4 fragColor;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    vec4 worldPosition = model * vec4(inPosition, 1.0);
    vec4 viewPosition = view * worldPosition;
    gl_Position = projection * viewPosition;

    vec4 outlineColor = vec4(1.0, 0.0, 0.0, 1.0); // Example correction

    fragColor = outlineColor; // Ensure the line follows proper GLSL syntax
}
