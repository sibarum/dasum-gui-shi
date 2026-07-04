#version 330 core
// Fixed vertex stage for RaymarchLayer: transform the unit bounding cube
// into world space via center + per-axis half-extent, and hand the world
// position to the fragment stage for camera-ray construction. Paired with
// any caller-supplied fragment shader (RaymarchMaterial links this vert
// with each distinct fragment source).
layout(location = 0) in vec3 a_pos;   // unit cube corner in [-1, 1]
uniform mat4 u_mvp;
uniform vec3 u_center;
uniform vec3 u_halfExtent;
out vec3 v_world;

void main() {
    v_world = u_center + a_pos * u_halfExtent;
    gl_Position = u_mvp * vec4(v_world, 1.0);
}
