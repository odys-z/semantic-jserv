"""
    debug/run as script
    working folder: peers.py3
"""

import os
import unittest

from anson.io.odysz.common import Utils

def run_script(script_path):
    python = 'py' if Utils.iswindows() else 'python3'
    os.system(f'{python} {script_path}')

test_loader = unittest.TestLoader()
test_suite = test_loader.discover(start_dir='test', pattern='t_*.py')


if __name__ == '__main__':
    unittest.TextTestRunner(verbosity=2).run(test_suite)
    print('OK: Semantics.py3/test.test')
