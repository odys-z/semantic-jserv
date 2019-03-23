package io.odysz.semantic.workflow;

import javax.servlet.http.HttpServlet;

public class CheapServ extends HttpServlet {
	private static final long serialVersionUID = 1L;
	/**
	 * @see HttpServlet#doGet(HttpServletRequest request, HttpServletResponse response)
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (IrSingleton.debug) System.out.println("cheapwf.serv get ------");
		jsonResp(request, response);
	}
	 */

	/**
	 * @see HttpServlet#doPost(HttpServletRequest request, HttpServletResponse response)
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		if (IrSingleton.debug) System.out.println("cheapwf.serv post ======");
		jsonResp(request, response);
	}

	private void jsonResp(HttpServletRequest request, HttpServletResponse response) {
		response.setContentType("text/html;charset=UTF-8");
		JsonWriter writer = null;
		try {
			writer = Json.createWriter(response.getOutputStream());
			JsonStructure resp;

			String t = request.getParameter("t");
			if ("reload".equals(t)) {
				// reload workflow configuration (after data changed)
				reloadCheap();
				writer.write(JsonHelper.OK(String.format("There are %s workflow templates re-initialized.", wfs.size())));
			}
			else {
				JSONObject[] parsed = ServManager.parseReq(request); // [0] jheader, [1] jreq-obj
				DbLog dblog = IrSession.check(parsed[0]);

				JSONObject jarr = parsed[1];

				String connId = request.getParameter("conn");
				if (connId == null || connId.trim().length() == 0)
					connId = DA.getDefltConnId();

				IrUser usr = null;
				// check header
				JSONObject jheader = (JSONObject) jarr.get("header");
				if (jheader == null) throw new IrSessionException("Empty header for workflow request.");
				IrSession.check(jheader);
				usr = IrSession.getUser(jheader);
				if (usr == null)
					throw new IrSessionException("No such user logged in.");
			
				// handle request (next, back, deny, ...)
				JSONObject jreq = (JSONObject)jarr.get(EnginDesign.WfProtocol.reqBody);
				resp = handle(jreq, connId, t, usr, dblog);
				writer.write(resp);
			}

			writer.close();
			response.flushBuffer();
		} catch (CheapException chex) {
			if (writer != null)
				writer.write(JsonHelper.err(CheapException.ERR_WF, chex.getMessage()));
		} catch (IrSessionException ssex) {
			if (writer != null)
				writer.write(JsonHelper.err(IrSession.ERR_CHK, ssex.getMessage()));
		} catch (SQLException e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} catch (Exception e) {
			e.printStackTrace();
			if (writer != null)
				writer.write(JsonHelper.Err(e.getMessage()));
		} finally {
			if (writer != null)
				writer.close();
		}
	}
	 */
	
	/**TODO move to CheapServ
	 * Test plausibility, find routes of current node, or handle commitment.<br>
	 * To commit:<br>
	 * 1. t='test', test the accessibility<br>
	 * 1.1 req = 'start', can user start a workflow? Where right considered the same as the node1.<br>
	 * 2.2 req = other, can user commit with the request?<br>
	 * 2. t='route', (req ignored)<br>
	 *    find all possible routes<br>
	 * 3. start or commit a workflow<br>
	 * @param jobj
	 * @param connId
	 * @param t
	 * @param dblog 
	 * @param usr
	 * @return
	 * @throws SQLException
	 * @throws IOException
	 * @throws IrSemanticsException
	 * @throws CheapException
	private SemanticObject handle(SemanticObject jobj, String connId, String t, IUser usr)
			throws SQLException, IOException, SemanticException, CheapException {
		String wftype = (String) jobj.get(WfProtocol.wfid);
		String req = (String) jobj.get(WfProtocol.cmd);
		String busiId = (String) jobj.get(WfProtocol.busid); // taskId
		String currentInstId = (String) jobj.get(WfProtocol.current);
		String nodeDesc = (String)jobj.get(WfProtocol.ndesc);
		String[] busiNvs = (String[])jobj.get(WfProtocol.nvs);
		SemanticObject multireq = (SemanticObject)jobj.get(WfProtocol.busiMulti);

		// String[] postreq = (String[])jobj.get(WfProtocol.busiPostupdate);
		SemanticObject postreq = (SemanticObject)jobj.get(WfProtocol.busiPostupdate);
	
		// 1. t= test
		if (Req.Ttest.eq(t) && req != null && req.trim().length() > 0) {
			// can next? can start?
			CheapNode currentNode; 
			CheapWorkflow wf = wfs.get(wftype);
			if (Req.start.eq(req)) {
				currentNode = wf.start(); // a virtual node
				// can start?
				if (currentNode == null)
					// return JsonHelper.err(CheapException.ERR_WF, Configs.getCfg("cheap-workflow", "err-no-start-node"));
					return new SemanticObject()
							.code(CheapException.ERR_WF)
							.msg(Configs.getCfg("cheap-workflow", "err-no-start-node"));
			}
			else {
				currentNode = wf.getNodeByInst(currentInstId);
				// can next?
				if (currentNode == null)
					return new SemanticObject().code(CheapException.ERR_WF)
							.msg(Configs.getCfg("cheap-workflow", "err-no-current"));
			}

			// has route?
			CheapNode nextNode = currentNode.getRoute(req);
			if (nextNode == null)
				return new SemanticObject().code(CheapException.ERR_WF)
						.error(Configs.getCfg("cheap-workflow", "t-no-route"), req);

			// check rights
			wf.checkRights(usr, currentNode, nextNode);
		
			return new SemanticObject().code(WfProtocol.ok)
					.msg("%s:%s", req, nextNode.nodeId());
		}
		// 2. t= findroute
		if (Req.findRoute.eq(t)) {
			CheapNode currentNode; 
			CheapWorkflow wf = wfs.get(wftype);
			currentNode = wf.getNodeByInst(currentInstId);
			if (currentNode != null)
				return currentNode.formatAllRoutes(usr);
			else
				// wrong data
				return new SemanticObject().code(CheapException.ERR_WF).error("[]");
		}
		// 3. t = getDef
		else if (Req.TgetDef.eq(t)) {
			CheapWorkflow wf = wfs.get(wftype);
			if (wf != null) return wf.getDef(); else return null;
		}
		// t is anything else, handle request commands
		else {
			Object[] rets = onReqCmd(usr, wftype, currentInstId,
					req, busiId, nodeDesc, busiNvs, multireq,
					// parsePosts(postreq)
					postreq);
			Insert jupdate = (Insert) rets[0];
			ArrayList<String> sqls = new ArrayList<String>(1);
			SemanticObject newIds = jupdate.commit(sqls, usr);
			
			// fire event
			CheapEvent evt = (CheapEvent) rets[1];
			evt.resolveTaskId(newIds);
			// FIXME: Event handling can throw an exception.
			// FIXME: Event handling may needing a returning message to update nodes.
			ICheapEventHandler stepHandler = (ICheapEventHandler) rets[2];
			if (stepHandler != null)
				stepHandler.onNext(evt);
			ICheapEventHandler arriveHandler = (ICheapEventHandler) rets[3];
			if (arriveHandler  != null)
				arriveHandler.onArrive(evt);

			return new SemanticObject().code(WfProtocol.ok).data(newIds);
		}
	}
	 */


