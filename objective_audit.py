import os
import re

def objective_audit():
    print("--- INKFRAME OBJECTIVE AI AUDIT START ---")
    files_to_check = []
    for root, dirs, files in os.walk("."):
        if any(x in root for x in [".git", ".gradle", "build"]): continue
        for f in files:
            if f.endswith(".kt") or f.endswith(".kts"):
                files_to_check.append(os.path.join(root, f))

    all_defined_classes = set()
    all_imports = {}
    
    # Pass 1: Collect all class names and packages
    for path in files_to_check:
        with open(path, 'r') as f:
            content = f.read()
            # Find class/interface/object names
            names = re.findall(r'(?:class|interface|object)\s+([a-zA-Z0-9_]+)', content)
            all_defined_classes.update(names)
            
            # Record imports
            imports = re.findall(r'import\s+([\w\.]+)', content)
            all_imports[path] = imports

    # Pass 2: Check for obvious "Unresolved Reference" risk
    for path in files_to_check:
        filename = os.path.basename(path)
        with open(path, 'r') as f:
            content = f.read()
            
            # Check for common problematic references from previous errors
            if "Vec2" in content and "import" not in content and "Vec2.kt" not in filename:
                if "package com.inkframe.core.common" not in content:
                    print(f"[AUDIT ERROR] {path}: Possible unresolved reference to 'Vec2'")
            
            if "CanvasRenderer" in content and "import" not in content and "CanvasRenderer.kt" not in filename:
                if "package com.inkframe.engine.gl" not in content:
                    print(f"[AUDIT ERROR] {path}: Possible unresolved reference to 'CanvasRenderer'")

            # Check for line 134 specifically in the failing file if possible
            # Based on user feedback, let's look at line 134 of StudioScreen.kt
            if "StudioScreen.kt" in path:
                lines = content.splitlines()
                if len(lines) >= 134:
                    print(f"[AUDIT INFO] StudioScreen.kt:134: {lines[133]}")

    print("--- INKFRAME OBJECTIVE AI AUDIT END ---")

objective_audit()
