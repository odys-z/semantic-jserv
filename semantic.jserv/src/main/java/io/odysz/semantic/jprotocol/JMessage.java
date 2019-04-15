package io.odysz.semantic.jprotocol;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;

import io.odysz.semantics.SemanticObject;
import io.odysz.semantics.x.SemanticException;

/**<p>Base class of message used by JProtocol.</p>
 * <p>Relationship with {@link SemanticObject}:</p>
 * 1. A incoming json message is parsed by *.serv into JMessage, which should used to directly build statement;<br>
 * 2. A outgoing data ojbect is presented as SemanticObject, which should been directly write int output stream.
 * @author odys-z@github.com
 *
 */
public class JMessage <T extends JBody> {

	/**Port is the conceptual equivalent to the SOAP port, the service methods' group.<br>
	 * NOTE: java code shouldn't use switch-case block on enum. That cause problem with generated class.
	 * TODO shall we use dynamic registered ports?
	 * @author odys-z@github.com
	 */
	public enum Port implements IPort {  heartbeat("ping.serv"), session("login.serv"),
						insert("c.serv"), query("r.serv"), update("u.serv"), delete("d.serv"),
						echo("echo.serv"),
						/** serv port for downloading json/xml file or uploading a file.<br>
						 * @see {@link io.odysz.semantic.jserv.file.JFileServ}. */
						file("file.serv"),
						/**Any user defined request using message body of subclass of JBody must use this port */ 
						user("user.serv"),
						/** semantic tree of dataset extensions<br>
						 * @see {@link io.odysz.semantic.ext.SemanticTree}. */
						stree("s-tree.jserv"),
						/** dataset extensions<br>
						 * @see {@link io.odysz.semantic.ext.Dataset}. */
						dataset("ds.jserv");
		private String url;
		@Override public String url() { return url; }
		Port(String url) { this.url = url; }
		@Override public IPort valof(String pname) { return valueOf(pname); }
	};

	public enum MsgCode {ok, exSession, exSemantic, exIo, exTransct, exDA, exGeneral, ext;
		public boolean eq(String code) {
			if (code == null) return false;
			MsgCode c = MsgCode.valueOf(MsgCode.class, code);
			return this == c;
		}
	};

	static Gson gson = new Gson();

	/**The default IPort implelemtation.
	 * Used for parsing port name (string) to IPort instance, like {@link #Port}.<br>
	 * */
	static IPort defaultPortImpl;

	/**Set the default IPort implelemtation, which is used for parsing port name (string)
	 * to IPort instance, like {@link Port}.<br>
	 * Because {{@link Port} only defined limited ports, user must initialize JMessage with {@link understandPorts(IPort)}.<br>
	 * An example of how to use this is shown in jserv-sample/io.odysz.jsample.SysMenu.<br>
	 * Also check how to implement IPort extending {@link #Port}, see example of jserv-sample/io.odysz.jsample.protocol.Samport.
	 * */
	static public void understandPorts(IPort p) {
		defaultPortImpl = p;
	}
	
	@SuppressWarnings("unused")
	private String vestion = "1.0";
	int seq;
	public int seq() { return seq; }

	SemanticObject semanticObj;

	IPort port;
	public IPort port() { return port; }
	public void port(String pport) throws SemanticException {
		/// translate from string to enum
		if (defaultPortImpl == null)
			port = Port.echo.valof(pport);
		else
			port = defaultPortImpl.valof(pport);

		if (port == null)
			throw new SemanticException("Port can not be null. Not initialized? To use JMassage understand ports, call understandPorts(IPort) first.");
	}

	public String t;

	JMessage() {
		seq = (int) (Math.random() * 1000);
	}

	public JMessage(IPort port) {
		this.port = port;
		seq = (int) (Math.random() * 1000);
	}
	
	protected List<T> body;
	public T body(int i) { return body.get(0); }
	public List<T> body() { return body; }

	@SuppressWarnings("unchecked")
	public void body(JBody bodyItem) {
		if (body == null)
			body = new ArrayList<T>();
		body.add((T)bodyItem);
	}

	public JMessage<T> incSeq() {
		seq++;
		return this;
	}
	
	JHeader header;

	public JHeader header() { return header; }
	public JMessage<T> header(JHeader header) {
		this.header = header;
		return this;
	}

	public JMessage<T> body(List<T> bodyItems) {
		this.body = bodyItems;
		return this;
	}

	@Override
	public String toString() {
		// return gson.toJson(this, this.getClass());
		return toStringEx();
	}

	static String pairPrmv = "\n\t'%s': %s";
	public String toStringEx() {
		Field flist[] = this.getClass().getDeclaredFields();

		// FIXME performance problem : flat list (or appendable?) - stream don't help here, get instances first instead
		return Stream.of(flist)
			.filter(m -> !m.getName().startsWith("this$"))
			.map(m -> {
				try {
					Class<?> t = m.getType();
					if (m.get(this) == null)
						return String.format(pairPrmv, m.getName(), "null");
					if (t.isPrimitive() || t == String.class)
						return String.format(pairPrmv, m.getName(), m.get(this));
					else if (t.isArray()) {
						// FIXME performance problem
						String s = String.format(pairPrmv, m.getName(), 
							m == null || m.get(this) == null ? "" :
								Arrays.stream((Object[])m.get(this))
									.map(e -> {
										try {
											return e == null ? "" : e.toString();
										} catch (IllegalArgumentException e1) {
											e1.printStackTrace();
											return String.format("EXCEPTION:\"%\"", e1.getMessage());
										}
							}).collect(Collectors.joining(", ", "'" + m.getName() + "': [", "]"))
						);
						return s;
					}
					else if (List.class.isAssignableFrom(t)) {
						String s = ((List<?>)m.get(this))
								.stream()
								.map( e -> e.toString() )
								.collect(Collectors.joining(", ", "\n\t'" + m.getName() + "': [", "]"));
						return s;
					}
					else return String.format(pairPrmv, m.getName(), m.get(this));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					return String.format("EXCEPTION: \"%s\"", e.getMessage());
				}
			}).collect(Collectors.joining(", ", "{", "}"));
	}

}
