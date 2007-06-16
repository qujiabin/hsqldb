/* Copyright (c) 2001-2007, The HSQL Development Group
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * Neither the name of the HSQL Development Group nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL HSQL DEVELOPMENT GROUP, HSQLDB.ORG,
 * OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
 * PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


package org.hsqldb.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/* $Id$ */

/**
 * Sql Tool.  A command-line and/or interactive SQL tool.
 * (Note:  For every Javadoc block comment, I'm using a single blank line
 *  immediately after the description, just like's Sun's examples in
 *  their Coding Conventions document).
 *
 * See JavaDocs for the main method for syntax of how to run.
 * This class is mostly used in a static (a.o.t. object) way, because most
 * of the work is done in the static main class.
 * This class should be refactored so that the main work is done in an
 * object method, and the static main invokes the object method.
 * Then programmatic users could use instances of this class in the normal
 * Java way.
 *
 * @see #main()
 * @version $Revision$
 * @author Blaine Simpson unsaved@users
 */
public class SqlTool {

    private static final String DEFAULT_RCFILE =
        System.getProperty("user.home") + "/sqltool.rc";
    private Connection conn;

    // N.b. the following is static!
    private static String  revnum = null;

    public static final int SQLTOOLERR_EXITVAL = 1;
    public static final int SYNTAXERR_EXITVAL = 11;
    public static final int RCERR_EXITVAL = 2;
    public static final int SQLERR_EXITVAL = 3;
    public static final int IOERR_EXITVAL = 4;
    public static final int FILEERR_EXITVAL = 5;
    public static final int INPUTERR_EXITVAL = 6;
    public static final int CONNECTERR_EXITVAL = 7;

    /**
     * The configuration identifier to use when connection parameters are
     * specified on the command line
     */
    private static String CMDLINE_ID = "cmdline";

    static {
        revnum = "$Revision$".substring("$Revision: ".length(),
                                               "$Revision$".length()
                                               - 2);
    }
    public static String LS = System.getProperty("line.separator");
    private static RefCapablePropertyResourceBundle bundle =
            RefCapablePropertyResourceBundle.getBundle(
                    "org.hsqldb.util.sqltool");

    private static final String SYNTAX_MESSAGE =
            bundle.getString("SqlTool.syntax") + revnum + '.';

    /** Utility nested class for internal use. */
    private static class BadCmdline extends Exception {
        static final long serialVersionUID = -2134764796788108325L;
        BadCmdline() {}
    }

    /** Utility object for internal use. */
    private static BadCmdline bcl = new BadCmdline();

    /** For trapping of exceptions inside this class.
     * These are always handled inside this class.
     */
    private static class PrivateException extends Exception {
        static final long serialVersionUID = -7765061479594523462L;

        PrivateException() {
            super();
        }

        PrivateException(String s) {
            super(s);
        }
    }

    public static class SqlToolException extends Exception {
        static final long serialVersionUID = 1424909871915188519L;

        int exitValue = 1;
        SqlToolException(String message, int exitValue) {
            super(message);
            this.exitValue = exitValue;
        }
        SqlToolException(int exitValue, String message) {
            this(message, exitValue);
        }
        SqlToolException(int exitValue) {
            super();
            this.exitValue = exitValue;
        }
    }

    /**
     * Prompt the user for a password.
     *
     * @param username The user the password is for
     * @return The password the user entered
     */
    private static String promptForPassword(String username)
    throws PrivateException {

        BufferedReader console;
        String         password;

        password = null;

        try {
            console = new BufferedReader(new InputStreamReader(System.in));

            // Prompt for password
            System.out.print(RCData.expandSysPropVars(username)
                    + "'s password: ");

            // Read the password from the command line
            password = console.readLine();

            if (password == null) {
                password = "";
            } else {
                password = password.trim();
            }
        } catch (IOException e) {
            throw new PrivateException(e.getMessage());
        }

        return password;
    }

