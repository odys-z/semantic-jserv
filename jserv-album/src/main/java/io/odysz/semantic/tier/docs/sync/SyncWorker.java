package io.odysz.semantic.tier.docs.sync;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import io.odysz.anson.x.AnsonException;
import io.odysz.common.Utils;
import io.odysz.jclient.SessionClient;
import io.odysz.jclient.tier.ErrorCtx;
import io.odysz.semantic.jprotocol.AnsonHeader;
import io.odysz.semantic.jprotocol.AnsonMsg;
import io.odysz.semantic.jprotocol.AnsonMsg.MsgCode;
import io.odysz.semantic.jprotocol.AnsonMsg.Port;
import io.odysz.semantic.jserv.file.ISyncFile;
import io.odysz.semantic.jsession.SessionInf;
import io.odysz.semantic.tier.docs.DocsReq;
import io.odysz.semantic.tier.docs.DocsReq.A;
import io.odysz.semantic.tier.docs.DocsResp;
import io.odysz.semantics.x.SemanticException;

public class SyncWorker implements Runnable {

	public static final int hub = 0;
	public static final int main = 1;
	public static final int priv = 2;
	
	int mode;

	SessionClient client;
	
	String uri;
	private ErrorCtx errLog;

	public SyncWorker(int mode) {
		this.mode = mode;
		uri = "sync.jserv";
		
		errLog = new ErrorCtx() {
			@Override
			public void onError(MsgCode code, String msg) {
				Utils.warn(msg);
			}
		};
	}

	@Override
	public void run() {
		try {
	        DocsResp resp = null;

			String[] act = AnsonHeader.usrAct("sync.jserv", "query", "r/tasks", "query tasks");
			AnsonHeader header = client.header().act(act);

			List<DocsResp> reslts = new ArrayList<DocsResp>();

			DocsReq req = (DocsReq) new DocsReq().a(A.records);

			AnsonMsg<DocsReq> q = client.<DocsReq>userReq("", AnsonMsg.Port.docsync, req)
									.header(header);

			resp = client.commit(q, errLog);
			
			resp = syncDocs(resp);

			reslts.add(resp);

		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private DocsResp syncDocs(DocsResp resp) {
		return null;
	}

	String syncDoc(ISyncFile p, SessionInf worker, AnsonHeader header) throws AnsonException, SemanticException, IOException {
		DocsReq req = (DocsReq) new DocsReq(/* p */)
						.syncWith(p.fullpath(), p.device())
						.a(A.download);

		AnsonMsg<DocsReq> q = client.<DocsReq>userReq(uri, Port.docsync, req)
								.header(header);

		String pth = client.download(uri, Port.docsync, req, "");

		return pth;
	}

}
