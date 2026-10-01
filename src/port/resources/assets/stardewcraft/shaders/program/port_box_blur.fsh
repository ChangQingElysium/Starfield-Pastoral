#version 150

// PORT(1.20.1): separable box blur used by the 1.21 menu background (Radius 5 = 1.21 default blurriness).
// Linear filtering lets every tap average two texels: taps sit between pixel pairs, stepping two pixels at a
// time, and the outermost pixel (the window is 2r + 1 wide) is added with half weight.
uniform sampler2D DiffuseSampler;
uniform float Radius;
uniform float RadiusMultiplier;

in vec2 texCoord;
in vec2 sampleStep;

out vec4 fragColor;

void main() {
    float radius = round(Radius * RadiusMultiplier);
    vec4 sum = vec4(0.0);
    for (float offset = -radius + 0.5; offset <= radius; offset += 2.0) {
        sum += texture(DiffuseSampler, texCoord + sampleStep * offset);
    }
    sum += texture(DiffuseSampler, texCoord + sampleStep * radius) / 2.0;
    fragColor = sum / (radius + 0.5);
}