    /**
     * Parses a comma delimited string of name value pairs into a
     * <code>Map</code> object.
     *
     * @param varString The string to parse
     * @param varMap The map to save the pared values into
     * @param lowerCaseKeys Set to <code>true</code> if the map keys should be
     *        converted to lower case
     */
    private static void varParser(String varString, Map varMap,
                                  boolean lowerCaseKeys)
                                  throws PrivateException {

        int             equals;
        String          curSetting;
        String          var;
        String          val;
        StringTokenizer allvars;

        if ((varMap == null) || (varString == null)) {
            return;
        }

        allvars = new StringTokenizer(varString, ",");

        while (allvars.hasMoreTokens()) {
            curSetting = allvars.nextToken().trim();
            equals     = curSetting.indexOf('=');

            if (equals < 1) {
                throw new PrivateException(
                    "Var settings not of format name=value[,...]");
            }

            var = curSetting.substring(0, equals).trim();
            val = curSetting.substring(equals + 1).trim();

            if (var.length() < 1 || val.length() < 1) {
                throw new PrivateException(
                    "Var settings not of format name=value[,...]");
            }

            if (lowerCaseKeys) {
                var = var.toLowerCase();
            }

            varMap.put(var, val);
        }
    }

    /**
     * A static wrapper for objectMain, so that that method may be executed
     * as a Java "program".
     *
     * Throws only RuntimExceptions or Errors, because this method is intended
     * to System.exit() for all but disasterous system problems, for which
     * the inconvenience of a a stack trace would be the least of your worries.
     *
     * If you don't want SqlTool to System.exit(), then use the method
     * objectMain() instead of this method.
     *
     * @see objectMain(String[])
     */
    public static void main(String[] args) {
        try {
            new SqlTool().objectMain(args);
        } catch (SqlToolException fr) {
            if (fr.getMessage() != null) {
                System.err.println(fr.getMessage());
            }
            System.exit(fr.exitValue);
        }
        System.exit(0);
    }

