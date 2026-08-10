import re
from dataclasses import dataclass
from pathlib import Path
from typing import cast, List

from anson.io.odysz.anson import Anson
from anson.io.odysz.common import Utils, LangExt, Primtypes
from semanticshare.io.odysz.reflect import AnsonBodyAst, PeerSettings, AnsonAst, init_asts, SemanExpr, AnCtor, \
    AnsonJavaEnumAst


def entf_ctors(ast: AnsonAst) -> List[str]:
    '''
    :param ctorstrs: e.g.
        [[["echo", "string", "m"], ["r/query"]],
        [[], ["r/query"]]]
    :return: entf.ctor<>(); entf.ctor<string>();
    '''
    ctorss = []
    for ctor in ast.ctors:
        lst = []
        for c in ctor[1:]:
            if len(c) > 1:
                lst.append(c[0])
        ctorss.append(f'        entf.ctor<{", ".join(lst)}>();\n')
    return ctorss

def ent_ctors(ast: AnsonAst) -> List[str]:
    '''
    :return: .ctor<>().ctor<string>()
    '''
    ctorss = []
    for ctor in ast.ctorsemantics:
        lst = []
        for ar in ctor.args:
            if ar.stype == 'ini':
                lst.append(' '.join(ar.args[:-2]))
            elif ar.stype == '':
                lst.append(' '.join(ar.args[:-1]))
        ctorss.append(f'        .ctor<{", ".join(lst)}>()\n')
    return ctorss

def c_type(dataAnclass: str) -> str:
    def replace_cpp_type(typstr: str) -> str:
        tss = re.split(r',\s*', typstr)
        if len(tss) > 0:
            for ix in range(len(tss)):
                t = tss[ix]
                tss[ix] = Primtypes.C20[t] if t in Primtypes.C20 else t
        return ', '.join(tss)

    typss = dataAnclass.split('<')
    end = ''
    for x in range(len(typss)):
        t = typss[x].split('.')[-1]
        # t = re.sub(r",\s*list", ", vector", t)
        t = replace_cpp_type(t)

        typss[x] = Primtypes.C20[t] if t in Primtypes.C20 else t
        end = end + '>'
    return '<'.join(typss) + (end[:-1] if len(end) > 0 else '')

def class_fields(asts: dict[str, AnsonAst], ast: AnsonAst) -> List[str]:
    fields = []

    for fn, fd in ast.fields.items():
        data_anclass = fd['dataAnclass']
        fields.append(f'    {c_type(data_anclass)} {fn};\n')

    return fields

def class_ctors(ast: AnsonAst) -> List[str]:
    """
    C++ 20 Constructors Generator
    =============================

    For c++, a reflect bridge for serialization is necessary. So the constructor will enforcing adding
    a pointer arg to JsonOpt, the context named in Anson.cmake 0.1, for deserialization.
    :param ast:
    :return:

    """

    ctors = []
    body_formatters = []
    found_0arg_ctor = False
    ctorsemantics = ast.ctorsemantics if LangExt.len(ast.ctorsemantics) > 0 else [AnCtor().as_default(ast)]
    for ctorss in ctorsemantics:
        if len(ctorss.args) == 0: found_0arg_ctor = True ## also will set false if JavaEnum and AnsomMsg

        body_lines = ctorss.cpp_body_exprs(ast, ' ' * 8)
        body_lines = [('\n'.join(body_lines) + '\n') if body_lines and len(body_lines) > 0 else '']

        # {'p': 'IFileProvider'} => format(p);
        map_arg_nt = ctorss.map_args_decls()

        if isinstance(ctorss.body, SemanExpr):
            bodys = [ctorss.body] # tolerate
        else:
            bodys = ctorss.body

        for body_expr in bodys:
            if body_expr.stype != '()': continue

            if LangExt.len(body_expr.args) <= 1:
                Utils.warn('Cannot understand body func configuration: {}', body_expr.args)
                continue

            arg_lst = map(lambda argname: f"{map_arg_nt[argname]} {argname}", body_expr.args[1:])
            voidfunc = f'\n    void {body_expr.args[0]}({", ".join(arg_lst)});\n'
            if voidfunc not in body_formatters:
                ctors.append(voidfunc)

        initlst = filter(lambda x: not LangExt.isblank(x), [ctorss.cpp_base_ini(ast), ctorss.cpp_arg_inis()])

        ctors.append(' : '.join(filter(lambda x: not LangExt.isblank(x), [f'\n    {ast.c_class()}({ctorss.cpp_arg_decl(ast)})', ', '.join(initlst)])) +
                     ' {\n' +
                     '\n'.join(body_lines) +
                     ('    }\n' if len(body_lines) > 0 else '}\n'))

    if not found_0arg_ctor:
        Utils.warn("No default ctor is not found. Force a compile error here: {} {}()", ast.dataAnclass, ast.c_class())
        ctors.append(f"\n    // No default ctor is not found. Force a compile error here: {ast.dataAnclass} {ast.c_class()} ()")
        ctors.append(f'\n    {ast.c_class()}() : {ast.c_base()}() {{ Type(_type_); }}\n')

    return ctors

