//
// Credits to org.eclipse.jetty.util;
//
// ========================================================================
// Copyright (c) 1995 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================

package io.oz.jserv.docs.syn.singleton;

import java.util.ArrayList;
import java.util.List;

public class StringUtil {
    /**
     * Parse a CSV string using {@link #csvSplit(List, String, int, int)}
     *
     * @param s The string to parse
     * @return An array of parsed values.
     */
    public static String[] csvSplit(String s)
    {
        if (s == null)
            return null;
        return csvSplit(s, 0, s.length());
    }

    /**
     * Parse a CSV string using {@link #csvSplit(List, String, int, int)}
     *
     * @param s The string to parse
     * @param off The offset into the string to start parsing
     * @param len The len in characters to parse
     * @return An array of parsed values.
     */
    public static String[] csvSplit(String s, int off, int len)
    {
        if (s == null)
            return null;
        if (off < 0 || len < 0 || off > s.length())
            throw new IllegalArgumentException();
        List<String> list = new ArrayList<>();
        csvSplit(list, s, off, len);
        return list.toArray(new String[0]);
    }

    enum CsvSplitState
    {
        PRE_DATA, QUOTE, SLOSH, DATA, WHITE, POST_DATA
    }

    /**
     * Split a quoted comma separated string to a list
     * <p>Handle <a href="https://www.ietf.org/rfc/rfc4180.txt">rfc4180</a>-like
     * CSV strings, with the exceptions:<ul>
     * <li>quoted values may contain double quotes escaped with back-slash
     * <li>Non-quoted values are trimmed of leading trailing white space
     * <li>trailing commas are ignored
     * <li>double commas result in a empty string value
     * </ul>
     *
     * @param list The Collection to split to (or null to get a new list)
     * @param s The string to parse
     * @param off The offset into the string to start parsing
     * @param len The len in characters to parse
     * @return list containing the parsed list values
     */
    public static List<String> csvSplit(List<String> list, String s, int off, int len)
    {
        if (list == null)
            list = new ArrayList<>();
        CsvSplitState state = CsvSplitState.PRE_DATA;
        StringBuilder out = new StringBuilder();
        int last = -1;
        while (len > 0)
        {
            char ch = s.charAt(off++);
            len--;

            switch (state)
            {
                case PRE_DATA ->
                {
                    if ('"' == ch)
                    {
                        state = CsvSplitState.QUOTE;
                    }
                    else if (',' == ch)
                    {
                        list.add("");
                    }
                    else if (!Character.isWhitespace(ch))
                    {
                        state = CsvSplitState.DATA;
                        out.append(ch);
                    }
                }
                case DATA ->
                {
                    if (Character.isWhitespace(ch))
                    {
                        last = out.length();
                        out.append(ch);
                        state = CsvSplitState.WHITE;
                    }
                    else if (',' == ch)
                    {
                        list.add(out.toString());
                        out.setLength(0);
                        state = CsvSplitState.PRE_DATA;
                    }
                    else
                    {
                        out.append(ch);
                    }
                }
                case WHITE ->
                {
                    if (Character.isWhitespace(ch))
                    {
                        out.append(ch);
                    }
                    else if (',' == ch)
                    {
                        out.setLength(last);
                        list.add(out.toString());
                        out.setLength(0);
                        state = CsvSplitState.PRE_DATA;
                    }
                    else
                    {
                        state = CsvSplitState.DATA;
                        out.append(ch);
                        last = -1;
                    }
                }
                case QUOTE ->
                {
                    if ('\\' == ch)
                    {
                        state = CsvSplitState.SLOSH;
                    }
                    else if ('"' == ch)
                    {
                        list.add(out.toString());
                        out.setLength(0);
                        state = CsvSplitState.POST_DATA;
                    }
                    else
                    {
                        out.append(ch);
                    }
                }
                case SLOSH ->
                {
                    out.append(ch);
                    state = CsvSplitState.QUOTE;
                }
                case POST_DATA ->
                {
                    if (',' == ch)
                    {
                        state = CsvSplitState.PRE_DATA;
                    }
                }
                default -> throw new IllegalStateException(state.toString());
            }
        }
        switch (state)
        {
            case PRE_DATA:
            case POST_DATA:
                break;

            case DATA:
            case QUOTE:
            case SLOSH:
                list.add(out.toString());
                break;

            case WHITE:
                out.setLength(last);
                list.add(out.toString());
                break;

            default:
                throw new IllegalStateException(state.toString());
        }

        return list;
    }
    
    /**
     * Replace substrings within string.
     * <p>
     * Fast replacement for {@code java.lang.String#}{@link String#replace(CharSequence, CharSequence)}
     * </p>
     *
     * @param s the input string
     * @param sub the string to look for
     * @param with the string to replace with
     * @return the now replaced string
     */
    public static String replace(String s, String sub, String with)
    {
        if (s == null)
            return null;

        int c = 0;
        int i = s.indexOf(sub, c);
        if (i == -1)
        {
            return s;
        }
        StringBuilder buf = new StringBuilder(s.length() + with.length());
        do
        {
            buf.append(s, c, i);
            buf.append(with);
            c = i + sub.length();
        }
        while ((i = s.indexOf(sub, c)) != -1);
        if (c < s.length())
        {
            buf.append(s.substring(c));
        }
        return buf.toString();
    }

}
