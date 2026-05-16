#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>

#include <gen/stubtypes.h>

namespace anson {

class PageInf : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.transact.sql.PageInf";
    long page;
    long size;
    long total;
    vector<vector<string>> arrCondts;
    map<string, list<LangExt::VarType>> mapCondts;
};

inline static void register_pageinfAst(AstMap & asts) {

    AnsonAst * ast = createAST <PageInf, AnsonAst> (
        asts, Anson::_type_, map <string, AnsonField> {
        {"page", {.dataAnclass="long"} },
        {"size", {.dataAnclass="long"} },
        {"total", {.dataAnclass="long"} },
        {"arrCondts", {.dataAnclass="list<list<string"} },
        {"mapCondts", {.dataAnclass="map<string, list<VarType"} },
       });

    entt::meta_factory <anson::PageInf> ()
        .type(ast->enttypeid)
        .base<Anson>()

        .data<&anson::PageInf::page>("PageInf")
        .data<&anson::PageInf::size>("PageInf")
        .data<&anson::PageInf::total>("PageInf")
        .data<&anson::PageInf::arrCondts>("PageInf")
        .data<&anson::PageInf::mapCondts>("PageInf")
        ;
}

class Device : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tiers.docs.Device";
    string id;
    string synode0;
    string devname;
    string toFolder;

    Device() : Anson(_type_)  {};

    Device(string id, string synode0, string devname) : Anson(_type_)  {
        id = id;
        synode0 = synode0;
        devname = devname;
    };
};

inline static void register_deviceAst(AstMap & asts) {

    AnsonAst * ast = createAST <Device, AnsonAst> (
        asts, Anson::_type_, map <string, AnsonField> {
        {"id", {.dataAnclass="string"} },
        {"synode0", {.dataAnclass="string"} },
        {"devname", {.dataAnclass="string"} },
        {"toFolder", {.dataAnclass="string"} },
       });

    entt::meta_factory <anson::Device> ()
        .type(ast->enttypeid)
        .base<Anson>()
        .ctor<>()
        .ctor<string, string, string>()

        .data<&anson::Device::id>("Device")
        .data<&anson::Device::synode0>("Device")
        .data<&anson::Device::devname>("Device")
        .data<&anson::Device::toFolder>("Device")
        ;
}

class SynEntity : public anson::AnsonBody {
public:
    inline static const std::string _type_ = "io.oz.syn.SynEntity";
    SynEntityMeta entm;
    vector<string> synpageCols;
    string recId;
    string uids;
    string synode;
    string synoder;
    long nyquence;

    SynEntity() : AnsonBody(_type_)  {};

    SynEntity(string type,  "", "") : AnsonBody(type, _type_)  {};

    SynEntity(SynEntityMeta entMeta) : AnsonBody(_type_), entm(entMeta) {};

    SynEntity(SynEntityMeta entMeta, string type) : AnsonBody(type, _type_), entm(entMeta) {};
};

inline static void register_synentityAst(AstMap & asts) {

    AnsonAst * ast = createAST <SynEntity, AnsonAst> (
        asts, AnsonBody::_type_, map <string, AnsonField> {
        {"entm", {.dataAnclass="SynEntityMeta"} },
        {"synpageCols", {.dataAnclass="list<string"} },
        {"recId", {.dataAnclass="string"} },
        {"uids", {.dataAnclass="string"} },
        {"synode", {.dataAnclass="string"} },
        {"synoder", {.dataAnclass="string"} },
        {"nyquence", {.dataAnclass="long"} },
       });

    entt::meta_factory <anson::SynEntity> ()
        .type(ast->enttypeid)
        .base<AnsonBody>()
        .ctor<>()
        .ctor<string, >()
        .ctor<SynEntityMeta>()
        .ctor<SynEntityMeta, string>()

        .data<&anson::SynEntity::entm>("SynEntity")
        .data<&anson::SynEntity::synpageCols>("SynEntity")
        .data<&anson::SynEntity::recId>("SynEntity")
        .data<&anson::SynEntity::uids>("SynEntity")
        .data<&anson::SynEntity::synode>("SynEntity")
        .data<&anson::SynEntity::synoder>("SynEntity")
        .data<&anson::SynEntity::nyquence>("SynEntity")
        ;
}

class PathsPage : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.PathsPage";
    string device;
    int start;
    int end;
    map<string, list<LangExt::VarType>> clientPaths;

    PathsPage() : Anson(_type_)  {};

    PathsPage(int begin, int afterLast) : Anson(_type_), start(begin), end(afterLast) {};

    PathsPage(string device, int begin, int afterLast) : Anson(_type_), device(device), start(begin), end(afterLast) {};
};

inline static void register_pathspageAst(AstMap & asts) {

    AnsonAst * ast = createAST <PathsPage, AnsonAst> (
        asts, Anson::_type_, map <string, AnsonField> {
        {"device", {.dataAnclass="string"} },
        {"start", {.dataAnclass="int"} },
        {"end", {.dataAnclass="int"} },
        {"clientPaths", {.dataAnclass="map<string, list<VarType"} },
       });

    entt::meta_factory <anson::PathsPage> ()
        .type(ast->enttypeid)
        .base<Anson>()
        .ctor<>()
        .ctor<int, int>()
        .ctor<string, int, int>()

        .data<&anson::PathsPage::device>("PathsPage")
        .data<&anson::PathsPage::start>("PathsPage")
        .data<&anson::PathsPage::end>("PathsPage")
        .data<&anson::PathsPage::clientPaths>("PathsPage")
        ;
}

