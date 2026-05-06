
from dataclasses import dataclass
from pathlib import Path
from typing import List, cast, Union

from anson.io.odysz.anson import Anson, AnsonField
from anson.io.odysz.common import Utils

from semanticshare.io.odysz.semantic import PeerSettings


def gen_cpp_peer(settings: PeerSettings, ast_folder: Path):
    '''
    :param settings:
    :param config_path:
    :return:
    '''

    msglines = MsgLines()

    gen_pth = Path(settings.cpp_gen)
    gen_pth.parent.mkdir(parents=True, exist_ok=True)

    with open(gen_pth, 'w') as gen:
        gen.writelines(msglines.start_header)

        for astjson in settings.anRequests:
            if Path(ast_folder / astjson).exists():
                ast = cast(AnsonBodyAst, Anson.from_file(str(ast_folder / astjson)))
                gen.writelines(msglines.specialize_req(ast))
            else:
                Utils.warn('Cannot find file ' + astjson)

        gen.writelines(msglines.end_ns)


def gen_peers(settings: PeerSettings, config_path: Path) -> None:
    # gen_ts_peer(settings)
    # gen_py_peer(settings)
    gen_cpp_peer(settings, config_path)
