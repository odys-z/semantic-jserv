package io.odysz.semantic.jprotocol;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.Gson;

import io.odysz.semantics.SemanticObject;

/**<p>Base class of message used by JProtocol.</p>
 * <p>Relationship with {@link SemanticObject}:</p>
 * 1. A incoming json message is parsed by *.serv into JMessage, which should used to directly build statement;<br>
 * 2. A outgoing data ojbect is presented as SemanticObject, which should been directly write int output stream.
 * @author ody
 *
 */
public class JMessage {
	public enum Port { heartbeat, session, query, update };
	public enum MsgCode {ok, exSession, exSemantic, exIo, exTransct, exDA, exGeneral};

	static Gson gson = new Gson();

	@SuppressWarnings("unused")
	private String vestion = "1.0";
	int seq;
	public int seq() { return seq; }

	SemanticObject semanticObj;
	Port port;
	MsgCode code;
	String msg;

	List<? extends JMessage> body;
	
	JMessage() {
		seq = (int) (Math.random() * 1000);
	}

	public JMessage(Port msgCode) {
		this.port = msgCode;
		seq = (int) (Math.random() * 1000);
	}
	
	public JMessage incSeq() {
		seq++;
		return this;
	}
	
	public JMessage err(String msg) {
		return err(MsgCode.exGeneral, msg);
	}

	public JMessage err(MsgCode errCode, String msg) {
		code = errCode;
		this.msg = msg;
		return this;
	}
	
	public JMessage header(JHeader header) {
		return this;
	}

	public JMessage body(List<? extends JMessage> bodyItems) {
		this.body = bodyItems;
		return this;
	}
	
	public List<? extends JMessage> body() {
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

}
