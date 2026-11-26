/**
 *	Copyright 2026 Tony Bringardner
 *
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. 
 *	You may obtain a copy of the License at
 *
 *	http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed 
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for 
 *	the specific language governing permissions and limitations under the License.
 */
package us.bringardner.fsh.studio.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Expands a template's code for insertion: "${name}" becomes the text "name", a field the
 * user fills in; "${cursor}" is where the caret goes; "$$" is a "$". (The same syntax the
 * Swing IDE's autocompletion uses.)
 * <p>
 * A field with choices starts with "|": "${|-e : exists|-f : a regular file|-d : a directory}"
 * inserts the first choice's value (-e), and when the field is chosen the editor offers the list.
 * Each choice is "value : label" (or just "value"); "$$" is a "$" there too, and a "\" takes the
 * next character as it is, for a "|", "}" or "\" in a value ("${|&& : and|\\|\\| : or}").
 */
public final class TemplateText {

	/** One of a field's choices: what it inserts, and what it means. */
	public static final class Choice {
		public final String value;
		public final String label;

		public Choice(String value, String label) {
			this.value = value;
			this.label = label == null ? "" : label;
		}

		@Override
		public String toString() {
			return label.isEmpty() ? value : value + "   " + label;
		}
	}

	/** A template's code, expanded. */
	public static final class Expansion {
		public final String text;
		/** The fields, in order, each [start, end) in text. */
		public final List<int[]> fields;
		/** Each field's choices, in the same order as fields: null for a field without them. */
		public final List<List<Choice>> choices;
		/** Where the caret goes, or -1 (then: on the first field, else after the text). */
		public final int cursor;

		Expansion(String text, List<int[]> fields, List<List<Choice>> choices, int cursor) {
			this.text = text;
			this.fields = Collections.unmodifiableList(fields);
			this.choices = Collections.unmodifiableList(choices);
			this.cursor = cursor;
		}
	}

	private TemplateText() {
	}

	public static Expansion expand(String code) {
		StringBuilder out = new StringBuilder();
		List<int[]> fields = new ArrayList<>();
		List<List<Choice>> choices = new ArrayList<>();
		int cursor = -1;
		int i = 0;
		while( i < code.length()) {
			if( code.startsWith("$$", i)) {
				out.append('$');
				i += 2;
			} else if( code.startsWith("${|", i)) {
				List<String> parts = new ArrayList<>();
				int close = choiceParts(code, i+3, parts);
				if( close < 0 ) {
					out.append(code, i, code.length());
					break;
				}
				List<Choice> list = new ArrayList<>();
				for(String part : parts) {
					list.add(choice(part));
				}
				String value = list.isEmpty() ? "" : list.get(0).value;
				fields.add(new int[] {out.length(), out.length()+value.length()});
				choices.add(Collections.unmodifiableList(list));
				out.append(value);
				i = close+1;
			} else if( code.startsWith("${", i)) {
				int close = code.indexOf('}', i+2);
				if( close < 0 ) {
					out.append(code, i, code.length());
					break;
				}
				String name = code.substring(i+2, close);
				if( name.equals("cursor")) {
					cursor = out.length();
				} else {
					fields.add(new int[] {out.length(), out.length()+name.length()});
					choices.add(null);
					out.append(name);
				}
				i = close+1;
			} else {
				out.append(code.charAt(i++));
			}
		}
		return new Expansion(out.toString(), fields, choices, cursor);
	}

	/**
	 * The code with each field with choices made a plain field of its first value
	 * ("${|-e : exists|-f : a file}" is "${-e}"), for the Swing IDE's autocompletion, which takes
	 * the choices separately ({@link #expand(String)}'s choices, in the same order).
	 */
	public static String withoutChoices(String code) {
		StringBuilder out = new StringBuilder();
		int i = 0;
		while( i < code.length()) {
			if( code.startsWith("$$", i)) {
				out.append("$$");
				i += 2;
			} else if( code.startsWith("${|", i)) {
				List<String> parts = new ArrayList<>();
				int close = choiceParts(code, i+3, parts);
				if( close < 0 ) {
					out.append(code, i, code.length());
					break;
				}
				out.append("${").append(parts.isEmpty() ? "" : choice(parts.get(0)).value).append('}');
				i = close+1;
			} else {
				out.append(code.charAt(i++));
			}
		}
		return out.toString();
	}

	/**
	 * What's wrong with a "$" in a template's code, or null: a "$" must be "$$" (a "$"), "${name}" (a
	 * field) or "${|...}" (a field with choices). Anything else ("echo $HOME", "$(date)", a "${" with no
	 * "}") makes the autocompletion library loop for ever when it reads the template, so it isn't kept
	 * (and {@link #escapeStray(String)} makes one that was kept safe).
	 */
	public static String problem(String code) {
		return code == null ? null : scan(code, null);
	}

	/**
	 * The code with every "$" that isn't "$$", "${name}" or "${|...}" made a "$$" (a "$"), and a "${"
	 * with no "}" a "$${": so "echo $HOME" is put in as it is typed. Code that has no such "$" is
	 * returned as it is.
	 */
	public static String escapeStray(String code) {
		if( code == null || code.indexOf('$') < 0 ) {
			return code;
		}
		StringBuilder fixed = new StringBuilder();
		scan(code, fixed);
		return fixed.toString();
	}

	/**
	 * Reads the "$"s of code: the first problem as a message (null if none), and, if fixed isn't null,
	 * the code with each problem escaped. Every "$" is passed or escaped, so it always ends.
	 */
	private static String scan(String code, StringBuilder fixed) {
		String problem = null;
		int line = 1;
		int i = 0;
		while( i < code.length()) {
			char c = code.charAt(i);
			if( c == '\n' ) {
				line++;
			}
			if( c != '$' ) {
				if( fixed != null ) {
					fixed.append(c);
				}
				i++;
			} else if( code.startsWith("$$", i)) {
				if( fixed != null ) {
					fixed.append("$$");
				}
				i += 2;
			} else if( code.startsWith("${|", i)) {
				int close = choiceParts(code, i+3, new ArrayList<>());
				if( close < 0 ) {
					problem = problem != null ? problem : unclosed(line);
					if( fixed != null ) {
						fixed.append("$$");
					}
					i++;
				} else {
					if( fixed != null ) {
						fixed.append(code, i, close+1);
					}
					line += newLines(code, i, close);
					i = close+1;
				}
			} else if( code.startsWith("${", i)) {
				int close = code.indexOf('}', i+2);
				if( close < 0 ) {
					problem = problem != null ? problem : unclosed(line);
					if( fixed != null ) {
						fixed.append("$$");
					}
					i++;
				} else {
					if( fixed != null ) {
						fixed.append(code, i, close+1);
					}
					line += newLines(code, i, close);
					i = close+1;
				}
			} else {
				problem = problem != null ? problem : "Line " + line
						+ ": a $ that isn't a field: write $$ for a $ (\"echo $$HOME\") or ${name} for a field";
				if( fixed != null ) {
					fixed.append("$$");
				}
				i++;
			}
		}
		return problem;
	}

	private static String unclosed(int line) {
		return "Line " + line + ": a \"${\" with no closing \"}\": write $${ for a literal ${";
	}

	private static int newLines(String code, int from, int to) {
		int n = 0;
		for(int i = from; i <= to; i++) {
			if( code.charAt(i) == '\n' ) {
				n++;
			}
		}
		return n;
	}

	/** The choices of a field from at (after "${|") to its "}", into parts: the "}"'s index, or -1. */
	private static int choiceParts(String code, int at, List<String> parts) {
		StringBuilder part = new StringBuilder();
		for(int i = at; i < code.length(); i++) {
			char c = code.charAt(i);
			if( c == '\\' && i+1 < code.length()) {
				part.append(code.charAt(++i));
			} else if( code.startsWith("$$", i)) {
				part.append('$');
				i++;
			} else if( c == '|' ) {
				parts.add(part.toString());
				part.setLength(0);
			} else if( c == '}' ) {
				if( part.length() > 0 ) {
					parts.add(part.toString());
				}
				return i;
			} else {
				part.append(c);
			}
		}
		return -1;
	}

	/** "value : label" or "value" */
	private static Choice choice(String part) {
		int sep = part.indexOf(" : ");
		return sep < 0 ? new Choice(part.trim(), "") : new Choice(part.substring(0, sep).trim(), part.substring(sep+3).trim());
	}
}
