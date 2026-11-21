#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>

#include <io/odysz/dbmeta.h>

namespace anson {

class DocsReq : public anson::UserReq {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.DocsReq";

    struct A {
        inline static const string syncdocs = "r/syncs";
        inline static const string orgNodes = "r/synodes";
        inline static const string mydocs = "r/my-docs";
        inline static const string rec = "r/rec";
        inline static const string download = "r/download";
        inline static const string download206 = "r/doc206";
        inline static const string upload = "c";
        inline static const string del = "d";
        inline static const string blockStart = "c/b/start";
        inline static const string blockUp = "c/b/block";
        inline static const string blockEnd = "c/b/end";
        inline static const string blockAbort = "c/b/abort";
        inline static const string selectSyncs = "r/syncflags";
        inline static const string devices = "r/devices";
        inline static const string registDev = "c/device";
        inline static const string checkDev = "r/check-dev";
        inline static const string requestSyn = "u/syn";
    };
    string synuri;
    string docTabl;
    ExpSyncDoc doc;
    PageInf  pageInf;
    vector<string> deletings;
    string stamp;
    PathsPage syncingPage;
    Device device;
    vector<ExpSyncDoc> syncQueries;
    int blockSeq;
    string org;
    bool reset;
    int limit;

    DocsReq() : UserReq() {
        Type(_type_);
    }

    void format(const IFileDescriptor& p);

    DocsReq(const string & doctbl, const IFileDescriptor& p) : UserReq() {
        Type(_type_);
        format(p);
    }

    void format(const IFileDescriptor & p, const string uri);

    DocsReq(AnsonMsg<AnsonBody> parent, const string uri, const IFileDescriptor & p) : UserReq(uri), synuri(uri) {
        Type(_type_);
        format(p, uri);
    }

    DocsReq(const string & docTabl, const ExpSyncDoc & doc, const string & uri) : UserReq(uri), docTabl(docTabl), doc(doc), synuri(uri) {
        Type(_type_);
    }
};

inline static void load_docsreqAst(JsonOpt* ctx, const string &ast_path) {
    specialize_msg_astpth<DocsReq, UserReq>(ctx, ast_path,
      [ctx](meta_factory<DocsReq> &entf, AnsonBodyAst *ast) {
        entf.data<&DocsReq::synuri>("synuri");
        entf.data<&DocsReq::docTabl>("docTabl");
        entf.data<&DocsReq::doc>("doc");
        entf.data<&DocsReq::pageInf>("pageInf");
        entf.data<&DocsReq::deletings>("deletings");
        entf.data<&DocsReq::stamp>("stamp");
        entf.data<&DocsReq::syncingPage>("syncingPage");
        entf.data<&DocsReq::device>("device");
        entf.data<&DocsReq::syncQueries>("syncQueries");
        entf.data<&DocsReq::blockSeq>("blockSeq");
        entf.data<&DocsReq::org>("org");
        entf.data<&DocsReq::reset>("reset");
        entf.data<&DocsReq::limit>("limit");

        //
        ast->get_field_instance = [ast, ctx](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const DocsReq&>(ans);
                if ("synuri" == fieldname)
                    return entt::forward_as_meta(concrete.synuri);
                if ("docTabl" == fieldname)
                    return entt::forward_as_meta(concrete.docTabl);
                if ("doc" == fieldname)
                    return entt::forward_as_meta(concrete.doc);
                if ("pageInf" == fieldname)
                    return entt::forward_as_meta(concrete.pageInf);
                if ("deletings" == fieldname)
                    return entt::forward_as_meta(concrete.deletings);
                if ("stamp" == fieldname)
                    return entt::forward_as_meta(concrete.stamp);
                if ("syncingPage" == fieldname)
                    return entt::forward_as_meta(concrete.syncingPage);
                if ("device" == fieldname)
                    return entt::forward_as_meta(concrete.device);
                if ("syncQueries" == fieldname)
                    return entt::forward_as_meta(concrete.syncQueries);
                if ("blockSeq" == fieldname)
                    return entt::forward_as_meta(concrete.blockSeq);
                if ("org" == fieldname)
                    return entt::forward_as_meta(concrete.org);
                if ("reset" == fieldname)
                    return entt::forward_as_meta(concrete.reset);
                if ("limit" == fieldname)
                    return entt::forward_as_meta(concrete.limit);
            }

            if (ctx->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = ctx->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<DocsReq>(): Failed to get entt instance (meta_any)");
            return { };
        };
    });
}

}
