/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.security.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import com.sun.enterprise.util.ParseUtil;

/**
 * A utility class to expand properties embedded in a string.
 * Strings of the form ${some.property.name} are expanded to
 * be the value of the property. Also, the special ${/} property
 * is expanded to be the same as file.separator. If a property
 * is not set, a GeneralSecurityException will be thrown.
 */
//This class is copied from sun.security.util.PropertyExpander as this is a JDK internal class
//and not accessible in JDK9
public class PropertyExpander {


    public static class ExpandException extends GeneralSecurityException {

        private static final long serialVersionUID = -7941948581406161702L;

        public ExpandException(String msg) {
            super(msg);
        }
    }

    public static String expand(String value)
        throws ExpandException
    {
        return expand(value, false);
    }

     public static String expand(String value, boolean encodeURL)
         throws ExpandException
     {
        if (value == null)
            return null;

        int p = value.indexOf("${", 0);

        // no special characters
        if (p == -1) return value;

        StringBuffer sb = new StringBuffer(value.length());
        int max = value.length();
        int i = 0;  // index of last character we copied

    scanner:
        while (p < max) {
            if (p > i) {
                // copy in anything before the special stuff
                sb.append(value.substring(i, p));
                i = p;
            }
            int pe = p+2;

            // do not expand ${{ ... }}
            if (pe < max && value.charAt(pe) == '{') {
                pe = value.indexOf("}}", pe);
                if (pe == -1 || pe+2 == max) {
                    // append remaining chars
                    sb.append(value.substring(p));
                    break scanner;
                } else {
                    // append as normal text
                    pe++;
                    sb.append(value.substring(p, pe+1));
                }
            } else {
                while ((pe < max) && (value.charAt(pe) != '}')) {
                    pe++;
                }
                if (pe == max) {
                    // no matching '}' found, just add in as normal text
                    sb.append(value.substring(p, pe));
                    break scanner;
                }
                String prop = value.substring(p+2, pe);
                if (prop.equals("/")) {
                    sb.append(java.io.File.separatorChar);
                } else {
                    String val = System.getProperty(prop);
                    if (val != null) {
                        if (encodeURL) {
                            // encode 'val' unless it's an absolute URI
                            // at the beginning of the string buffer
                            try {
                                if (sb.length() > 0 ||
                                    !(new URI(val)).isAbsolute()) {
                                    val = ParseUtil.encodePath(val);
                                }
                            } catch (URISyntaxException use) {
                                val = ParseUtil.encodePath(val);
                            }
                        }
                        sb.append(val);
                    } else {
                        throw new ExpandException(
                                             "unable to expand property " +
                                             prop);
                    }
                }
            }
            i = pe+1;
            p = value.indexOf("${", i);
            if (p == -1) {
                // no more to expand. copy in any extra
                if (i < max) {
                    sb.append(value.substring(i, max));
                }
                // break out of loop
                break scanner;
            }
        }
        return sb.toString();
    }
}
