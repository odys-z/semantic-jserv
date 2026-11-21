import sys
from pathlib import Path
from typing import cast, Union

from anson.io.odysz.anson import Anson
from semanticshare.io.odysz.reflect import PeerSettings

from semantier_gen.io.oz.semanticpeer.java_generator import gen_java_peer
from semantier_gen.io.oz.semanticpeer.py_generator import gen_cpp_peer2


def gen_peers(settings: PeerSettings, ast_folder: Union[str, Path] = None) -> tuple[Path, Path]:
    if Path is not None:
        settings.ast_folder = ast_folder

    return gen_cpp_peer2(settings), gen_java_peer(settings)


if __name__ == '__main__':
    print('Semantier Generator 0.1.0 ...')

    if len(sys.argv) < 2 or len(sys.argv) > 3:
        print('usage: semantier_gen <path to setting.json> [optional: ast-folder = settings.ast_folder]')
    else:
        print('Loading settings:', Path(sys.argv[1]).absolute())

        settings = cast(PeerSettings, Anson.from_file(sys.argv[1]))

        ast_folder = sys.argv[2] if len(sys.argv) > 2 else 'ast'
        print('Generating peers with ASTs in ', ast_folder)

        pypath, javapath = gen_peers(settings, ast_folder)

        print(f'Done. Genterated ASTs:\n', settings.ansons)
        print(f'Output:\n', 'python:\t', pypath.absolute(), 'java:\t', javapath.absolute())