start_header = '''#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>

'''

start_namespace = '''
namespace anson {
'''
'''
    [0] pragma once ...
'''

class_decl = '''
class {} : public anson::{} {{
public:
    inline static const std::string _type_ = "{}";
'''
'''
    E.g.
    class {Req} : public anson::{AnsonBody} {
    
    public:
        inline static const std::string _type_ = "{io.ody.syn.x}";
'''

field_getter0 = '''
        //
        ast->get_field_instance = [ast, ctx](const IJsonable& ans, const string& fieldname) -> meta_any {{
            if (ast->fields.contains(fieldname)) {{
                auto& concrete = static_cast<const {0}&>(ans);'''
field_getif ='''
                if ("{0}" == fieldname)
                    return entt::forward_as_meta(concrete.{0});'''
field_getter9 = '''
            }}

            if (ctx->has_ast(ast->baseAnclass)) {{
                {ast_type} *bast = ctx->ast<{ast_type}>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }}

            anerror("get_field_instance<{0}>(): Failed to get entt instance (meta_any)");
            return {{ }};
        }};
'''

caller_func = '''
inline static void register_{tier_name}(JsonOpt* ctx, const string &ast_folder) {{
'''
class MsgLines:

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

    inline_static = 'inline static '

    # 0: echoreq, 1: AnSessionResp, 2: AnsonResp
    load_ast = '''void {0}(JsonOpt* ctx, const string &ast_path) {{
    specialize_msg_astpth<{1}, {2}>(ctx, ast_path,
      [ctx](meta_factory<{1}> &entf, AnsonBodyAst *ast) {{'''

    entt_ctor = '''
    entf.ctor<&{0}{1}>();'''

    entt_data = '''
        entf.data<&{0}::{1}>("{1}");'''

    end_ns = '\n}\n'


    def specialize_req(self, asts: dict[str, AnsonAst], ast: AnsonBodyAst, caller: List[tuple], astpath: str) -> List[str]:
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

        fn = f'load_{ast.c_class().lower()}Ast'
        caller.append(('load-msg', fn, astpath))
        load_func = self.inline_static + self.load_ast.format(fn, ast.c_class(), ast.c_base())

        return [class_decl.format(ast.c_class(), ast.c_base(), ast.dataAnclass),
                self.struct_A,
                *[f'\n        inline static const string {k} = "{v}";' for k, v in ast.A.items()],
                '\n    };\n',
                *class_fields(asts, ast),
                *class_ctors(ast),
                '};\n',

                '\n' + load_func,
                *[self.entt_data.format(ast.c_class(), fn) for fn, _ in ast.fields.items()],
                '\n',
                *entf_ctors(ast),
                field_getter0.format(ast.c_class()),
                *[field_getif.format(fn) for fn, _ in ast.fields.items()],
                field_getter9.format(ast.c_class(), ast_type='AnsonBodyAst'),
                '    });\n}\n'
                ]

