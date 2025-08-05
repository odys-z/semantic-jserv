from src.io.odysz.semantic.jprotocol import AnsonBody, AnsonMsg


class A:
    singup = "singup"


class SingupReq(AnsonBody):

    def __init__(self, parent: AnsonMsg = None):
        super().__init__(parent)
