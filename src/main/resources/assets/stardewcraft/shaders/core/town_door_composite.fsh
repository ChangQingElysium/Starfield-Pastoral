#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform vec2 ScreenSize;
out vec4 fragColor;

void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    if (gl_FragCoord.z > texture(Sampler1, uv).r) discard;
    fragColor = vec4(texture(Sampler0, uv).rgb, 1.0);
    gl_FragDepth = min(gl_FragCoord.z, texture(Sampler2, uv).r);
}
