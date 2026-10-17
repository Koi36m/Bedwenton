"""Standalone package-integrity guard; reusable on owner and CI machines."""
import sys
import zipfile

def verify(path, version, engine=None):
    with zipfile.ZipFile(path) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)): raise ValueError('Duplicate ZIP entries')
        if jar.testzip() is not None: raise ValueError('ZIP CRC failure')
        banned = ('org/bukkit/', 'io/papermc/', 'net/kyori/', 'com/mojang/', 'org/junit/', 'org/junit/platform/', 'org/opentest4j/', 'org/apiguardian/')
        if any(n.startswith(banned) for n in names): raise ValueError('Server/test dependency bundled')
        required = ['plugin.yml', 'config.yml', 'META-INF/bedwenton-build.properties',
                    'workshop/vfx/delivery/DeliveryPlanner.class', 'workshop/vfx/paper/bukkit/VfxDemoPlugin.class']
        if any(n not in names for n in required): raise ValueError('Required plugin content missing')
        text = jar.read('plugin.yml').decode()
        if '${' in text or not any(line.strip().strip("'\"") == version for line in
            [s.split(':',1)[1].strip() for s in text.splitlines() if s.startswith('version:')]):
            raise ValueError('Unexpanded or incorrect plugin version')
        if any(s.startswith('commands:') for s in text.splitlines()): raise ValueError('Commands must use the Paper API')
        if engine:
            with zipfile.ZipFile(engine) as source:
                classes = [n for n in source.namelist() if n.endswith('.class')]
                if not classes: raise ValueError('Engine class set is empty')
                for n in classes:
                    if n not in names or jar.read(n) != source.read(n): raise ValueError('Engine class changed or missing: ' + n)
                print('PASS: all engine classes embedded byte-identically; classes=' + str(len(classes)))
        print('PASS: plugin JAR integrity; entries=' + str(len(names)))

if __name__ == '__main__': verify(sys.argv[1],sys.argv[2],sys.argv[3] if len(sys.argv)>3 else None)
