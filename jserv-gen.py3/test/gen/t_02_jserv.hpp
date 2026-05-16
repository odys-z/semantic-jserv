#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>



namespace anson {

class AnSessionReq : public anson::AnsonBody {
public:
    inline static const std::string _type_ = "io.odysz.semantic.jsession.AnSessionReq";

    struct A {
        inline static const string login = "login";
        inline static const string logout = "logout";
        inline static const string pswd = "pswd";
        inline static const string init = "init";
        inline static const string touch = "touch";
        inline static const string ping = "ping";
    };
    string uid;
    string token;
    string iv;
    string deviceId;

    AnSessionReq() : AnsonBody(_type_)  {};
};

inline static void load_ansessionreqAst(AstMap &asts, const string &ast_path) {
    specialize_msg_astpth<AnSessionReq, AnsonBody>(asts, ast_path,
      [](meta_factory<AnSessionReq> &entf, AnsonBodyAst *ast) {
        entf.data<&AnSessionReq::uid>("uid");
        entf.data<&AnSessionReq::token>("token");
        entf.data<&AnSessionReq::iv>("iv");
        entf.data<&AnSessionReq::deviceId>("deviceId");
        entf.ctor<>();

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const AnSessionReq&>(ans);
                if ("uid" == fieldname)
                    return entt::forward_as_meta(concrete.uid);
                if ("token" == fieldname)
                    return entt::forward_as_meta(concrete.token);
                if ("iv" == fieldname)
                    return entt::forward_as_meta(concrete.iv);
                if ("deviceId" == fieldname)
                    return entt::forward_as_meta(concrete.deviceId);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<AnSessionReq>(): Failed to get entt instance (meta_any)");
            return { };
        };
  });
}

class AnSessionResp : public anson::AnsonResp {
public:
    inline static const std::string _type_ = "io.odysz.semantic.jsession.AnSessionResp";

    struct A {
    };
    SessionInf ssInf;
    Anson profile;

    AnSessionResp(string ssid, string uid, string roleId) : AnsonResp(_type_)  {
        ssInf.ssid = ssid;
        ssInf.uid = uid;
        ssInf.roleId = roleId;
    };

    AnSessionResp(SessionInf ss_inf) : AnsonResp(_type_), ssInf(ss_inf) {};

    AnSessionResp() : AnsonResp("", _type_)  {};
};

inline static void load_ansessionrespAst(AstMap &asts, const string &ast_path) {
    specialize_msg_astpth<AnSessionResp, AnsonResp>(asts, ast_path,
      [](meta_factory<AnSessionResp> &entf, AnsonBodyAst *ast) {
        entf.data<&AnSessionResp::ssInf>("ssInf");
        entf.data<&AnSessionResp::profile>("profile");
        entf.ctor<string, string, string>();
        entf.ctor<SessionInf>();
        entf.ctor<>();

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const AnSessionResp&>(ans);
                if ("ssInf" == fieldname)
                    return entt::forward_as_meta(concrete.ssInf);
                if ("profile" == fieldname)
                    return entt::forward_as_meta(concrete.profile);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<AnSessionResp>(): Failed to get entt instance (meta_any)");
            return { };
        };
  });
}

}
