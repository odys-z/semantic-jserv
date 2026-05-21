from pathlib import Path
from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson

from src.semantier_gen.io.oz.semanticpeer.generator2 import gen_peers
from semanticshare.io.odysz.reflect import PeerSettings


class GenJservTest(TestCase):

    def test_(self):
        testpath = 'test'
        settings = cast(PeerSettings, Anson.from_file(testpath + '/settings/t_02-jserv.json'))

        gen_peers(settings, testpath)

        with (open('../../anson.cmake/tests/expect/t_02_jserv.hpp', 'r') as e,
              open(settings.cpp_gen, 'r') as f):
            self.assertEqual(e.readlines(), f.readlines())

        with (open('../../anson.cmake/src/io/odysz/gen/jserv.hpp', 'r') as f,
              open(settings.cpp_gen, 'r') as e):
            self.assertEqual(e.readlines(), f.readlines())
