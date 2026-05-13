from dataclasses import dataclass
from pathlib import Path
from typing import cast, List

from anson.io.odysz.anson import Anson
from anson.io.odysz.common import Utils, LangExt, primtypes
from semanticshare.io.odysz.reflect import AnsonBodyAst, PeerSettings, AnsonAst, init_asts


def entt_ctors(ast: AnsonAst) -> List[str]:
    '''
    :param ctorstrs: e.g.
        [[["echo", "string", "m"], ["r/query"]],
        [[], ["r/query"]]]
    :return: .ctor<>().ctor<string>()
    '''
    # return [f'        entf.ctor<{c}>();\n' for ctor in ast.ctors for c in ctor[0]]
    ctorss = []
    for ctor in ast.ctors:
        lst = []
        for c in ctor[1:]:
            if len(c) > 1:
                # string echo m
                # string ssInf.ssid = sid
                lst.append(c[0])
        ctorss.append(f'        entf.ctor<{", ".join(lst)}>();\n')

    return ctorss


@dataclass
class MsgLines:
    start_header = '''#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>

namespace anson {'''
    '''
    [0] pragma once ...
    '''

    class_decl = '''
class {} : public anson::{} {{
public:
    inline static const std::string _type_ = "{}";'''
    '''
    [1] public class {Req} : public anson::{AnsonBody} { _type_={} ...
    '''

    struct_A = '''
    struct A {'''
    '''
    [2] stuct A {
    '''
    # A.a ...

    act_enum = '''
        inline static const string {} = "{}";'''
    '''
    [3] inline static const string...
    '''

    def class_fields(self, asts: dict[str, AnsonAst], ast: AnsonAst):
        fields = []

        for fn, fd in ast.fields.items():
            ftype = fd['dataAnclass']
            if ftype in primtypes['C20']:
                fields.append(f'    {primtypes["C20"][ftype]} {fn};\n')
            elif ftype in asts:
                fields.append(f'    {asts[ftype].c_class()} {fn};\n')
            else:
                fields.append(f'    {ftype.split(".")[-1]} {fn};\n')

        return fields

    def class_ctors(self, ast: AnsonBodyAst) -> List[str]:
        '''
        :param ctorstrs: e.g.
            [[["echo", "string", "m"], ["r/query"]],
            [[], ["r/query"]]]
        :return:
            EchoReq() : AnsonBody(_type_);
            EchoReq(string m) : AnsonBody(m, _type_);
        '''
        ctors = []
        for ctorss in ast.ctors:
            # e.g. [['r/peer-test'], ['string', 'echo', 'm']]
            parlist, fieldini = [], []

            # e.g. semntics = {"ssInf.ssid=ssid", "ssInf.uid=uid", "ssInf.roleId=roleId"}
            #      [[[], ["string", "ssInf.ssid", "=", "ssid"], ["string", "ssInf.uid", "=", "uid"], ["string", "ssInf.roleId", "=", "roleId"]], ...
            ctor_body = []

            if LangExt.len(ctorss[0]) == 0:
                base_ini_list = '_type_'
            else:
                base_ini_list = ', '.join([*ctorss[0], '_type_'])

            for parass in ctorss[1:]:
                if LangExt.len(parass) == 0:
                    continue
                # if LangExt.len(parass) != 3 and LangExt.len(parass) != 2:
                    # Utils.warn("Error: ", parass)
                    # continue
                if LangExt.len(parass) == 3:
                    parlist.append(parass[0] + " " + parass[2])
                    fieldini.append(f'{parass[1]}({parass[2]})')
                elif LangExt.len(parass) == 4 and parass[2] == '=':
                    parlist.append(parass[0] + " " + parass[3])
                    # fieldini.append(f'{parass[1]}({parass[2]})')
                    ctor_body.append(f'\n        {parass[1]} = {parass[3]};')

                else: Utils.warn("Error: Cannot parse ctor's initializer list: " + "\,".join(parass))

            base_ini = f'{ast.c_base()}({base_ini_list}){", " if len(fieldini) > 0 else " "}'
            ctor_body = ''.join(ctor_body) + ('' if len(ctor_body) == 0 else '\n    ')
            ctors.append(f'\n    {ast.c_class()}({", ".join(parlist)}) : {base_ini}{", ".join(fieldini)} {{{ctor_body}}};\n')
        return ctors

    inline_static = True

    # 0: echoreq, 1: AnSessionResp, 2: AnsonResp
    load_ast = '''void load_{0}Ast(AstMap &asts, const string &ast_path) {{
    specialize_msg_astpth<{1}, {2}>(asts, ast_path,
      [](meta_factory<{1}> &entf, AnsonBodyAst *ast) {{'''

    entt_ctor = '''
    entf.ctor<&{0}{1}>();'''

    entt_data = '''
        entf.data<&{0}::{1}>("{1}");'''

    field_getter0 = '''
        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {{
            if (ast->fields.contains(fieldname)) {{
                auto& concrete = static_cast<const {0}&>(ans);'''
    field_getif ='''
                if ("{0}" == fieldname)
                    return entt::forward_as_meta(concrete.{0});'''
    field_getter9 = '''
            }}

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {{
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }}

            anerror("get_field_instance<{0}>(): Failed to get entt instance (meta_any)");
            return {{ }};
        }};
  }});
}}'''

    end_ns = '\n}'

    def specialize_req(self, asts: dict[str, AnsonAst], ast: AnsonBodyAst) -> List[str]:
        '''
        Example
        =======
        class EchoReq: public AnsonBody {
        public:
            inline static const std::string _type_ = "io.odysz.semantic.jserv.echo.EchoReq";
            struct A {
                inline static const string echo = "echo";
                inline static const string inet = "inet";
            };

            string echo;
            EchoReq() : AnsonBody("r/query", EchoReq::_type_) {}
            EchoReq(string echo) : AnsonBody("r/query", EchoReq::_type_), echo(echo) {}
        };

        inline static void load_echoAst_expect(AstMap &asts, const string &ast_path) {
            specialize_msg_astpth<EchoReq>(asts, ast_path,
              [](meta_factory<EchoReq> &entf, AnsonBodyAst *ast) {

                entf.data<&EchoReq::echo>("echo");

                ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
                    if (ast->fields.contains(fieldname)) {
                        auto& concrete = static_cast<const EchoReq&>(ans);
                        if ("echo" == fieldname)
                            return entt::forward_as_meta(concrete.echo);
                    }

                    if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                        AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                        return bast->get_field_instance(ans, fieldname);
                    }

                    anerror("get_field_instance<EchoReq>(): Failed to get entt instance (meta_any)");
                    return {};
                };
            });
        }
        :param ast:
        :return: formatted source header lines
        '''
        return [self.class_decl.format(ast.c_class(), ast.c_base(), ast.dataAnclass),
                self.struct_A,
                *[f'\n        inline static const string {k} = "{v}";' for k, v in ast.A.items()],
                '\n\t};\n',
                *self.class_fields(asts, ast),
                *self.class_ctors(ast),
                '};\n',

                # load_ast()
                '\n' + ('inline static ' if self.inline_static else '') + self.load_ast.format(
                    ast.c_class().lower(), ast.c_class(), ast.c_base()),
                *[self.entt_data.format(ast.c_class(), fn) for fn, _ in ast.fields.items()],
                '\n',
                *entt_ctors(ast),
                self.field_getter0.format(ast.c_class()),
                *[self.field_getif.format(fn) for fn, _ in ast.fields.items()],
                self.field_getter9.format(ast.c_class()),
                ]

