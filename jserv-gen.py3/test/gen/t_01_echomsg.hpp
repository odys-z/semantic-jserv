#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>



namespace anson {

class EchoReq2 : public anson::AnsonBody {
public:
    inline static const std::string _type_ = "io.odysz.semantic.jserv.echo.EchoReq2";

    struct A {
        inline static const string echo = "echo";
        inline static const string inet = "inet";
    };
    string echo;

    EchoReq2(string m) : AnsonBody("r/peertest", _type_), echo(m) {};

    EchoReq2() : AnsonBody("r/peertest", _type_)  {};
};

inline static void load_echoreq2Ast(AstMap &asts, const string &ast_path) {
    specialize_msg_astpth<EchoReq2, AnsonBody>(asts, ast_path,
      [](meta_factory<EchoReq2> &entf, AnsonBodyAst *ast) {
        entf.data<&EchoReq2::echo>("echo");
        entf.ctor<string>();
        entf.ctor<>();

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const EchoReq2&>(ans);
                if ("echo" == fieldname)
                    return entt::forward_as_meta(concrete.echo);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<EchoReq2>(): Failed to get entt instance (meta_any)");
            return { };
        };
  });
}

}
