#!/usr/bin/env python3
"""Independent stdlib-only fallback; does not execute or validate verify.ps1.

Usage: python scripts/verify.py [--root REPOSITORY]
Exit codes: 0 pass, 1 violations, 2 missing/invalid inputs or I/O error.
"""

import argparse
import json
from pathlib import Path


def verify(root: Path) -> int:
    violations = False
    input_error = False
    try:
        lang = root / 'src/main/resources/assets/silkvis/lang'
        keys = []
        for name in ('en_us', 'ru_ru'):
            value = json.loads((lang / (name + '.json')).read_text(encoding='utf-8-sig'))
            if not isinstance(value, dict):
                raise ValueError(f'Expected a JSON object: {name}.json')
            keys.append(set(value))
        en, ru = keys
        missing_ru, missing_en = sorted(en - ru), sorted(ru - en)
        violations = bool(missing_ru or missing_en)
        label = 'FAIL' if violations else 'PASS'
        print(f'{label} lang: en_us={len(en)} ru_ru={len(ru)} '
              f'missing_in_ru={len(missing_ru)} missing_in_en={len(missing_en)}')
        for key in missing_ru:
            print(f'  missing_in_ru: {key}')
        for key in missing_en:
            print(f'  missing_in_en: {key}')
    except (OSError, UnicodeError, ValueError) as error:
        print(f'ERROR lang: {error}')
        input_error = True

    try:
        src = root / 'src'
        if not src.is_dir():
            raise ValueError('Missing src directory.')
        java_files = sorted(src.rglob('*.java'))
        if not java_files:
            raise ValueError('No Java sources found under src.')
        bad_files = 0
        for path in java_files:
            bad_file = False
            for line_number, line in enumerate(path.read_bytes().split(b'\n'), 1):
                for column, value in enumerate(line, 1):
                    if value > 127:
                        relative = path.relative_to(root).as_posix()
                        print(f'  non_ascii: {relative}:{line_number}:{column} byte=0x{value:02X}')
                        bad_file = True
                        break
            bad_files += bad_file
        label = 'FAIL' if bad_files else 'PASS'
        print(f'{label} ascii: java_files={len(java_files)} non_ascii_files={bad_files}')
        violations |= bool(bad_files)
    except OSError as error:
        print(f'ERROR ascii: {error}')
        input_error = True
    except ValueError as error:
        print(f'ERROR ascii: {error}')
        input_error = True

    result = 2 if input_error else 1 if violations else 0
    print('RESULT ' + ('ERROR' if input_error else 'FAIL' if violations else 'PASS'))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    raise SystemExit(verify(args.root.resolve()))