@dataclass
class AnsonLines:
    regist_anson: str = '''inline static void register_{}Ast(AstMap & asts) {{
    //
    AnsonAst * ast = createAST <{}, AnsonAst> (
        asts, {}, map <string, AnsonField> {{'''
    anson_field: str = '        {{"{}", {{.dataAnclass="{}"}} }}\n'
    '''
    {"scopeEnums", {.dataAnclass = "list<string"}},
    {"cpp_gen", {.dataAnclass = "string"}}
    });
    '''

    entt_facotry = '''
    entt::meta_factory <anson::{}> ()
        .type(ast->enttypeid)
        .base<{}>()
    '''
    '''
    entt::meta_factory < anson::PeerSettings > ()
        .type(ast->enttypeid)
        .base < Anson > ()
        .ctor <> ()
        .data < & anson::PeerSettings::ansons > ("ansons")
        .data < & anson::PeerSettings::scopeEnums > ("scopeEnums")
        .data < & anson::PeerSettings::javaEnums > ("javaEnums")
        .data < & anson::PeerSettings::ansonMsg > ("ansonMsg")
        .data < & anson::PeerSettings::ansonBody > ("ansonBody")
        .data < & anson::PeerSettings::anRequests > ("anRequests")
        .data < & anson::PeerSettings::cpp_gen > ("cpp_gen") \
        ;
    }
    '''
    entt_data = '''
        .data<&anson::{0}::{1}>("{0}")'''

    def cppcode(self, ast: AnsonAst) -> List[str]:
        return [self.regist_anson.format(ast.c_class().lower(), ast.c_class(), ast.c_base()),
                *[self.anson_field.format(fn, f['dataAnclass']) for fn, f in ast.fields.items()],
                '       })\n',
                self.entt_facotry.format(ast.c_class(), ast.c_base()),
                *entt_ctors(ast),
                self.entt_facotry.format(ast.c_class(), ast.c_base()),
                *[self.entt_data.format(ast.c_class(), fn) for fn, _ in ast.fields.items()],
                '        ;\n}'
                ]


def gen_cpp_peer(settings: PeerSettings, ast_folder: Path):
    '''
    :param settings:
    :param ast_folder:
    :return:
    '''

    msglines = MsgLines()
    ansonlines = AnsonLines()

    gen_pth = Path(settings.cpp_gen)
    gen_pth.parent.mkdir(parents=True, exist_ok=True)

    asts = init_asts()

    with open(gen_pth, 'w') as gen:
        gen.writelines(msglines.start_header)

        for astjson in settings.anRequests:
            if Path(ast_folder / astjson).exists():
                ast: AnsonAst = cast(AnsonAst, Anson.from_file(str(ast_folder / astjson)))
                asts[ast.dataAnclass] = ast

                if (isinstance(ast, AnsonBodyAst)):
                    bdast = cast(AnsonBodyAst, ast)
                    gen.writelines(msglines.specialize_req(asts, bdast))
                else:
                    gen.writelines(ansonlines.cppcode(ast))
            else:
                Utils.warn('Cannot find file ' + astjson)

        gen.writelines(msglines.end_ns)


def gen_peers(settings: PeerSettings, config_path: Path) -> None:
    # gen_ts_peer(settings)
    # gen_py_peer(settings)
    gen_cpp_peer(settings, config_path)