	/**
	 * Parse post updates for business requirements.<pre>
[
  -- post update request 1
  [
	{	"method":"record",
		"vals": {
				"tabl":"c_fault_rec",
				"act":{"a":"insert",
					"vals":[{"name":"inspTaskId","value":"AUTO"}],
					"conds":[{"field":"recId","v":"000001","logic":"="}]}
				}
	},
	{	"method":"multi","vals":[]	} -- any method other than "record" are ignored.
  ]
  -- more post update following here can be handled
]
</pre>
	 * Complicate request structure (self recursive structured) can not handled here. 
	 * @param postups
	 * @return
	 * @throws SQLException
	@SuppressWarnings("rawtypes")
	private static Update parsePosts(JSONArray postups) throws SQLException {
		if (postups != null) {
			// case 1
			// [
			//	-- update 1 (post_u)
			//	[ {	"method":"record",				-- record update (up_v)
			//		"vals":{"tabl":"c_fault_rec",
			//				"act":{	"a":"insert",
			//						"vals":[{"name":"inspTaskId","value":"AUTO"}],
			//						"conds":[{"field":"recId","v":"000001","logic":"="}]
			//					  }
			//				}
			//	  },
			//	  {	"method":"multi","vals":[]}		-- multi update
			//	]
			//	-- update 2
			// ]
			
			// case 2
			// [{"method":"record","vals":{"tabl":"e_areas","act":{"a":"update","vals":[{"name":"maintenceStatus"}],"conds":[{"field":"areaId","v":"000006","logic":"="}]},"postupdates":null}}]

			JRequestUpdate updates = null;
			Iterator u = postups.iterator();
			while (u.hasNext()) {
				// post update-i
				JSONArray post_u = (JSONArray) u.next();
				
				Iterator v = post_u.iterator();
				while (v.hasNext()) {
					JSONObject up_v = (JSONObject) v.next();
					String method = (String) up_v.get("method");
					// FIXME Our protocol not designed with recursive structure in mind.
					// It's not protocol needing upgraded, it's the server side structure needing to be re-designed.
					// Of course the protocol should be designed in a more elegant style.
					if (!"record".equals(method)) {
						if (debug) {
							System.err.println("CheapEngine can not handle the request (only 'record' method is supported). This is due to the protocol and architect design limitation.");
							System.err.println("Request ignored: " + up_v.toString());
						}
						continue;
					}

					JSONObject vals_v = (JSONObject) up_v.get("vals");
					String tabl = (String) vals_v.get("tabl");
					JSONObject act = (JSONObject) vals_v.get("act");
					if (act == null) continue;
				
					String a = (String) act.get("a");
					JSONArray conds = (JSONArray) act.get("conds");
					JSONArray vals = (JSONArray) act.get("vals");

					String pk = null;
					if ( conds != null && conds.size() == 1) {
						Iterator c = conds.iterator();
						JSONObject cond = (JSONObject) c.next();
						pk = (String) cond.get("field");
					}

					String[][] eqConds = JsonHelper.convertFvList(conds);
					ArrayList<String[]> inserVals = JsonHelper.convertNvList(vals);

					if ("update".equals(a))
						// updateRecord(usr, sqls, post, connId, tabl, lobs, autoK, logBuffer, flag);
						updates = new JRequestUpdate(Cmd.update, tabl, pk, eqConds, inserVals, updates);
					else if ("insert".equals(a))
						updates = new JRequestUpdate(Cmd.insert, tabl, pk, eqConds, inserVals, updates);
					else if ("delete".equals(a))
						updates = new JRequestUpdate(Cmd.delete, tabl, pk, eqConds, inserVals, updates);
					else {
						System.err.println(String.format("WARN - DeleteBatch: postupdate command is not 'update/delete/insert'. New function requirements must implemented. \ntabl=%s,\nact=%s",
								tabl, act.toJSONString()));
					}
				}
			}
			return updates;
		}
		return null;
	}
	 */
}
