#!/usr/bin/env python3
"""Generate the JSON document consumed by ReVanced Manager 2.6.0."""
from datetime import datetime, timezone
from pathlib import Path
import json, re, sys
version = sys.argv[1]
if not re.fullmatch(r'v\d+\.\d+\.\d+(?:-[A-Za-z0-9.-]+)?', version):
    raise SystemExit('Expected a vMAJOR.MINOR.PATCH release tag')
feed = {
    'version': version.removeprefix('v'),
    'created_at': datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%S'),
    'download_url': f'https://github.com/permissionBRICK/youtube-home-assistant-patches/releases/download/{version}/youtube-home-assistant.rvp',
    'description': 'Send the current YouTube video and position to your existing Home Assistant webhook.'
}
Path('build/libs/patches.json').write_text(json.dumps(feed, indent=2) + '\n')
