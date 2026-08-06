#version 330 core
layout(location = 0) in vec3 a_pos;
layout(location = 1) in vec3 a_color;
layout(location = 2) in vec3 a_normal;
uniform mat4 u_mvp;
out vec3 v_color;
out vec3 v_normal;

void main() {
    v_color = a_color;
    // Positions and normals are already in world space (the scene bakes its
    // box transform into both, so no separate model/normal matrix here).
    v_normal = a_normal;
    gl_Position = u_mvp * vec4(a_pos, 1.0);
}
