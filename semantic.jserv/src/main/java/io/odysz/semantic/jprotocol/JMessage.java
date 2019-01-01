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
 * @author ody
 *
 */
public class JMessage <T extends JBody> {

	/**TODO shall we use dynamic registered ports?
	 * @author ody
	 *
	 */
	public enum Port {  heartbeat("ping.serv"), session("login.serv"),
						insert("c.serv"), query("r.serv"), update("u.serv"), delete("d.serv"),
						echo("echo.serv"), user("user.serv");
		private String url;
		Port(String url) { this.url = url; }
		public String url() { return url; }

		public static Port parse(String pport) {
			if (heartbeat.name().equals(pport)) return heartbeat;
			if (session.name().equals(pport)) return session;
			if (insert.name().equals(pport)) return insert;
			if (query.name().equals(pport)) return query;
			if (update.name().equals(pport)) return update;
			if (delete.name().equals(pport)) return delete;
			if (echo.name().equals(pport)) return echo;
			if (user.name().equals(pport)) return user;
			return null;
		}
	};

	public enum MsgCode {ok, exSession, exSemantic, exIo, exTransct, exDA, exGeneral;
		public boolean eq(String code) {
			if (code != null) return false;
			MsgCode c = MsgCode.valueOf(MsgCode.class, code);
			return this == c;
		}
	};

	static Gson gson = new Gson();

	@SuppressWarnings("unused")
	private String vestion = "1.0";
	int seq;
	public int seq() { return seq; }

	SemanticObject semanticObj;

	Port port;
	public Port port() { return port; }

	MsgCode code;
	String msg;

	protected List<T> body;
	
	JMessage() {
		seq = (int) (Math.random() * 1000);
	}

	public JMessage(Port msgCode) {
		this.port = msgCode;
		seq = (int) (Math.random() * 1000);
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

	public void body(T itm) {
		if (body == null)
			body = new ArrayList<T>();
		body.add(itm);
	}
	
	public List<T> body() {
		return body;
	}

	static String pairPrmv = "'%s': %s";
	
	@Override
	public String toString() {
		return gson.toJson(this, this.getClass());
	}

	public String toStringEx() {
//		return String.format("\n%s (id=%s)\tseq:%s\tcode:%s",
//				getClass().getSimpleName(), this.hashCode(), seq, Port == null ? null : Port.name());
		Field flist[] = this.getClass().getDeclaredFields();

		// FIXME performance problem : flat list
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
						String s = ((List<?>)m.get(this)).stream().map(e -> e.toString())
								.collect(Collectors.joining(", ", "'" + m.getName() + "': [", "]"));
						return s;
					}
					else return String.format(pairPrmv, m.getName(), m.get(this));
				} catch (IllegalArgumentException | IllegalAccessException e) {
					return String.format("EXCEPTION: \"%s\"", e.getMessage());
				}
			}).collect(Collectors.joining(", ", "{", "}"));
	}

	public String servUrl(String servRoot, String conn) {
		return String.format("%s/%s?conn=%s", servRoot, port.url(), conn);
	}

	public void port(String pport) throws SemanticException {
		port = Port.parse(pport);
		if (pport == null)
			throw new SemanticException("Port can not be null");
	}

}
