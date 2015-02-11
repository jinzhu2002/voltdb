/* This file is part of VoltDB.
 * Copyright (C) 2008-2015 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package org.voltdb.compiler;

import java.util.Set;

import junit.framework.TestCase;

public class TestClassMatcher extends TestCase {
    static String testClasses = "org.voltdb.utils.BinaryDeque\n" +
                                "org.voltdb.utils.BinaryDeque$BinaryDequeTruncator\n" +
                                "org.voltdb.utils.BuildDirectoryUtils\n" +
                                "org.voltdb.utils.ByteArrayUtils\n" +
                                "org.voltdb.utils.CLibrary\n" +
                                "org.voltdb.utils.CLibrary$Rlimit\n" +
                                "org.voltdb.utils.CSVLoader\n";


    boolean strContains(String[] list, String pattern) {
        for (String s : list) {
            if (s.equals(pattern)) {
                return true;
            }
        }
        return false;
    }

    public void testSimple() {
        ClassMatcher cm = new ClassMatcher();
        cm.m_classList = testClasses;
        cm.addPattern("org.**.B*");
        Set<String> matchedClasses = cm.getMatchedClassList();
        String[] out = matchedClasses.toArray(new String[matchedClasses.size()]);
        assertEquals(3, out.length);
        assertTrue(strContains(out, "org.voltdb.utils.BinaryDeque"));
        assertTrue(strContains(out, "org.voltdb.utils.BuildDirectoryUtils"));
        assertTrue(strContains(out, "org.voltdb.utils.ByteArrayUtils"));

        cm = new ClassMatcher();
        cm.m_classList = testClasses;
        cm.addPattern("**BinaryDeque");
        matchedClasses = cm.getMatchedClassList();
        out = matchedClasses.toArray(new String[matchedClasses.size()]);
        assertEquals(1, out.length);
        assertTrue(strContains(out, "org.voltdb.utils.BinaryDeque"));

        cm = new ClassMatcher();
        cm.m_classList = testClasses;
        cm.addPattern("*.voltdb.*.CLibrary");
        matchedClasses = cm.getMatchedClassList();
        out = matchedClasses.toArray(new String[matchedClasses.size()]);
        assertEquals(1, out.length);
        assertTrue(strContains(out, "org.voltdb.utils.CLibrary"));

        cm = new ClassMatcher();
        cm.m_classList = testClasses;
        cm.addPattern("*.voltdb.*CLibrary");
        matchedClasses = cm.getMatchedClassList();
        out = matchedClasses.toArray(new String[matchedClasses.size()]);
        assertEquals(0, out.length);

        // ENG-7233
        cm = new ClassMatcher();
        cm.m_classList = testClasses;
        cm.addPattern("**.*Lib*");
        matchedClasses = cm.getMatchedClassList();
        out = matchedClasses.toArray(new String[matchedClasses.size()]);
        assertEquals(0, out.length);
    }
}
