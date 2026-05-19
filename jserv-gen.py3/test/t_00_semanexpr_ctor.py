from typing import cast
from unittest import TestCase

from anson.io.odysz.anson import Anson
from semanticshare.io.odysz.reflect import AnCtor, AnsonBodyAst

from src.semantier_gen.io.oz.semanticpeer.generator2 import class_ctors


expt0 = '''
    EchoReq2(string m) : AnsonBody("r/peertest"), echo(m) {
        Type(_type_);
        echo = m;
    }
'''
ctor0 = cast(AnCtor, Anson.from_json(r'''
{
  "type": "io.odysz.reflect.AnCtor",
  "base": {"stype": "()", "args": ["AnsonBody", "\"r/peertest\""]},
  "args": [{"stype": "ini", "args": ["string", "m", "echo"]}],
  "body": [{"stype": "=", "args": ["echo", "m"]}]
}'''))

expt1 = '''
    EchoReq2(string m) : EchoReq2("r/peertest"), echo(m) {
    }
'''
ctor1 = cast(AnCtor, Anson.from_json(r'''
{
  "type": "io.odysz.reflect.AnCtor",
  "base": {"stype": "()", "args": ["EchoReq2", "\"r/peertest\""]},
  "args": [{"stype": "ini",
          "args": ["string", "m", "echo"]}],
  "body": []
}'''))

expt2 = '''
    EchoReq2() : EchoReq2("r/peertest") {
    }
'''
ctor2 = cast(AnCtor, Anson.from_json(r'''
{
  "type": "io.odysz.reflect.AnCtor",
  "base": {"stype": "()", "args": ["EchoReq2", "\"r/peertest\""]},
  "args": [],
  "body": []
}'''))

expt3 = [r'''
    void format(IFileDescriptor p);
''',
    r'''
    DocsReq(AnsonMsg<AnsonBody> parent, UserReq uri, IFileDescriptor p) : UserReq(uri) {
        Type(_type_);
        format(p);
    }
''']

ctor3 = cast(AnCtor, Anson.from_json(r'''{
  "type": "io.odysz.reflect.AnCtor",
  "base": {"stype": "()", "args": ["UserReq", "uri"]},
  "args": [{"stype": "", "args": ["AnsonMsg<AnsonBody>", "parent"],
            "expect_result":  "arg declared and ignored"},
           {"stype": "ini", "args": ["UserReq", "uri"]},
           {"stype": "", "args": ["IFileDescriptor", "p"]} ],
  "body": [{"stype": "()", "args": ["format", "p"]}]
}'''))

expt4 = '''
    DocsReq(string docTabl, ExpSyncDoc doc, string uri) : UserReq(uri), docTabl(docTabl), doc(doc) {
        Type(_type_);
    }
'''

ctor4 = cast(AnCtor, Anson.from_json(r'''{
  "type": "io.odysz.reflect.AnCtor",
  "base": {"stype": "()", "args": ["UserReq", "uri"]},
  "args": [{"stype": "ini", "args": ["string", "docTabl", "docTabl"]},
          {"stype": "ini", "args": ["ExpSyncDoc", "doc", "doc"]},
          {"stype": "ini", "args": ["string", "uri"]}]
  }'''))

class GenCtorsTest(TestCase):
    '''
      { "base": {"stype": "()", "args": ["AnsonBody", "\"r/peertest\""]},
        "args": [{"stype": "ini",
                "args": ["string", "m", "echo", "c++ code: (..., string m, ...) : echo(m)"]}],
        "body": [{"stype": "=", "args": ["echo", "m"], "expect_result":  "{ echo = m; ...}"}] }
    '''

    def test_(self):

        ast = AnsonBodyAst()
        ast.dataAnclass = 'io.odysz.semantic.jserv.echo.EchoReq2'
        ast.baseAnclass = 'io.odysz.semantic.jprotocol.AnsonBody'

        ast.ctorsemantics = [ctor0, ctor1, ctor2]
        ctor_lines = class_ctors(ast)

        with(open('test/expect/t00-test_test.txt', 'w+') as ef):
            for c in [expt0, expt1, expt2]:
                ef.writelines(c)

        with(open('test/gen/t00-test_test.txt', 'w+') as f):
            for l in ctor_lines:
                f.writelines(l)

        self.assertEqual([expt0, expt1, expt2], ctor_lines)

    def test_docsreq(self):
        ast = AnsonBodyAst()
        ast.dataAnclass = 'io.odysz.semantic.tier.docs.DocsReq'
        ast.baseAnclass = 'io.odysz.semantic.jprotocol.UserReq'

        ast.ctorsemantics = [ctor3, ctor4]
        ctor_lines = class_ctors(ast)

        with(open('test/expect/t00-docsreq-test.txt', 'w+') as ef):
            for c in [expt3, expt4]:
                ef.writelines(c)

        with(open('test/gen/t00-docsreq-test.txt', 'w+') as f):
            for l in ctor_lines:
                f.writelines(l)

        self.assertEqual([*expt3, expt4], ctor_lines)
