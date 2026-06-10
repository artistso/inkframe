#version 300 es
precision highp float;

uniform vec4 uColor;
uniform float uHardness;
uniform bool uGlowEnabled;
uniform bool uNodeMode; // New: Renders points as sharp "Quantum Nodes"

in float vAngle;
in float vFlow;
out vec4 fragColor;

void main() {
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float dist = length(p);

    if (uNodeMode) {
        // Quantum Node: A sharp core with a massive outer "aura"
        float core = 1.0 - smoothstep(0.0, 0.15, dist);
        float aura = exp(-dist * 4.0) * 0.4;
        float alpha = (core + aura) * uColor.a;
        if (alpha <= 0.0) discard;
        vec3 nodeRgb = mix(uColor.rgb, vec3(1.0), core); // White hot core
        fragColor = vec4(nodeRgb, alpha);
        return;
    }

    float inner = clamp(uHardness, 0.0, 0.98);
    float falloff = 1.0 - smoothstep(inner, 1.0, dist);
    
    float alpha = falloff * vFlow * uColor.a;
    if (alpha <= 0.0) discard;

    vec3 finalRgb = uColor.rgb;
    
    if (uGlowEnabled) {
        float centerGlow = exp(-dist * 3.0);
        finalRgb += centerGlow * vec3(0.5, 0.8, 1.0);
        alpha = mix(alpha, 1.0 - pow(dist, 4.0), 0.5);
    }

    fragColor = vec4(finalRgb, alpha);
}
