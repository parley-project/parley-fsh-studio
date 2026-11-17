package us.bringardner.fsh.studio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The script a command line names. */
public class TestFshStudioArguments {

	@Test
	public void theFirstArgumentIsTheScript() {
		assertEquals("/tmp/a.fsh", FshStudio.fileArgument(new String[] {"/tmp/a.fsh", "other"}));
	}

	@Test
	public void noArgumentMeansTheScriptEditedLast() {
		assertNull(FshStudio.fileArgument(new String[0]));
		assertNull(FshStudio.fileArgument(null));
		assertNull(FshStudio.fileArgument(new String[] {""}));
	}
}
