#!/usr/bin/env python3
"""Fail if either application needs the other application's Java code or Maven artifact."""
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
for app, other in [('client', 'server'), ('server', 'client')]:
    project = ET.parse(ROOT / app / 'pom.xml').getroot()
    assert project.find('m:parent', NS) is None, f'{app} still requires a parent POM'
    for dependency in project.findall('m:dependencies/m:dependency', NS):
        assert dependency.findtext('m:groupId', namespaces=NS) != 'com.example.auction', f'{app}: internal artifact dependency'
    for source in (ROOT / app / 'src').rglob('*.java'):
        imports = re.findall(r'^import\s+(?:static\s+)?([\w.]+)', source.read_text(), re.M)
        for name in imports:
            assert not name.startswith(f'com.example.auction.{other}.'), f'{source}: imports {name}'
            if app == 'client':
                assert not name.startswith('com.example.auction.common.'), f'{source}: server domain dependency'
    print(f'PASS: {app} has its own POM, protocol and tests; no imports from {other}')

server_jar = ROOT / 'server/target/server-1.0-SNAPSHOT-all.jar'
if server_jar.exists():
    with zipfile.ZipFile(server_jar) as archive:
        assert not any(n.startswith(('javafx/', 'com/example/auction/client/')) for n in archive.namelist())
    print('PASS: packaged server has no JavaFX/client classes')
client_jar = ROOT / 'client/target/client-1.0-SNAPSHOT.jar'
if client_jar.exists():
    with zipfile.ZipFile(client_jar) as archive:
        assert not any(n.startswith(('com/example/auction/server/', 'com/example/auction/common/')) for n in archive.namelist())
    print('PASS: packaged client has no server/domain/DAO classes')