    /**
     * Connect to a JDBC Database and execute the commands given on
     * stdin or in SQL file(s).
     *
     * This method is changed for HSQLDB 1.8.0.8 and 1.9.0.x to never
     * System.exit().
     *
     * @param arg  Run "java... org.hsqldb.util.SqlTool --help" for syntax.
     * @throws SqlToolException  Upon any fatal error, with useful
     *                          reason as the exception's message.
     */
    public void objectMain(String[] arg) throws SqlToolException {

        /*
         * The big picture is, we parse input args; load a RCData;
         * get a JDBC Connection with the RCData; instantiate and
         * execute as many SqlFiles as we need to.
         */
        String  rcFile           = null;
        File    tmpFile          = null;
        String  sqlText          = null;
        String  driver           = null;
        String  targetDb         = null;
        String  varSettings      = null;
        boolean debug            = false;
        File[]  scriptFiles      = null;
        int     i                = -1;
        boolean listMode         = false;
        boolean interactive      = false;
        boolean noinput          = false;
        boolean noautoFile       = false;
        boolean autoCommit       = false;
        Boolean coeOverride      = null;
        Boolean stdinputOverride = null;
        String  rcParams         = null;
        String  rcUrl            = null;
        String  rcUsername       = null;
        String  rcPassword       = null;
        String  rcDriver         = null;
        String  rcCharset        = null;
        String  rcTruststore     = null;
        Map     rcFields         = null;
        String  parameter;

        try {
            while ((i + 1 < arg.length) && arg[i + 1].startsWith("--")) {
                i++;

                if (arg[i].length() == 2) {
                    break;             // "--"
                }

                parameter = arg[i].substring(2).toLowerCase();

                if (parameter.equals("help")) {
                    System.out.println(SYNTAX_MESSAGE);
                    return;
                }
                if (parameter.equals("abortonerr")) {
                    if (coeOverride != null) {
                        throw new SqlToolException(SYNTAXERR_EXITVAL,
                            "Switches '--abortOnErr' and "
                            + "'--continueOnErr' are mutually exclusive");
                    }

                    coeOverride = Boolean.FALSE;
                } else if (parameter.equals("continueonerr")) {
                    if (coeOverride != null) {
                        throw new SqlToolException(SYNTAXERR_EXITVAL,
                            "Switches '--abortOnErr' and "
                            + "'--continueOnErr' are mutually exclusive");
                    }

                    coeOverride = Boolean.TRUE;
                } else if (parameter.equals("list")) {
                    listMode = true;
                } else if (parameter.equals("rcfile")) {
                    if (++i == arg.length) {
                        throw bcl;
                    }

                    rcFile = arg[i];
                } else if (parameter.equals("setvar")) {
                    if (++i == arg.length) {
                        throw bcl;
                    }

                    varSettings = arg[i];
                } else if (parameter.equals("sql")) {
                    noinput = true;    // but turn back on if file "-" specd.

                    if (++i == arg.length) {
                        throw bcl;
                    }

                    sqlText = arg[i];
                } else if (parameter.equals("debug")) {
                    debug = true;
                } else if (parameter.equals("noautofile")) {
                    noautoFile = true;
                } else if (parameter.equals("autocommit")) {
                    autoCommit = true;
                } else if (parameter.equals("stdinput")) {
                    noinput          = false;
                    stdinputOverride = Boolean.TRUE;
                } else if (parameter.equals("noinput")) {
                    noinput          = true;
                    stdinputOverride = Boolean.FALSE;
                } else if (parameter.equals("driver")) {
                    if (++i == arg.length) {
                        throw bcl;
                    }

                    driver = arg[i];
                } else if (parameter.equals("inlinerc")) {
                    if (++i == arg.length) {
                        throw bcl;
                    }

                    rcParams = arg[i];
                } else {
                    throw bcl;
                }
            }

            if (!listMode) {

                // If an inline RC file was specified, don't worry about the targetDb
                if (rcParams == null) {
                    if (++i == arg.length) {
                        throw bcl;
                    }

                    targetDb = arg[i];
                }
            }

            int scriptIndex = 0;

            if (sqlText != null) {
                try {
                    tmpFile = File.createTempFile("sqltool-", ".sql");

                    //(new java.io.FileWriter(tmpFile)).write(sqlText);
                    java.io.FileWriter fw = new java.io.FileWriter(tmpFile);

                    fw.write("/* " + (new java.util.Date()) + ".  "
                             + SqlTool.class.getName()
                             + " command-line SQL. */" + LS + LS);
                    fw.write(sqlText + LS);
                    fw.flush();
                    fw.close();
                } catch (IOException ioe) {
                    throw new SqlToolException(IOERR_EXITVAL,
                            "Failed to write given sql to temp file: " + ioe);
                }
            }

            if (stdinputOverride != null) {
                noinput = !stdinputOverride.booleanValue();
            }

            interactive = (!noinput) && (arg.length <= i + 1);

            if (arg.length == i + 2 && arg[i + 1].equals("-")) {
                if (stdinputOverride == null) {
                    noinput = false;
                }
            } else if (arg.length > i + 1) {

                // I.e., if there are any SQL files specified.
                scriptFiles =
                    new File[arg.length - i - 1 + ((stdinputOverride == null ||!stdinputOverride.booleanValue()) ? 0
                                                                                                                 : 1)];

                if (debug) {
                    System.err.println("scriptFiles has "
                                       + scriptFiles.length + " elements");
                }

                while (i + 1 < arg.length) {
                    scriptFiles[scriptIndex++] = new File(arg[++i]);
                }

                if (stdinputOverride != null
                        && stdinputOverride.booleanValue()) {
                    scriptFiles[scriptIndex++] = null;
                    noinput                    = true;
                }
            }
        } catch (BadCmdline bcl) {
            throw new SqlToolException(SYNTAXERR_EXITVAL, SYNTAX_MESSAGE);
        }

        RCData conData = null;

        // Use the inline RC file if it was specified
        if (rcParams != null) {
            rcFields = new HashMap();

            try {
                varParser(rcParams, rcFields, true);
            } catch (PrivateException e) {
                throw new SqlToolException(SYNTAXERR_EXITVAL, e.getMessage());
            }

            rcUrl        = (String) rcFields.get("url");
            rcUsername   = (String) rcFields.get("user");
            rcDriver     = (String) rcFields.get("driver");
            rcCharset    = (String) rcFields.get("charset");
            rcTruststore = (String) rcFields.get("truststore");

            // Don't ask for password if what we have already is invalid!
            if (rcUrl == null || rcUrl.length() < 1)
                throw new SqlToolException(RCERR_EXITVAL,
                        "URL element is required for inline RC arg");
            if (rcUsername == null || rcUsername.length() < 1)
                throw new SqlToolException(RCERR_EXITVAL,
                        "USER element is required for inline RC arg");

            try {
                rcPassword   = promptForPassword(rcUsername);
            } catch (PrivateException e) {
                throw new SqlToolException(INPUTERR_EXITVAL,
                        "Bad password: " + e.getMessage());
            }
            try {
                conData = new RCData(CMDLINE_ID, rcUrl, rcUsername,
                                     rcPassword, rcDriver, rcCharset,
                                     rcTruststore);
            } catch (Exception e) {
                throw new SqlToolException(RCERR_EXITVAL,
                        "Failed to generate RCData from given values: "
                        + e.getMessage());
            }
        } else {
            try {
                conData = new RCData(new File((rcFile == null)
                                              ? DEFAULT_RCFILE
                                              : rcFile), targetDb);
            } catch (Exception e) {
                throw new SqlToolException(RCERR_EXITVAL,
                        "Failed to retrieve connection info for database '"
                        + targetDb + "': " + e.getMessage());
            }
        }

        if (listMode) {
            return;
        }

        if (debug) {
            conData.report();
        }

        try {
            conn = conData.getConnection(
                driver, System.getProperty("sqlfile.charset"),
                System.getProperty("javax.net.ssl.trustStore"));

            conn.setAutoCommit(autoCommit);

            DatabaseMetaData md = null;

            if (interactive && (md = conn.getMetaData()) != null) {
                System.out.println("JDBC Connection established to a "
                                   + md.getDatabaseProductName() + " v. "
                                   + md.getDatabaseProductVersion()
                                   + " database as '" + md.getUserName()
                                   + "'.");
            }
        } catch (Exception e) {
            //e.printStackTrace();

            // Let's not continue as if nothing is wrong.
            throw new SqlToolException(CONNECTERR_EXITVAL,
                     "Failed to get a connection to " + conData.url + " as "
                     + conData.username + ".  " + e.getMessage());
        }

        File[] emptyFileArray      = {};
        File[] singleNullFileArray = { null };
        File   autoFile            = null;

        if (interactive &&!noautoFile) {
            autoFile = new File(System.getProperty("user.home")
                                + "/auto.sql");

            if ((!autoFile.isFile()) ||!autoFile.canRead()) {
                autoFile = null;
            }
        }

        if (scriptFiles == null) {

            // I.e., if no SQL files given on command-line.
            // Input file list is either nothing or {null} to read stdin.
            scriptFiles = (noinput ? emptyFileArray
                                   : singleNullFileArray);
        }

        int numFiles = scriptFiles.length;

        if (tmpFile != null) {
            numFiles += 1;
        }

        if (autoFile != null) {
            numFiles += 1;
        }

        SqlFile[] sqlFiles = new SqlFile[numFiles];
        Map   userVars = new HashMap();

        if (varSettings != null) try {
            varParser(varSettings, userVars, false);
        } catch (PrivateException pe) {
            throw new SqlToolException(RCERR_EXITVAL, pe.getMessage());
        }

        // We print version before execing this one.
        int interactiveFileIndex = -1;

        try {
            int fileIndex = 0;

            if (autoFile != null) {
                sqlFiles[fileIndex++] = new SqlFile(autoFile, false,
                                                    userVars);
            }

            if (tmpFile != null) {
                sqlFiles[fileIndex++] = new SqlFile(tmpFile, false, userVars);
            }

            for (int j = 0; j < scriptFiles.length; j++) {
                if (interactiveFileIndex < 0 && interactive) {
                    interactiveFileIndex = fileIndex;
                }

                sqlFiles[fileIndex++] = new SqlFile(scriptFiles[j],
                                                    interactive, userVars);
            }
        } catch (IOException ioe) {
            try {
                conn.close();
            } catch (Exception e) {}

            throw new SqlToolException(FILEERR_EXITVAL, ioe.getMessage());
        }

        try {
            for (int j = 0; j < sqlFiles.length; j++) {
                if (j == interactiveFileIndex) {
                    System.out.print("SqlTool v. " + revnum
                                     + ".                        ");
                }

                sqlFiles[j].execute(conn, coeOverride);
            }
            // Following two Exception types are handled properly inside of
            // SqlFile.  We just need to return an appropriate error status.
        } catch (SqlToolError ste) {
            throw new SqlToolException(SQLTOOLERR_EXITVAL);
        } catch (SQLException se) {
            // SqlTool will only throw an SQLException if it is in
            // "\c false" mode.
            throw new SqlToolException(SQLERR_EXITVAL);
        } finally {
            try {
                conn.close();
            } catch (Exception e) {}
        }

        // Taking file removal out of final block because this is good debug
        // info to keep around if the program aborts.
        if (tmpFile != null && !tmpFile.delete()) {
            System.err.println(
                "Error occurred while trying to remove temp file '" + tmpFile
                + "'");
        }
    }
}
