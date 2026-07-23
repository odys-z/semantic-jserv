from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson

from src.semantier_gen.io.oz.semanticpeer.generator2 import gen_peers
from semanticshare.io.odysz.reflect import PeerSettings

def replace_in_file(file_path: str, src: str, replace: str):
    try:
        # Read the file content
        with open(file_path, "r", encoding="utf-8") as file:
            content = file.read()

        # Perform the replacement
        updated_content = content.replace(src, replace)

        # Write the updated content back to the file
        with open(file_path, "w", encoding="utf-8") as file:
            file.write(updated_content)

        print(f"Successfully updated '{file_path}'.")

    except FileNotFoundError:
        print(f"Error: The file '{file_path}' was not found.")
    except Exception as e:
        print(f"An error occurred: {e}")

class GenJservTest(TestCase):

    def test_(self):
        testpath = 'test'
        settings = cast(PeerSettings, Anson.from_file(testpath + '/settings/t_03-anclient.cmake-doctier.json'))

        gen_peers(settings, ast_folder=testpath + '/ast')

        # Generator can tolerate the error, but the c++ peer generated cannot handle the error.
        replace_in_file(settings.cpp_gen,
                        "docsreq-test-case-body-invalid.ast.json",
                        "docsreq.ast.json")

        with (open('../../anson.cmake/tests/expect/t_10_doctier.hpp', 'r') as e,
              open(settings.cpp_gen, 'r') as f):
            self.assertEqual(e.readlines(), f.readlines())
