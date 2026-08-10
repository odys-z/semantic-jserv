import sys
from pathlib import Path
from typing import cast

from anson.io.odysz.anson import Anson
from semanticshare.io.odysz.reflect import PeerSettings

# pip install -e . for debug source
# mark src directory as source root
from semantier_gen.io.oz.semanticpeer.generator2 import gen_peers

# from semantier_gen.io.oz.semanticpeer.generator2 import gen_peers

if __name__ == '__main__':
    print('Semantier Generator 0.1.0 ...')

    if len(sys.argv) < 2 or len(sys.argv) > 3:
        print('usage: semantier_gen <path to setting.json> [optional: ast-folder = settings.ast_folder]')
    else:
        print('Loading settings:', Path(sys.argv[1]).absolute())

        settings = cast(PeerSettings, Anson.from_file(sys.argv[1]))

        ast_folder = sys.argv[2] if len(sys.argv) > 2 else 'ast'
        print('Generating peers with ASTs in ', ast_folder)

        outpth = gen_peers(settings, ast_folder)

        print(f'Done. Genterated ASTs:\n', settings.ansons)
        print(f'Output:', outpth.absolute())
