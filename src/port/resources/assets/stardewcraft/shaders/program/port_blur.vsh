#version 150

// PORT(1.20.1): post-pass vertex stage for the 1.21 menu background blur (1.20.1 ships no blur.vsh).
in vec4 Position;

uniform mat4 ProjMat;
uniform vec2 InSize;
uniform vec2 OutSize;
uniform vec2 BlurDir;

out vec2 texCoord;
out vec2 sampleStep;

void main() {
    vec4 projected = ProjMat * vec4(Position.xy, 0.0, 1.0);
    gl_Position = vec4(projected.xy, 0.2, 1.0);
    sampleStep = BlurDir / InSize;
    texCoord = Position.xy / OutSize;
}
