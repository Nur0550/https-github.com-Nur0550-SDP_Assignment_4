import subprocess
import glob
import os
import sys

project_dir = os.path.dirname(os.path.abspath(__file__))
main_classes = os.path.join(project_dir, 'target', 'classes')
test_classes = os.path.join(project_dir, 'target', 'test-classes')
lib_jar = os.path.join(project_dir, 'lib', 'junit-platform-console-standalone-1.10.2.jar')

os.makedirs(main_classes, exist_ok=True)
os.makedirs(test_classes, exist_ok=True)

main_sources = glob.glob(os.path.join(project_dir, 'src', 'main', 'java', '**', '*.java'), recursive=True)
test_sources = glob.glob(os.path.join(project_dir, 'src', 'test', 'java', '**', '*.java'), recursive=True)

if main_sources:
    print(f"Compiling {len(main_sources)} main source files...")
    res = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', main_classes] + main_sources)
    if res.returncode != 0:
        sys.exit(res.returncode)

if test_sources:
    print(f"Compiling {len(test_sources)} test source files...")
    cp = f"{main_classes};{lib_jar}"
    res = subprocess.run(['javac', '-encoding', 'UTF-8', '-cp', cp, '-d', test_classes] + test_sources)
    if res.returncode != 0:
        sys.exit(res.returncode)

    print("Running JUnit 5 Platform Console Standalone...")
    test_cp = f"{main_classes};{test_classes}"
    res = subprocess.run([
        'java', '-jar', lib_jar, 'execute',
        '--class-path', test_cp,
        '--scan-class-path',
        '--details=flat'
    ])
    sys.exit(res.returncode)
