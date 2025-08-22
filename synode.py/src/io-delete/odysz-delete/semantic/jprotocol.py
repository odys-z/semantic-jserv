from dataclasses import dataclass
from enum import Enum
from typing_extensions import Self

from anson.io.odysz.anson import JsonOpt, Anson


class MsgCode(Enum):
    """
    public enum MsgCode {ok, exSession, exSemantic, exIo, exTransct, exDA, exGeneral, ext };
    """
    ok = 'ok'
    exSession = 'exSession'
    exSemantics = 'exSemantics'
    exIo = 'exIo'
    exTransc = 'exTransac'
    exDA = 'exDA'
    exGeneral = 'exGeneral'
    ext = 'ext'


class Port(Enum):
    echo = 'echo.less'
    singup = 'signup.less'
    session = 'login.serv'
    r = 'r.serv'


@dataclass
class AnsonHeader(Anson):
    uid: str
    ssid: str
    iv64: str
    usrAct: [str]
    ssToken: str

    def __init__(self, ssid = None, uid = None, token = None):
        super().__init__()
        self.ssid = ssid
        self.uid = uid
        self.ssToken = token

# TAnsonBody = TypeVar('TAnsonBody', bound='AnsonBody')
# class AnTypeRef(object):
#     bref: str
#     def __init__(self, bound: str):
#         self.bref = bound

@dataclass
class AnsonMsg(Anson):
    # body: [TAnsonBody]
    body: ['AnsonBody']
    header: AnsonHeader
    port: Port
    code: MsgCode
    opts: JsonOpt
    addr: str
    seq: int
    version: str

    def __init__(self, p: Port = None):
        super().__init__()
        self.port = p
        self.body = []

    def Header(self, h: AnsonHeader) -> Self:
        self.header = h
        return self

    # def Body(self, bodyItem: TAnsonBody) -> Self:
    def Body(self, bodyItem: 'AnsonBody') -> Self:
        self.body.append(bodyItem)
        return self


@dataclass
class AnsonBody(Anson):
    uri: str
    a: str
    rs: dict
    m: str
    map: dict
    opts: JsonOpt
    addr: str
    version: str
    seq: int


    def __init__(self, parent: AnsonMsg = None):
        super().__init__()
        self.uri = None
        self.parent = parent
        Anson.enclosinguardtypes.add(AnsonMsg)

    def A(self, a: str) -> Self:
        self.a = a
        return self

@dataclass
class AnsonReq(AnsonBody):
    def __init__(self):
        super().__init__()
        self.a = None


@dataclass
class AnsonResp(AnsonBody):
    code: MsgCode
    parent: str

    def __init__(self):
        super().__init__()
        self.a = None
        self.code = MsgCode.ok

    def msg(self) -> str:
        return self.m

