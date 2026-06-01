#pragma once

#include <entt/meta/factory.hpp>
#include <entt/meta/meta.hpp>

#include <io/odysz/anson.h>
#include <io/odysz/jprotocol.h>
#include <io/odysz/entt_jserv.h>
#include <io/odysz/module/rs.h>

#include <io/odysz/dbmeta.h>

namespace anson {

class PageInf : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.transact.sql.PageInf";
    long page;
    long size;
    long total;
    vector<vector<string>> arrCondts;
    map<string, vector<LangExt::VarType>> mapCondts;

    PageInf() : Anson() {
        Type(_type_);
    }
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

        .data<&anson::PageInf::page>("page")
        .data<&anson::PageInf::size>("size")
        .data<&anson::PageInf::total>("total")
        .data<&anson::PageInf::arrCondts>("arrCondts")
        .data<&anson::PageInf::mapCondts>("mapCondts")
        ;

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const PageInf&>(ans);
                if ("page" == fieldname)
                    return entt::forward_as_meta(concrete.page);
                if ("size" == fieldname)
                    return entt::forward_as_meta(concrete.size);
                if ("total" == fieldname)
                    return entt::forward_as_meta(concrete.total);
                if ("arrCondts" == fieldname)
                    return entt::forward_as_meta(concrete.arrCondts);
                if ("mapCondts" == fieldname)
                    return entt::forward_as_meta(concrete.mapCondts);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonAst *bast = IJsonable::contxt_ptr->ast<AnsonAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<PageInf>(): Failed to get entt instance (meta_any)");
            return { };
        };
}

class Device : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.Device";
    string id;
    string synode0;
    string devname;
    string toFolder;

    Device() : Anson() {
        Type(_type_);
    }

    Device(const string& id, const string& synode0, const string& devname) : Anson() {
        Type(_type_);
        this->id = id;
        this->synode0 = synode0;
        this->devname = devname;
    }
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
        .ctor<const string&, const string&, const string&>()

        .data<&anson::Device::id>("id")
        .data<&anson::Device::synode0>("synode0")
        .data<&anson::Device::devname>("devname")
        .data<&anson::Device::toFolder>("toFolder")
        ;

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const Device&>(ans);
                if ("id" == fieldname)
                    return entt::forward_as_meta(concrete.id);
                if ("synode0" == fieldname)
                    return entt::forward_as_meta(concrete.synode0);
                if ("devname" == fieldname)
                    return entt::forward_as_meta(concrete.devname);
                if ("toFolder" == fieldname)
                    return entt::forward_as_meta(concrete.toFolder);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonAst *bast = IJsonable::contxt_ptr->ast<AnsonAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<Device>(): Failed to get entt instance (meta_any)");
            return { };
        };
}

class SynEntity : public anson::Anson {
public:
    inline static const std::string _type_ = "io.oz.syn.SynEntity";
    SynEntityMeta entm;
    string recId;
    string uids;
    string synode;
    string synoder;

    SynEntity() : Anson() {
        Type(_type_);
    }

    SynEntity(const SynEntityMeta & m) : SynEntity() {
        entm = m;
    }
};

inline static void register_synentityAst(AstMap & asts) {

    AnsonAst * ast = createAST <SynEntity, AnsonAst> (
        asts, Anson::_type_, map <string, AnsonField> {
        {"entm", {.dataAnclass="SynEntityMeta"} },
        {"recId", {.dataAnclass="string"} },
        {"uids", {.dataAnclass="string"} },
        {"synode", {.dataAnclass="string"} },
        {"synoder", {.dataAnclass="string"} },
       });

    entt::meta_factory <anson::SynEntity> ()
        .type(ast->enttypeid)
        .base<Anson>()
        .ctor<>()
        .ctor<const SynEntityMeta &>()

        .data<&anson::SynEntity::entm>("entm")
        .data<&anson::SynEntity::recId>("recId")
        .data<&anson::SynEntity::uids>("uids")
        .data<&anson::SynEntity::synode>("synode")
        .data<&anson::SynEntity::synoder>("synoder")
        ;

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const SynEntity&>(ans);
                if ("entm" == fieldname)
                    return entt::forward_as_meta(concrete.entm);
                if ("recId" == fieldname)
                    return entt::forward_as_meta(concrete.recId);
                if ("uids" == fieldname)
                    return entt::forward_as_meta(concrete.uids);
                if ("synode" == fieldname)
                    return entt::forward_as_meta(concrete.synode);
                if ("synoder" == fieldname)
                    return entt::forward_as_meta(concrete.synoder);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonAst *bast = IJsonable::contxt_ptr->ast<AnsonAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<SynEntity>(): Failed to get entt instance (meta_any)");
            return { };
        };
}

class PathsPage : public anson::Anson {
public:
    inline static const std::string _type_ = "io.odysz.semantic.tier.docs.PathsPage";
    string device;
    int start;
    int end;
    map<string, vector<LangExt::VarType>> clientPaths;

