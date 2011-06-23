/**
 *  Copyright (C) 2010 Cloud.com, Inc.  All rights reserved.
 * 
 * This software is licensed under the GNU General Public License v3 or later.
 * 
 * It is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * 
 */

package com.cloud.utils.log;

import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.util.ArrayList;

import org.apache.log4j.spi.ThrowableRenderer;

/**
 * This renderer removes all the Cglib generated methods from the call
 * stack. It is generally not useful and makes the log confusing to look
 * at.
 * 
 * Unfortunately, I had to copy out the EnhancedThrowableRenderer from
 * the apach libraries because EnhancedThrowableRenderer is a final class.
 * I would have much more preferred to extend EnhancedThrowableRenderer and
 * simply override doRender. Not sure what the developers are thinking there
 * making it final.
 * 
 * In order to use this you must add
 * <throwableRenderer class="com.cloud.utils.log.CglibThrowableRenderer"/>
 * into log4j.xml.
 * 
 */
public class CglibThrowableRenderer implements ThrowableRenderer {
    /**
     * Throwable.getStackTrace() method.
     */
    private Method getStackTraceMethod;
    /**
     * StackTraceElement.getClassName() method.
     */
    private Method getClassNameMethod;

    /**
     * Construct new instance.
     */
    public CglibThrowableRenderer() {
        try {
            Class[] noArgs = null;
            getStackTraceMethod = Throwable.class.getMethod("getStackTrace", noArgs);
            Class ste = Class.forName("java.lang.StackTraceElement");
            getClassNameMethod = ste.getMethod("getClassName", noArgs);
        } catch (Exception ex) {
        }
    }

    @Override
    public String[] doRender(final Throwable th) {
        try {
            ArrayList<String> lines = new ArrayList<String>();
            Throwable throwable = th;
            lines.add(throwable.toString());
            int start = 0;
            do {
                StackTraceElement[] elements = throwable.getStackTrace();
                for (int i = 0; i < elements.length - start; i++) {
                    StackTraceElement element = elements[i];
                    String filename = element.getFileName();
                    String method = element.getMethodName();
                    if ((filename != null && filename.equals("<generated>")) || (method != null && method.equals("invokeSuper"))) {
                        continue;
                    }
                    lines.add("\tat " + element.toString());
                }
                if (start != 0) {
                    lines.add("\t... " + start + " more");
                }
                throwable = throwable.getCause();
                if (throwable != null) {
                    lines.add("Caused by: " + throwable.toString());
                    start = elements.length - 1;
                }
            } while (throwable != null);
            return lines.toArray(new String[lines.size()]);
        } catch (Exception ex) {
            PrintWriter pw = new PrintWriter(System.err);
            ex.printStackTrace(pw);
            pw = new PrintWriter(System.out);
            ex.printStackTrace(pw);
            ex.printStackTrace();
            return null;
        }
    }

    /**
     * Find class given class name.
     * 
     * @param className class name, may not be null.
     * @return class, will not be null.
     * @throws ClassNotFoundException thrown if class can not be found.
     */
    private Class findClass(final String className) throws ClassNotFoundException {
        try {
            return Thread.currentThread().getContextClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            try {
                return Class.forName(className);
            } catch (ClassNotFoundException e1) {
                return getClass().getClassLoader().loadClass(className);
            }
        }
    }

}
