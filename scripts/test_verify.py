#!/usr/bin/env python3
"""Exercise verifier CLIs against isolated fixtures; never edit project sources."""

import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


def main() -> int:
    scripts = Path(__file__).resolve().parent
    runners = [('python', [sys.executable, str(scripts / 'verify.py')], '--root')]
    powershell = shutil.which('pwsh') or shutil.which('powershell')
    if powershell:
        runners.append(('powershell', [powershell, '-NoProfile', '-File',
                                      str(scripts / 'verify.ps1')], '-RepositoryRoot'))
    else:
        print('NOT_RUN powershell: neither pwsh nor powershell is installed')
    cases = [
        ('matching', 0, 'RESULT PASS'),
        ('missing_ru', 1, 'missing_in_ru: extra'),
        ('missing_en', 1, 'missing_in_en: extra'),
        ('case_sensitive', 1, 'missing_in_ru: Key'),
        ('unicode_main', 1, 'non_ascii: src/Main.java:2:4'),
        ('unicode_test', 1, 'non_ascii: src/test/Test.java:1:4'),
        ('bom_java', 1, 'non_ascii: src/Main.java:1:1'),
        ('malformed_json', 2, 'ERROR lang:'),
        ('array_json', 2, 'ERROR lang:'),
        ('missing_json', 2, 'ERROR lang:'),
        ('missing_src', 2, 'ERROR ascii:'),
        ('no_java', 2, 'ERROR ascii:'),
    ]
    failures = 0
    total = 0
    for runner_name, command, root_option in runners:
        for case, expected, marker in cases:
            total += 1
            with tempfile.TemporaryDirectory(prefix='silkvis verifier ') as tmp:
                root = Path(tmp) / 'repo with spaces'
                lang = root / 'src/main/resources/assets/silkvis/lang'
                lang.mkdir(parents=True)
                en, ru = {'key': 'English'}, {'key': 'Russian'}
                if case == 'missing_ru': en['extra'] = 'Extra'
                if case == 'missing_en': ru['extra'] = 'Extra'
                if case == 'case_sensitive': en = {'Key': 'Upper'}
                for name, value in [('en_us', en), ('ru_ru', ru)]:
                    (lang / (name + '.json')).write_text(json.dumps(value), encoding='utf-8')
                java = root / 'src/Main.java'
                java.write_bytes(b'class Main {}\n')
                if case == 'unicode_main': java.write_bytes(b'class Main {}\n// \xc3\xa9\n')
                if case == 'unicode_test':
                    test = root / 'src/test/Test.java'
                    test.parent.mkdir()
                    test.write_bytes(b'// \xd0\x91\n')
                if case == 'bom_java': java.write_bytes(b'\xef\xbb\xbfclass Main {}\n')
                if case == 'malformed_json': (lang / 'ru_ru.json').write_text('{')
                if case == 'array_json': (lang / 'ru_ru.json').write_text('[]')
                if case == 'missing_json': (lang / 'ru_ru.json').unlink()
                if case == 'missing_src': shutil.rmtree(root / 'src')
                if case == 'no_java': java.unlink()
                result = subprocess.run(command + [root_option, str(root)], cwd=tmp,
                                        capture_output=True, text=True, timeout=30)
                output = result.stdout + result.stderr
                passed = result.returncode == expected and marker in output
                print(f'{"PASS" if passed else "FAIL"} {runner_name}/{case}: '
                      f'exit={result.returncode} expected={expected}')
                if not passed:
                    failures += 1
                    print(output)
    print(f'verifier_fixture_checks={total} failed={failures}')
    return int(failures > 0)


if __name__ == '__main__':
    raise SystemExit(main())
