import PyInstaller.__main__ as pi
import os

print('Building with setup-gui.spec ...')
pi.run(['setup-gui.spec'])

print('Building setup-cli.spec ...')
pi.run(['setup-cli.spec'])