class ExpSyncDoc : public anson::SynEntity {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.ExpSyncDoc";
    string pname;
    string clientpath;
    string device;
    string org;
    string shareflag;
    string shareMsg;
    string createDate;
    string uri64;
    string shareby;
    string sharedate;
    long size;
    string mime;
    string folder;

    ExpSyncDoc() : SynEntity(_type_)  {};

    ExpSyncDoc(SynEntityMeta m, string orgId) : SynEntity(m, _type_), org(orgId) {};

    void format(AnResultset rs);

    ExpSyncDoc(SynEntityMeta meta, AnResultset rs) : SynEntity(meta, _type_)  {
        format(rs);
    };
};

inline static void register_expsyncdocAst(AstMap & asts) {

    AnsonAst * ast = createAST <ExpSyncDoc, AnsonAst> (
        asts, SynEntity::_type_, map <string, AnsonField> {
        {"pname", {.dataAnclass="string"} },
        {"clientpath", {.dataAnclass="string"} },
        {"device", {.dataAnclass="string"} },
        {"org", {.dataAnclass="string"} },
        {"shareflag", {.dataAnclass="string"} },
        {"shareMsg", {.dataAnclass="string"} },
        {"createDate", {.dataAnclass="string"} },
        {"uri64", {.dataAnclass="string"} },
        {"shareby", {.dataAnclass="string"} },
        {"sharedate", {.dataAnclass="string"} },
        {"size", {.dataAnclass="long"} },
        {"mime", {.dataAnclass="string"} },
        {"folder", {.dataAnclass="string"} },
       });

    entt::meta_factory <anson::ExpSyncDoc> ()
        .type(ast->enttypeid)
        .base<SynEntity>()
        .ctor<>()
        .ctor<SynEntityMeta, string>()
        .ctor<SynEntityMeta, AnResultset>()

        .data<&anson::ExpSyncDoc::pname>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::clientpath>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::device>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::org>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::shareflag>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::shareMsg>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::createDate>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::uri64>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::shareby>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::sharedate>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::size>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::mime>("ExpSyncDoc")
        .data<&anson::ExpSyncDoc::folder>("ExpSyncDoc")
        ;
}

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

    void format(IFileDescriptor p);

    DocsReq(AnsonMsg<AnsonBody> parent, string uri, IFileDescriptor p) : UserReq(uri, _type_)  {
        format(p);
    };

    DocsReq(string docTabl, ExpSyncDoc doc, string uri) : UserReq(uri, _type_), docTabl(docTabl), doc(doc) {};
};

inline static void load_docsreqAst(AstMap &asts, const string &ast_path) {
    specialize_msg_astpth<DocsReq, UserReq>(asts, ast_path,
      [](meta_factory<DocsReq> &entf, AnsonBodyAst *ast) {
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
        entf.ctor<AnsonMsg<AnsonBody>, string, IFileDescriptor>();
        entf.ctor<string, ExpSyncDoc, string>();

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
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

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<DocsReq>(): Failed to get entt instance (meta_any)");
            return { };
        };
  });
}

class DocsResp : public anson::AnsonResp {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.DocsResp";

    struct A {
    };
    ExpSyncDoc xdoc;
    string docTabl;
    PathsPage syncingPage;
    string collectId;
    int blockSeqReply;
    string org;
    string device;
    string stamp;
    string syndomain;
};

inline static void load_docsrespAst(AstMap &asts, const string &ast_path) {
    specialize_msg_astpth<DocsResp, AnsonResp>(asts, ast_path,
      [](meta_factory<DocsResp> &entf, AnsonBodyAst *ast) {
        entf.data<&DocsResp::xdoc>("xdoc");
        entf.data<&DocsResp::docTabl>("docTabl");
        entf.data<&DocsResp::syncingPage>("syncingPage");
        entf.data<&DocsResp::collectId>("collectId");
        entf.data<&DocsResp::blockSeqReply>("blockSeqReply");
        entf.data<&DocsResp::org>("org");
        entf.data<&DocsResp::device>("device");
        entf.data<&DocsResp::stamp>("stamp");
        entf.data<&DocsResp::syndomain>("syndomain");

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const DocsResp&>(ans);
                if ("xdoc" == fieldname)
                    return entt::forward_as_meta(concrete.xdoc);
                if ("docTabl" == fieldname)
                    return entt::forward_as_meta(concrete.docTabl);
                if ("syncingPage" == fieldname)
                    return entt::forward_as_meta(concrete.syncingPage);
                if ("collectId" == fieldname)
                    return entt::forward_as_meta(concrete.collectId);
                if ("blockSeqReply" == fieldname)
                    return entt::forward_as_meta(concrete.blockSeqReply);
                if ("org" == fieldname)
                    return entt::forward_as_meta(concrete.org);
                if ("device" == fieldname)
                    return entt::forward_as_meta(concrete.device);
                if ("stamp" == fieldname)
                    return entt::forward_as_meta(concrete.stamp);
                if ("syndomain" == fieldname)
                    return entt::forward_as_meta(concrete.syndomain);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonBodyAst *bast = IJsonable::contxt_ptr->ast<AnsonBodyAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<DocsResp>(): Failed to get entt instance (meta_any)");
            return { };
        };
  });
}

}
