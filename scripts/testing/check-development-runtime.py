#!/usr/bin/env python3
"""Reproduce a late-loaded charge dependency disappearing during a dev rebuild."""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser()
parser.add_argument('--unprotected', action='store_true')
args = parser.parse_args()
repo = Path(__file__).resolve().parents[2]
with tempfile.TemporaryDirectory(prefix='goat-runtime-') as directory:
    root = Path(directory)
    (root / 'src/main/java').mkdir(parents=True)
    (root / 'settings.gradle').write_text("rootProject.name = 'late-charge-class'\n")
    script = str(repo / 'scripts/development/runtime-snapshot.gradle').replace("'", "\\'")
    (root / 'build.gradle').write_text("""plugins { id 'java' }
tasks.register('runClient', JavaExec) {
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'Probe'
    workingDir = projectDir
}
""" + ('' if args.unprotected else f"apply from: '{script}'\n"))
    (root / 'src/main/java/GoatCharge.java').write_text('''public class GoatCharge {
    static void tick() { System.out.println(new Landing(200, 40)); }
    private record Landing(long expires, int duration) {}
}''')
    (root / 'src/main/java/Probe.java').write_text('''import java.nio.file.*;
public class Probe {
    public static void main(String[] args) throws Exception {
        Class.forName("GoatCharge"); // The hit-only nested class has not loaded yet.
        Files.writeString(Path.of("ready"), "ready");
        while (!Files.exists(Path.of("hit"))) Thread.sleep(25);
        GoatCharge.tick();
        System.out.println("PASS late-loaded charge dependency survives output deletion");
    }
}''')
    with (root / 'run.log').open('w') as log:
        process = subprocess.Popen([str(repo / 'gradlew'), '-p', str(root), 'runClient', '--quiet'], stdout=log, stderr=log)
        try:
            deadline = time.monotonic() + 60
            while not (root / 'ready').exists():
                if process.poll() is not None or time.monotonic() >= deadline:
                    raise RuntimeError((root / 'run.log').read_text())
                time.sleep(.05)
            shutil.rmtree(root / 'build/classes')
            (root / 'hit').touch()
            code = process.wait(timeout=30)
            output = (root / 'run.log').read_text()
            print(output)
            if code or 'PASS late-loaded' not in output:
                raise SystemExit(1)
        finally:
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)
