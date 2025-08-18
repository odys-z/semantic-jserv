import json
from dataclasses import dataclass
from pathlib import Path
from typing import cast

from anson.io.odysz.anson import Anson

from src.io.odysz.semantic.syn import Synode, SyncUser


@dataclass
class SynOrg(Anson):
    meta: str

    orgId: str
    orgName: str
    # edu | org | com | ...
    orgType: str
    ''' This is a tree table. '''
    parent: str
    fullpath: str
    market: str
    # web server url, configured in dictionary like: $WEB-ROOT:8888
    webroot: str
    # The home page url (landing page)
    homepage: str
    # The default resources collection, usually a group / tree of documents.
    album0: str

    def __init__(self):
        super().__init__()
        self.parent = None
        self.fullpath = ""
        self.orgType = ""
        self.webroot = ""
        self.homepage = ""
        self.album0 = ""


@dataclass
class SynodeConfig(Anson):
    synid: str
    domain: str
    mode: str | None

    admin: str

    sysconn: str
    synconn: str

    org: SynOrg
    ''' Market, organization or so? '''

    syncIns: float
    '''
     * Synchronization interval, initially, in seconds.
     * No worker thread started if less or equals 0.
    '''

    peers: list[Synode]

    https: bool

    def __init__(self):
        super().__init__()
        self.https = False


@dataclass()
class AnRegistry(Anson):
    config: SynodeConfig
    synusers: list[SyncUser]

    def __init__(self):
        super().__init__()
        self.config = cast('SynodeConfig', None)
        self.synusers = []

    @staticmethod
    def load(path) -> 'AnRegistry':
        if Path(path).is_file():
            with open(path, 'r') as file:
                obj = json.load(file)
                obj['__type__'] = AnRegistry().__type__
                return Anson.from_envelope(obj)
        else:
            raise FileNotFoundError(f"File doesn't exist: {path}")

    @classmethod
    def find_synode(cls, synodes: list[Synode], id):
        if synodes is not None:
            for peer in synodes:
                if peer.synid == id:
                    return peer
        return None

    @classmethod
    def find_synuser(cls, users: list[SyncUser], id):
        if users is not None:
            for u in users:
                if u.userId == id:
                    return u
        return None


def loadYellowPages():
    path = ""
    registry = AnRegistry().load(path)
    return registry
