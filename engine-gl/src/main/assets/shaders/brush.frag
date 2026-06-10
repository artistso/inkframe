#version 300 es
precision highp float;

uniform vec4 uColor;
uniform float uHardness;
uniform bool uGlowEnabled;
uniform bool uNodeMode;
uniform float uTime;

in float vAngle;
in float vFlow;
out vec4 fragColor;

void main() {
    vec2 p = gl_PointCoord * 2.0 - 1.0;
    float dist = length(p);

    if (uNodeMode) {
        float core = 1.0 - smoothstep(0.0, 0.15, dist);
        float aura = exp(-dist * 4.0) * 0.4;
        float alpha = (core + aura) * uColor.a;
        if (alpha <= 0.0) discard;
        vec3 nodeRgb = mix(uColor.rgb, vec3(1.0), core);
        fragColor = vec4(nodeRgb, alpha);
        return;
    }

    float inner = clamp(uHardness, 0.0, 0.98);
    float falloff = 1.0 - smoothstep(inner, 1.0, dist);
    
    float alpha = falloff * vFlow * uColor.a;
    if (alpha <= 0.0) discard;

    vec3 finalRgb = uColor.rgb;
    
    if (uGlowEnabled) {
        // Andrew Kramer "Saber" Style: Flicker + Core Intensity
        float flicker = 0.95 + 0.05 * sin(uTime * 15.0);
        float centerGlow = exp(-dist * 2.5) * flicker;
        float whiteCore = exp(-dist * 10.0) * 2.0; 
        
        finalRgb = mix(finalRgb, vec3(1.0), clamp(whiteCore, 0.0, 1.0));
        finalRgb += centerGlow * vec3(0.0, 0.9, 1.0); 
        alpha = mix(alpha, 1.0 - pow(dist, 3.5), 0.6) * flicker;
    }

    fragColor = vec4(finalRgb, alpha);
}
