with open('feature-canvas/src/main/java/com/inkframe/feature/canvas/StudioScreen.kt', 'r') as f:
    lines = f.readlines()
    stack = 0
    for i, line in enumerate(lines):
        stack += line.count('{')
        stack -= line.count('}')
        if stack < 0:
            print(f"ERROR at line {i+1}: {line.strip()}")
            stack = 0
    print(f"Final stack: {stack}")
