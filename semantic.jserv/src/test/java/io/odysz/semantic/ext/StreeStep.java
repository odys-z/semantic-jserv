package io.odysz.semantic.ext;

import java.util.List;

import io.odysz.anson.Anson;

/**
 * A step of {@link StreeCase}: one tree modification and the expected results.
 * @since 1.5.20
 */
public class StreeStep extends Anson {
	/** the placement, see {@link StreePlace} */
	StreePlace req;

	/** expected reload root, null for the whole forest */
	String root;

	/** expected rejection reason, null if accepted */
	TreeModify.Rejected rejected;

	/** expected tree after the step */
	List<String> expectings;

	public StreeStep() { }
}
