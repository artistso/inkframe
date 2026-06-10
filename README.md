# InkFrame Studio

A GPU-accelerated 2D bitmap **animation studio for Android**, built in partnership between **artistso** and **Arena AI**.

> **Status:** Active development. High-precision rendering, Fibonacci-based UI, and Vector-hybrid engine.

---

## 🎨 Our Philosophy
InkFrame is designed for the perfectionist. We leverage OpenGL ES 3.0 for buttery-smooth performance and unique "Donut" transparency UI elements to keep you focused on your art.

### 🛡️ Privacy & Ethics
- **No Subscriptions.**
- **No Data Collection.**
- **No Data Retention.**
- **No Ads.**
- **Offline First.**

## 🚀 Vision & Proposal
For a deep dive into our engineering goals, Fibonacci-based timeline designs, and the future of the Specter connection mode, see **[VISION.md](VISION.md)**.

## 🛠 Tech Stack
- **Language:** Kotlin
- **UI:** Jetpack Compose (Fibonacci-influenced layout)
- **Rendering:** OpenGL ES 3.0 (`GLSurfaceView` + custom renderer)
- **Min / Target SDK:** 26 (Android 8) / 34
- **Build System:** Gradle 8.9 + Kotlin 2.0.0

## 🤝 Partners & Credit
Developed by **artistso** (Visionary Founder) & **Arena AI** (Engineering Agent). 

*Note: This project is currently proprietary. We are not open for commercial cloning or redistribution at this stage.*

## 🐞 Feedback & Debugging
If you encounter bugs on your device or have performance feedback, please open an Issue or leave a comment on the GitHub repository. We look into every report.

---

## Module layout
```
:app              Android application, MainActivity, theme
:core-common      Pure-Kotlin math (Vec2, lerp, Catmull-Rom) + UndoStack
:core-model       Document model: Project, Scene, Layer, Cel, Brush, RgbaColor
:engine-gl        OpenGL ES paint engine: surfaces, brush renderer, compositor, stroke processor
:feature-canvas   Compose canvas screen, GL view host, StudioState
:feature-timeline (placeholder for timeline UI extraction)
:feature-layers   (placeholder for layer-panel UI extraction)
```

## License
Copyright © 2026 artistso. All rights reserved.
