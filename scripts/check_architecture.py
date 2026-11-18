#!/usr/bin/env python3
"""Check the three-module layout and runtime separation of the two applications."""
from pathlib import Path
import re
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
NS = {'m': 'http://maven.apache.org/POM/4.0.0'}
root_pom = ET.parse(ROOT / 'pom.xml').getroot()
modules = [m.text for m in root_pom.findall('m:modules/m:module', NS)]
assert modules == ['common', 'server', 'client'], f'unexpected Maven modules: {modules}'
print('PASS: root reactor has exactly common, server, client modules')
for app, other in [('client', 'server'), ('server', 'client')]:
    project = ET.parse(ROOT / app / 'pom.xml').getroot()
    assert project.find('m:parent', NS) is not None, f'{app} must inherit the root POM'
    for dependency in project.findall('m:dependencies/m:dependency', NS):
        artifact = dependency.findtext('m:artifactId', namespaces=NS)
        scope = dependency.findtext('m:scope', namespaces=NS)
        if app == 'client' and artifact == 'server':
            assert scope == 'test', 'client may use server only for integration tests'
        elif app == 'server':
            assert artifact != 'client', 'server must not depend on client'
    for source in (ROOT / app / 'src').rglob('*.java'):
        is_test = 'src/test/java' in source.as_posix()
        imports = re.findall(r'^import\s+(?:static\s+)?([\w.]+)', source.read_text(), re.M)
        for name in imports:
            if app == 'client' and name.startswith('com.example.auction.server.'):
                assert is_test, f'{source}: runtime imports server class {name}'
                continue
            assert not name.startswith(f'com.example.auction.{other}.'), f'{source}: imports {name}'
            if app == 'client':
                assert not name.startswith('com.example.auction.server.'), f'{source}: runtime imports server'
                assert not name.startswith('com.example.auction.common.'), f'{source}: client imports domain'
    print(f'PASS: {app} inherits root POM and has no production imports from {other}')

server_jar = ROOT / 'server/target/server-1.0-SNAPSHOT-all.jar'
if server_jar.exists():
    with zipfile.ZipFile(server_jar) as archive:
        assert any(n.startswith('com/example/auction/common/model/') for n in archive.namelist())
        assert not any(n.startswith(('javafx/', 'com/example/auction/client/')) for n in archive.namelist())
    print('PASS: server JAR contains common domain but no JavaFX/client classes')
client_jar = ROOT / 'client/target/client-1.0-SNAPSHOT.jar'
if client_jar.exists():
    with zipfile.ZipFile(client_jar) as archive:
        assert not any(n.startswith(('com/example/auction/server/', 'com/example/auction/common/')) for n in archive.namelist())
    print('PASS: packaged client has no server/domain/DAO classes')
