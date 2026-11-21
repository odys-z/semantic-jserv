from pathlib import Path
from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson

from semantier_gen.__main__ import gen_peers
from semantier_gen.io.oz.semanticpeer.java_generator import j_type
from semanticshare.io.odysz.reflect import PeerSettings


class GenJavaTest(TestCase):

    def test_j_type(self):
        self.assertEqual(('String', set()), j_type('string'))
        self.assertEqual(('boolean', set()), j_type('boolean'))
        self.assertEqual(('HashMap<String, String>', {'java.util.HashMap'}), j_type('map<string, string'))
        self.assertEqual(('ArrayList<Integer>', {'java.util.ArrayList'}), j_type('list<int'))
        self.assertEqual(('ArrayList<Device>', {'java.util.ArrayList', 'io.odysz.semantic.tier.docs.Device'}),
                         j_type('list<io.odysz.semantic.tier.docs.Device'))

    def test_(self):
        testpath = 'test'
        ast_path = testpath + '/ast'
        settings = cast(PeerSettings, Anson.from_file(testpath + '/settings/t_05-gen-java.json'))

        cpp, java = gen_peers(settings, ast_path)

        self.assertTrue(cpp.exists())
        with (open(testpath + '/expect/t_05_AppSettings.java', 'r') as e,
              open(java / 'io/oz/jserv/docs/syn/singleton/AppSettings2.java', 'r') as f):
            self.assertEqual(e.readlines(), f.readlines())
