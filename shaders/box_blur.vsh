#version 450

layout(location = 0) in vec3 inPosition;
layout(location = 1) in vec2 inTexCoord;

layout(location = 0) out vec2 fragTexCoord;

uniform mat4 model;
uniform mat4 view;
uniform mat4 projection;

void main() {
    vec4 worldPosition = model * vec4(inPosition, 1.0);
    vec4 viewPosition = view * worldPosition;
    gl_Position = projection * viewPosition;

    fragTexCoord = inTexCoord;
}
