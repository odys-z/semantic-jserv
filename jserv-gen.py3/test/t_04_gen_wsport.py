from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson

from src.semantier_gen.io.oz.semanticpeer.generator2 import gen_peers
from semanticshare.io.odysz.reflect import PeerSettings


class GenSemantierTest(TestCase):

    def test_(self):
        testpath = 'test'
        settings = cast(PeerSettings, Anson.from_file(testpath + '/settings/t_04-gen-wsport.json'))

        gen_peers(settings, ast_folder= testpath + '/ast')

        with (open('../../anclient/examples/example.slint/app/src/gen/wsport.hpp', 'r') as f,
              open(settings.cpp_gen, 'r') as e):
            self.assertEqual(e.readlines(), f.readlines())
