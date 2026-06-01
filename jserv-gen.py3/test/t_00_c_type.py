from unittest import TestCase

from src.semantier_gen.io.oz.semanticpeer.generator2 import c_type


class GenCtorsTest(TestCase):

    def test_ctype_parser(self):
        self.assertEqual('map<string, string>', c_type('map<string, string'))
        self.assertEqual('map<string, vector<string>>', c_type('map<string, list<string'))
        self.assertEqual('vector<vector<string>>', c_type('list<list<string'))

