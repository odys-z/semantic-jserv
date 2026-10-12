'''
Validators for Qt (PySide6) widgets, wrapping the prompt validators in validators.py,
so that a data widget and synode-cli check the same rules and give the user the same error.
GUI only: synode-cli must not import this module, as Qt needs system libraries (libEGL.so.1, ...).
@since 0.7.7, moved from validators.py in 0.8.0
'''

from PySide6.QtGui import QValidator
from prompt_toolkit.document import Document

from synodepy3.validators import PIPValidator, PJservValidator

class QIPValidator(QValidator):
    '''
    Not Used
    ========
    '''
    pv: PIPValidator

    def __init__(self):
        super().__init__()
        self.pv = PIPValidator()

    def validate(self, arg__1, arg__2, /):
        try:
            self.pv.validate(Document(arg__1))
            return QValidator.State.Acceptable
        except: return QValidator.State.Invalid

class QJservValidator(QValidator):
    '''
    Not Used
    ========
    @deprecated
    Synodes protocol validator ('jserv-album')
    '''

    def __init__(self, protcol_rrot: str = None):
        super().__init__()
        self.pv = PJservValidator(protcol_rrot)

    def validate(self, arg__1, arg__2, /):
        try:
            self.pv.validate(Document(arg__1))
            return QValidator.State.Acceptable
        except: return QValidator.State.Invalid