    PathsPage() : Anson() {
        Type(_type_);
    }

    PathsPage(int start, int end) : Anson(), start(start), end(end) {
        Type(_type_);
    }

    PathsPage(const string & dev, int start, int end) : Anson(), device(dev), start(start), end(end) {
        Type(_type_);
    }
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
        .ctor<const string &, int, int>()

        .data<&anson::PathsPage::device>("device")
        .data<&anson::PathsPage::start>("start")
        .data<&anson::PathsPage::end>("end")
        .data<&anson::PathsPage::clientPaths>("clientPaths")
        ;

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const PathsPage&>(ans);
                if ("device" == fieldname)
                    return entt::forward_as_meta(concrete.device);
                if ("start" == fieldname)
                    return entt::forward_as_meta(concrete.start);
                if ("end" == fieldname)
                    return entt::forward_as_meta(concrete.end);
                if ("clientPaths" == fieldname)
                    return entt::forward_as_meta(concrete.clientPaths);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonAst *bast = IJsonable::contxt_ptr->ast<AnsonAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<PathsPage>(): Failed to get entt instance (meta_any)");
            return { };
        };
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

    ExpSyncDoc() : SynEntity() {
        Type(_type_);
    }

    ExpSyncDoc(const SynEntityMeta& m, const string& orgid) : SynEntity(m), org(orgid) {
        Type(_type_);
    }

    void format(const AnResultset& rs);

    ExpSyncDoc(const SynEntityMeta& m, const AnResultset& rs) : SynEntity(m) {
        Type(_type_);
        format(rs);
    }
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
        .ctor<const SynEntityMeta&, const string&>()
        .ctor<const SynEntityMeta&, const AnResultset&>()

        .data<&anson::ExpSyncDoc::pname>("pname")
        .data<&anson::ExpSyncDoc::clientpath>("clientpath")
        .data<&anson::ExpSyncDoc::device>("device")
        .data<&anson::ExpSyncDoc::org>("org")
        .data<&anson::ExpSyncDoc::shareflag>("shareflag")
        .data<&anson::ExpSyncDoc::shareMsg>("shareMsg")
        .data<&anson::ExpSyncDoc::createDate>("createDate")
        .data<&anson::ExpSyncDoc::uri64>("uri64")
        .data<&anson::ExpSyncDoc::shareby>("shareby")
        .data<&anson::ExpSyncDoc::sharedate>("sharedate")
        .data<&anson::ExpSyncDoc::size>("size")
        .data<&anson::ExpSyncDoc::mime>("mime")
        .data<&anson::ExpSyncDoc::folder>("folder")
        ;

        //
        ast->get_field_instance = [ast](const IJsonable& ans, const string& fieldname) -> meta_any {
            if (ast->fields.contains(fieldname)) {
                auto& concrete = static_cast<const ExpSyncDoc&>(ans);
                if ("pname" == fieldname)
                    return entt::forward_as_meta(concrete.pname);
                if ("clientpath" == fieldname)
                    return entt::forward_as_meta(concrete.clientpath);
                if ("device" == fieldname)
                    return entt::forward_as_meta(concrete.device);
                if ("org" == fieldname)
                    return entt::forward_as_meta(concrete.org);
                if ("shareflag" == fieldname)
                    return entt::forward_as_meta(concrete.shareflag);
                if ("shareMsg" == fieldname)
                    return entt::forward_as_meta(concrete.shareMsg);
                if ("createDate" == fieldname)
                    return entt::forward_as_meta(concrete.createDate);
                if ("uri64" == fieldname)
                    return entt::forward_as_meta(concrete.uri64);
                if ("shareby" == fieldname)
                    return entt::forward_as_meta(concrete.shareby);
                if ("sharedate" == fieldname)
                    return entt::forward_as_meta(concrete.sharedate);
                if ("size" == fieldname)
                    return entt::forward_as_meta(concrete.size);
                if ("mime" == fieldname)
                    return entt::forward_as_meta(concrete.mime);
                if ("folder" == fieldname)
                    return entt::forward_as_meta(concrete.folder);
            }

            if (IJsonable::contxt_ptr->has_ast(ast->baseAnclass)) {
                AnsonAst *bast = IJsonable::contxt_ptr->ast<AnsonAst>(ast->baseAnclass);
                return bast->get_field_instance(ans, fieldname);
            }

            anerror("get_field_instance<ExpSyncDoc>(): Failed to get entt instance (meta_any)");
            return { };
        };
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
    Device device;
    string stamp;
    string syndomain;

    DocsResp() : AnsonResp() {
        Type(_type_);
    }
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

inline static void register_doctier(AstMap &asts, const string &ast_folder) {
    register_pageinfAst(asts);
    register_deviceAst(asts);
    register_synentityAst(asts);
    register_pathspageAst(asts);
    register_expsyncdocAst(asts);
    load_docsreqAst(asts, ast_folder + "ast/docsreq.ast.json");
    load_docsrespAst(asts, ast_folder + "ast/docsresp.ast.json");
}

}
