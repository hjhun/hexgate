#!/usr/bin/env python3
"""Collect only synthetic integration artifacts; preserve bytes across Windows adb pipes."""
import base64
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parent.parent
adb = os.environ.get('HEXGATE_ADB', 'adb')
base = [adb, '-P', os.environ.get('HEXGATE_ADB_PORT', '5037'), '-s', os.environ.get('HEXGATE_SERIAL', 'emulator-5554')]
prefix = os.environ.get('HEXGATE_EVIDENCE_PREFIX', 'final-fold')
if not prefix or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-' for c in prefix):
    raise SystemExit('Use an alphanumeric evidence prefix.')
artifacts = root / 'artifacts'
artifacts.mkdir(exist_ok=True)
files = ['performance.json', 'herdr-tablet.png', 'markdown-mermaid.png', 'drawio.png', 'embedded-drawio.png', 'process-recovery.png', 'fold-open.png', 'fold-closed.png', 'fold-rotated.png', 'fold-open-preview.png', 'appearance-jetbrains.png', 'appearance-fira.png', 'appearance-source.png', 'panel-connections.png', 'panel-settings.png', 'theme-light.png', 'theme-amber.png', 'theme-dracula.png']
for name in files:
    result = subprocess.run(base + ['shell', 'run-as', 'dev.hexgate', 'base64', 'cache/' + name], capture_output=True)
    if result.returncode:
        print('Not available:', name)
        continue
    data = base64.b64decode(result.stdout, validate=False)
    if name.endswith('.png') and not data.startswith(b'\x89PNG\r\n\x1a\n'):
        raise SystemExit('Invalid PNG: ' + name)
    (artifacts / (prefix + '-' + name)).write_bytes(data)
    print('Collected:', prefix + '-' + name)
