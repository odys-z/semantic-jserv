from pathlib import Path
from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson

from src.semantier_gen.io.oz.semanticpeer.generator import gen_peers
from semanticshare.io.odysz.reflect import PeerSettings


class GenEchomsgTest(TestCase):

    def test_(self):
        testpath = Path('test')
        settings = cast(PeerSettings, Anson.from_file(str(testpath / 'settings/t_01-settings.json')))

        gen_peers(settings, testpath)

        with (open('../../anson.cmake/tests/expect/t_01_echomsg.hpp', 'r') as e,
              open(settings.cpp_gen, 'r') as f):
            self.assertEqual(e.readlines(), f.readlines())