@dataclass
class AnsonLines:
    inline_static: str = 'inline static '

    regist_anson: str = '''void {}(JsonOpt* ctx) {{

    AnsonAst * ast = createAST <{}, AnsonAst> (
        *ctx->asts, {}::_type_, map <string, AnsonField> {{
'''
    anson_field: str = '        {{"{}", {{.dataAnclass="{}"}} }},\n'
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
        .data<&anson::{0}::{1}>("{1}")'''

    def cppcode(self, asts: dict[str, AnsonAst], ast: AnsonAst, caller: List[tuple]) -> List[str]:
        load_func = f'register_{ast.c_class().lower()}Ast'
        caller.append(('register_', load_func))

        return [class_decl.format(ast.c_class(), ast.c_base(), ast.dataAnclass),
                *class_fields(asts, ast),
                *class_ctors(ast),
                '};\n\n',

                self.inline_static + self.regist_anson.format(load_func, ast.c_class(), ast.c_base()),
                *[self.anson_field.format(fn, f['dataAnclass']) for fn, f in ast.fields.items()],
                '       });\n',
                self.entt_facotry.format(ast.c_class(), ast.c_base()),
                *ent_ctors(ast),

                *[self.entt_data.format(ast.c_class(), fn) for fn, _ in ast.fields.items()],
                '\n        ;\n',

                field_getter0.format(ast.c_class()),
                *[field_getif.format(fn) for fn, _ in ast.fields.items()],
                field_getter9.format(ast.c_class(), ast_type='AnsonAst'),
                '}\n'
                ]

@dataclass
class IPortLines:
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
        .data<&anson::{0}::{1}>("{1}")'''

    def cppcode(self, asts: dict[str, AnsonAst], ast: AnsonJavaEnumAst, caller: List[tuple], astpath: str) -> List[str]:
        # register_iport <WSPort> (asts, "ast/wsport.ast.json");
        caller.append(('load-port', f'register_iport<{ast.c_class()}>', astpath))

        return [class_decl.format(ast.c_class(), ast.c_base(), ast.dataAnclass),
                *[f'\n    inline static const string {k} = "{v}";' for k, v in ast.encode.items()],
                '\n',
                *class_ctors(ast),
                '};\n\n',
                ]


def gen_cpp_peer2(settings: PeerSettings) -> Path:
    '''
    :param settings:
    :param ast_folder:
    :return:
    '''

    msglines = MsgLines()
    ansonlines = AnsonLines()
    enumlines = IPortLines()

    gen_pth = Path(settings.cpp_gen)
    gen_pth.parent.mkdir(parents=True, exist_ok=True)

    asts = init_asts()

    with open(gen_pth, 'w') as gen:
        gen.writelines(start_header)
        for h in settings.cpp_include:
            gen.writelines(f'#include <{h}>')
        gen.writelines('\n')
        gen.writelines(start_namespace)

        settings.ansons.extend(settings.javaEnums)
        settings.ansons.extend(settings.anRequests)
        caller_body: List[tuple] = []

        for astjson in settings.ansons:
            if (Path(settings.ast_folder) / astjson).exists():
                ast: AnsonAst = cast(AnsonAst, Anson.from_file(str(Path(settings.ast_folder) / astjson)))
                asts[ast.dataAnclass] = ast

                if isinstance(ast, AnsonBodyAst):
                    bdast = cast(AnsonBodyAst, ast)
                    gen.writelines(msglines.specialize_req(asts, bdast, caller_body, astjson))
                elif isinstance(ast, AnsonJavaEnumAst) :
                    enumast = cast(AnsonJavaEnumAst, ast)
                    gen.writelines(enumlines.cppcode(asts, enumast, caller_body, astjson))
                else:
                    gen.writelines(ansonlines.cppcode(asts, ast, caller_body))

            else:
                Utils.warn('Cannot find file ' + astjson)

        if len(caller_body) > 1:
            gen.writelines(caller_func.format(tier_name = settings.tier_name))
            gen.writelines('    filesystem::path folder_path{ast_folder};\n')
            for ln in caller_body:
                if 'register_' == ln[0]:
                    gen.writelines(f'    {ln[1]}(ctx);\n')
                else:
                    gen.writelines(f'    {ln[1]}(ctx, (folder_path / "{ln[2]}").string());\n')
            else:
                gen.writelines('}\n')

        gen.writelines(msglines.end_ns)

    return gen_pth


def gen_peers(settings: PeerSettings, ast_folder: str = None) -> Path:
    if Path is not None:
        settings.ast_folder = ast_folder

    # gen_ts_peer(settings)
    # gen_py_peer(settings)
    # gen_cpp_peer(settings, config_path)
    return gen_cpp_peer2(settings)
