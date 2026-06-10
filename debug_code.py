import re

def check_file(path):
    with open(path, 'r') as f:
        content = f.read()
    
    # Check for duplicated function names
    fun_names = re.findall(r'fun\s+([a-zA-Z0-9_]+)', content)
    seen = {}
    for name in fun_names:
        if name in seen:
            seen[name] += 1
            print(f"DUPLICATE FUN: {name} (count: {seen[name]})")
        else:
            seen[name] = 1

    # Check for mismatched braces
    stack = 0
    lines = content.splitlines()
    for i, line in enumerate(lines):
        stack += line.count('{')
        stack -= line.count('}')
        if stack < 0:
            print(f"BRACE ERROR: Negative stack at line {i+1}: {line.strip()}")
            stack = 0
    if stack > 0:
        print(f"BRACE ERROR: Unclosed braces at end: {stack}")

check_file('feature-canvas/src/main/java/com/inkframe/feature/canvas/StudioScreen.kt')
check_file('feature-canvas/src/main/java/com/inkframe/feature/canvas/StudioState.kt')
