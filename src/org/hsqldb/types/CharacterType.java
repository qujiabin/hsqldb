/* Copyright (c) 2001-2009, The HSQL Development Group
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


package org.hsqldb.types;

import java.math.BigDecimal;

import org.hsqldb.Collation;
import org.hsqldb.Error;
import org.hsqldb.ErrorCode;
import org.hsqldb.HsqlException;
import org.hsqldb.OpTypes;
import org.hsqldb.Session;
import org.hsqldb.SessionInterface;
import org.hsqldb.Tokens;
import org.hsqldb.Types;
import org.hsqldb.lib.StringConverter;
import org.hsqldb.lib.StringUtil;
import org.hsqldb.lib.java.JavaSystem;
import org.hsqldb.store.ValuePool;

/**
 * Type subclass for CHARACTER, VARCHAR, etc.<p>
 *
 * @author Fred Toussi (fredt@users dot sourceforge.net)
 * @version 1.9.0
 * @since 1.9.0
 */
public class CharacterType extends Type {

    Collation        collation;
    boolean          isEqualIdentical;
    final static int sqlDefaultCharPrecision = 1;

    public CharacterType(Collation collation, int type, long precision) {

        super(Types.SQL_VARCHAR, type, precision, 0);

        this.collation = collation;
        isEqualIdentical = this.collation.isEqualAlwaysIdentical()
                           && type != Types.VARCHAR_IGNORECASE;
    }

    /**
     * Always English collation
     */
    public CharacterType(int type, long precision) {

        super(Types.SQL_VARCHAR, type, precision, 0);

        this.collation   = Collation.getDefaultInstance();
        isEqualIdentical = type != Types.VARCHAR_IGNORECASE;
    }

    public int displaySize() {
        return precision > Integer.MAX_VALUE ? Integer.MAX_VALUE
                                             : (int) precision;
    }

    public int getJDBCTypeCode() {

        switch (typeCode) {

            case Types.SQL_CHAR :
                return Types.CHAR;

            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE :
                return Types.VARCHAR;

            case Types.SQL_CLOB :
                return Types.CLOB;

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public String getJDBCClassName() {
        return "java.lang.String";
    }

    public int getSQLGenericTypeCode() {
        return typeCode == Types.SQL_CHAR ? typeCode
                                          : Types.SQL_VARCHAR;
    }

    public String getNameString() {

        switch (typeCode) {

            case Types.SQL_CHAR :
                return Tokens.T_CHARACTER;

            case Types.SQL_VARCHAR :
                return Tokens.T_VARCHAR;

            case Types.VARCHAR_IGNORECASE :
                return Tokens.T_VARCHAR_IGNORECASE;

            case Types.SQL_CLOB :
                return Tokens.T_CLOB;

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public String getFullNameString() {

        switch (typeCode) {

            case Types.SQL_CHAR :
                return Tokens.T_CHARACTER;

            case Types.SQL_VARCHAR :
                return "CHARACTER VARYING";

            case Types.VARCHAR_IGNORECASE :
                return Tokens.T_VARCHAR_IGNORECASE;

            case Types.SQL_CLOB :
                return "CHARACTER LARGE OBJECT";

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public String getDefinition() {

        if (precision == 0) {
            return getNameString();
        }

        StringBuffer sb = new StringBuffer(16);

        sb.append(getNameString());
        sb.append('(');
        sb.append(precision);
        sb.append(')');

        return sb.toString();
    }

    public boolean isCharacterType() {
        return true;
    }

    public boolean acceptsPrecision() {
        return true;
    }

    public boolean requiresPrecision() {
        return typeCode == Types.SQL_VARCHAR
               || typeCode == Types.VARCHAR_IGNORECASE;
    }

    public int precedenceDegree(Type other) {

        if (other.typeCode == typeCode) {
            return 0;
        }

        if (!other.isCharacterType()) {
            return Integer.MIN_VALUE;
        }

        switch (typeCode) {

            case Types.SQL_CHAR :
                return other.typeCode == Types.SQL_CLOB ? 4
                                                        : 2;

            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE :
                return other.typeCode == Types.SQL_CLOB ? 4
                                                        : -2;

            case Types.SQL_CLOB :
                return other.typeCode == Types.SQL_CHAR ? -4
                                                        : -2;

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public Type getAggregateType(Type other) throws HsqlException {

        if (typeCode == other.typeCode) {
            return precision >= other.precision ? this
                                                : other;
        }

        switch (other.typeCode) {

            case Types.SQL_ALL_TYPES :
                return this;

            case Types.SQL_CHAR :
                return precision >= other.precision ? this
                                                    : getCharacterType(
                                                    typeCode, other.precision);

            case Types.SQL_VARCHAR :
                if (typeCode == Types.SQL_CLOB
                        || typeCode == Types.VARCHAR_IGNORECASE) {
                    return precision >= other.precision ? this
                                                        : getCharacterType(
                                                        typeCode,
                                                        other.precision);
                } else {
                    return other.precision >= precision ? other
                                                        : getCharacterType(
                                                        other.typeCode,
                                                        precision);
                }
            case Types.VARCHAR_IGNORECASE :
                if (typeCode == Types.SQL_CLOB) {
                    return precision >= other.precision ? this
                                                        : getCharacterType(
                                                        typeCode,
                                                        other.precision);
                } else {
                    return other.precision >= precision ? other
                                                        : getCharacterType(
                                                        other.typeCode,
                                                        precision);
                }
            case Types.SQL_CLOB :
                return other.precision >= precision ? other
                                                    : getCharacterType(
                                                    other.typeCode, precision);

            case Types.SQL_BIT :
            case Types.SQL_BIT_VARYING :
            case Types.SQL_BLOB :
            case Types.SQL_BINARY :
            case Types.SQL_VARBINARY :
            case Types.OTHER :
                throw Error.error(ErrorCode.X_42562);
            default :

                /**
                 * @todo - this seems to be allowed in SQL-92 (is in NIST)
                 * but is disallowed in SQL:2003
                 * need to make dependent on a database property
                 */
/*
                int length = other.displaySize();

                return getCharacterType(Types.SQL_VARCHAR,
                                        length).getAggregateType(this);
*/
                throw Error.error(ErrorCode.X_42562);
        }
    }

    /**
     * For concatenation
     */
    public Type getCombinedType(Type other,
                                int operation) throws HsqlException {

        if (operation != OpTypes.CONCAT) {
            return getAggregateType(other);
        }

        Type newType;

        switch (other.typeCode) {

            case Types.SQL_ALL_TYPES :
                return this;

            case Types.SQL_CHAR :
                newType = this;
                break;

            case Types.SQL_VARCHAR :
                newType =
                    (typeCode == Types.SQL_CLOB || typeCode == Types
                        .VARCHAR_IGNORECASE) ? this
                                             : other;
                break;

            case Types.VARCHAR_IGNORECASE :
                newType = typeCode == Types.SQL_CLOB ? this
                                                     : other;
                break;

            case Types.SQL_CLOB :
                newType = other;
                break;

            default :
                throw Error.error(ErrorCode.X_42562);
        }

        return getCharacterType(newType.typeCode, precision + other.precision);
    }

    public int compare(Object a, Object b) {

        if (a == b) {
            return 0;
        }

        if (a == null) {
            return -1;
        }

        if (b == null) {
            return 1;
        }

        String as          = (String) a;
        String bs          = (String) b;
        int    la          = as.length();
        int    lb          = bs.length();
        int    shortLength = la > lb ? lb
                                     : la;
        int    result;

        if (typeCode == Types.VARCHAR_IGNORECASE) {
            result = collation.compareIgnoreCase(as.substring(0, shortLength),
                                                 bs.substring(0, shortLength));
        } else {
            result = collation.compare(as.substring(0, shortLength),
                                       bs.substring(0, shortLength));
        }

        if (la == lb || result != 0) {
            return result;
        }

        if (la > lb) {
            as = as.substring(shortLength, la);
            bs = ValuePool.getSpaces(la - shortLength);
        } else {
            as = ValuePool.getSpaces(lb - shortLength);
            bs = bs.substring(shortLength, lb);
        }

        if (typeCode == Types.VARCHAR_IGNORECASE) {
            return collation.compareIgnoreCase(as, bs);
        } else {
            return collation.compare(as, bs);
        }
    }

    public Object convertToTypeLimits(Object a) throws HsqlException {

        if (a == null) {
            return a;
        }

        if (precision == 0) {
            return a;
        }

        switch (typeCode) {

            case Types.SQL_CHAR : {
                int slen = ((String) a).length();

                if (slen == precision) {
                    return a;
                }

                if (slen > precision) {
                    if (getRightTrimSise((String) a, ' ') <= precision) {
                        return ((String) a).substring(0, (int) precision);
                    } else {
                        throw Error.error(ErrorCode.X_22001);
                    }
                }

                char[] b = new char[(int) precision];

                ((String) a).getChars(0, slen, b, 0);

                for (int i = slen; i < precision; i++) {
                    b[i] = ' ';
                }

                return new String(b);
            }
            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE : {
                int slen = ((String) a).length();

                if (slen > precision) {
                    if (getRightTrimSise((String) a, ' ') <= precision) {
                        return ((String) a).substring(0, (int) precision);
                    } else {
                        throw Error.error(ErrorCode.X_22001);
                    }
                }

                return a;
            }
            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public Object castToType(SessionInterface session, Object a,
                             Type otherType) throws HsqlException {

        if (a == null) {
            return a;
        }

        switch (otherType.typeCode) {

            case Types.SQL_CHAR :
            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE :
                if (precision != 0 && ((String) a).length() > precision) {
                    if (StringUtil.rightTrimSize((String) a) > precision) {
                        session.addWarning(Error.error(ErrorCode.W_01004));
                    }

                    return ((String) a).substring(0, (int) precision);
                }

                return convertToTypeLimits(a);

            case Types.SQL_CLOB : {
                long length = ((ClobData) a).length();

                if (precision != 0 && length > precision) {
                    if (((ClobData) a).nonSpaceLength() > precision) {
                        session.addWarning(Error.error(ErrorCode.W_01004));
                    }

                    return ((ClobData) a).getSubString(0, (int) precision);
                }

                return convertToTypeLimits(a);
            }
            case Types.SQL_BLOB :
            case Types.SQL_BINARY :
            case Types.SQL_VARBINARY : {
                throw Error.error(ErrorCode.X_42561);
            }
            default :
        }

        String s = otherType.convertToString(a);

        return convertToTypeLimits(s);
    }

    public Object convertToType(SessionInterface session, Object a,
                                Type otherType) throws HsqlException {

        if (a == null) {
            return a;
        }

        switch (otherType.typeCode) {

            case Types.SQL_CHAR :
            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE :
                return convertToTypeLimits(a);

            case Types.SQL_CLOB : {
                ClobData c = (ClobData) a;

                if (typeCode == Types.SQL_CLOB) {
                    return convertToTypeLimits(a);
                }

                if (precision != 0 && c.nonSpaceLength() > precision) {
                    throw Error.error(ErrorCode.X_22001);
                }

                String s = c.getSubString(0, (int) precision);

                return convertToTypeLimits(s);
            }

            case Types.SQL_BLOB : {
                throw Error.error(ErrorCode.X_42561);
            }

            default : {
                String s = otherType.convertToString(a);

                return convertToTypeLimits(s);
            }
        }
    }

    public Object convertToTypeJDBC(SessionInterface session, Object a,
                                    Type otherType) throws HsqlException {

        if (a == null) {
            return a;
        }

        return convertToType(session, a, otherType);
    }

    public Object convertToDefaultType(SessionInterface session,
                                       Object a) throws HsqlException {

        if (a == null) {
            return a;
        }

        if (a instanceof Boolean) {
            return convertToType(session, a, Type.SQL_BOOLEAN);
        } else if (a instanceof BigDecimal) {
            a = JavaSystem.toString((BigDecimal) a);

            return convertToType(session, a, Type.SQL_VARCHAR);
        } else if (a instanceof Number) {
            a = a.toString();    // use shortcut

            return convertToType(session, a, Type.SQL_VARCHAR);
        } else if (a instanceof String) {
            return convertToType(session, a, Type.SQL_VARCHAR);
        } else if (a instanceof java.sql.Date) {
            String s = ((java.sql.Date) a).toString();

            return convertToType(session, s, Type.SQL_VARCHAR);
        } else if (a instanceof java.sql.Time) {
            String s = ((java.sql.Time) a).toString();

            return convertToType(session, s, Type.SQL_VARCHAR);
        } else if (a instanceof java.sql.Timestamp) {
            String s = ((java.sql.Timestamp) a).toString();

            return convertToType(session, s, Type.SQL_VARCHAR);
        } else {
            throw Error.error(ErrorCode.X_42561);
        }
    }

    public String convertToString(Object a) {

        if (a == null) {
            return null;
        }

        switch (typeCode) {

            case Types.SQL_CHAR : {
                int slen = ((String) a).length();

                if (precision == 0 || slen == precision) {
                    return (String) a;
                }

                char[] b = new char[(int) precision];

                ((String) a).getChars(0, slen, b, 0);

                for (int i = slen; i < precision; i++) {
                    b[i] = ' ';
                }

                return new String(b);
            }
            case Types.SQL_VARCHAR :
            case Types.VARCHAR_IGNORECASE : {
                return (String) a;
            }
            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public String convertToSQLString(Object a) {

        if (a == null) {
            return "NULL";
        }

        String s = convertToString(a);

        return StringConverter.toQuotedString(s, '\'', true);
    }

    public boolean canConvertFrom(Type otherType) {
        return otherType.typeCode == Types.SQL_ALL_TYPES
               || (!otherType.isBinaryType() && !otherType.isObjectType());
    }

    public boolean isEqualIdentical() {
        return isEqualIdentical;
    }

    public boolean isCaseInsensitive() {
        return typeCode == Types.VARCHAR_IGNORECASE;
    }

    public long position(Object data, Object otherData, Type otherType,
                         long offset) throws HsqlException {

        if (data == null || otherData == null) {
            return -1L;
        }

        if (otherType.typeCode == Types.SQL_CLOB) {
            long otherLength = ((ClobData) data).length();

            if (offset + otherLength > ((String) data).length()) {
                return -1;
            }

            String otherString = ((ClobData) otherData).getSubString(0,
                (int) otherLength);

            return ((String) data).indexOf(otherString, (int) offset);
        } else if (otherType.isCharacterType()) {
            long otherLength = ((String) data).length();

            if (offset + otherLength > ((String) data).length()) {
                return -1;
            }

            return ((String) data).indexOf((String) otherData, (int) offset);
        } else {
            throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    public Object substring(SessionInterface session, Object data,
                            long offset, long length, boolean hasLength,
                            boolean trailing) throws HsqlException {

        long end;
        long dataLength = typeCode == Types.SQL_CLOB
                          ? ((ClobData) data).length()
                          : ((String) data).length();

        if (trailing) {
            end = dataLength;

            if (length > dataLength) {
                offset = 0;
                length = dataLength;
            } else {
                offset = dataLength - length;
            }
        } else if (hasLength) {
            end = offset + length;
        } else {
            end = dataLength > offset ? dataLength
                                      : offset;
        }

        if (end < offset) {
            throw Error.error(ErrorCode.X_22011);
        }

        if (offset > end || end < 0) {

            // return zero length data
            offset = 0;
            end    = 0;
        }

        if (offset < 0) {
            offset = 0;
        }

        if (end > dataLength) {
            end = dataLength;
        }

        length = end - offset;

        if (data instanceof String) {
            return ((String) data).substring((int) offset,
                                             (int) (offset + length));
        } else if (data instanceof ClobData) {
            ClobData clob = session.createClob();

            // todo - change to support long strings
            String result = ((ClobData) data).getSubString(offset,
                (int) length);

            clob.setString(0, result);

            return clob;
        } else {
            throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }

    /**
     * Memory limits apply to Upper and Lower implementations with Clob data
     */
    public Object upper(Session session, Object data) throws HsqlException {

        if (data == null) {
            return null;
        }

        if (typeCode == Types.SQL_CLOB) {
            String result = ((ClobData) data).getSubString(0,
                (int) ((ClobData) data).length());

            result = collation.toUpperCase(result);

            ClobData clob = session.createClob();

            clob.setString(0, result);

            return clob;
        }

        return collation.toUpperCase((String) data);
    }

    public Object lower(Session session, Object data) throws HsqlException {

        if (data == null) {
            return null;
        }

        if (typeCode == Types.SQL_CLOB) {
            String result = ((ClobData) data).getSubString(0,
                (int) ((ClobData) data).length());

            result = collation.toLowerCase(result);

            ClobData clob = session.createClob();

            clob.setString(0, result);

            return clob;
        }

        return collation.toLowerCase((String) data);
    }

    public Object trim(SessionInterface session, Object data, int trim,
                       boolean leading,
                       boolean trailing) throws HsqlException {

        if (data == null) {
            return null;
        }

        String s;

        if (typeCode == Types.SQL_CLOB) {
            s = ((ClobData) data).getSubString(
                0, (int) ((ClobData) data).length());
        } else {
            s = (String) data;
        }

        int endindex = s.length();

        if (trailing) {
            for (--endindex; endindex >= 0 && s.charAt(endindex) == trim;
                    endindex--) {}

            endindex++;
        }

        int startindex = 0;

        if (leading) {
            while (startindex < endindex && s.charAt(startindex) == trim) {
                startindex++;
            }
        }

        // todo - change to support long strings
        if (startindex == 0 && endindex == s.length()) {}
        else {
            s = s.substring(startindex, endindex);
        }

        if (typeCode == Types.SQL_CLOB) {
            ClobData clob = session.createClob();

            clob.setString(0, s);

            return clob;
        } else {
            return s;
        }
    }

    public Object overlay(SessionInterface session, Object data,
                          Object overlay, long offset, long length,
                          boolean hasLength) throws HsqlException {

        if (data == null || overlay == null) {
            return null;
        }

        if (!hasLength) {
            length = typeCode == Types.SQL_CLOB ? ((ClobData) overlay).length()
                                                : ((String) overlay).length();
        }

        Object temp = concat(null,
                             substring(session, data, 0, offset, true, false),
                             overlay);

        return concat(null, temp,
                      substring(session, data, offset + length, 0, false,
                                false));
    }

    public Object concat(Session session, Object a,
                         Object b) throws HsqlException {

        if (a == null || b == null) {
            return null;
        }

        String left;
        String right;

        if (a instanceof ClobData) {
            left = ((ClobData) a).getSubString(
                0, (int) ((ClobData) a).length());
        } else {
            left = (String) a;
        }

        if (b instanceof ClobData) {
            right =
                ((ClobData) b).getSubString(0, (int) ((ClobData) b).length());
        } else {
            right = (String) b;
        }

        if (typeCode == Types.SQL_CLOB) {
            ClobData clob = session.createClob();

            clob.setString(0, left);
            clob.setString(left.length(), right);

            return clob;
        } else {
            return left + right;
        }
    }

    public long size(Object data) throws HsqlException {

        if (typeCode == Types.SQL_CLOB) {
            return ((ClobData) data).length();
        }

        return ((String) data).length();
    }

/*
    public static Object concat(Object a, Object b) {

        if (a == null || b == null) {
            return null;
        }

        return a.toString() + b.toString();
    }
*/
    public static int getRightTrimSise(String s, char trim) {

        int endindex = s.length();

        for (--endindex; endindex >= 0 && s.charAt(endindex) == trim;
                endindex--) {}

        endindex++;

        return endindex;
    }

    public static Type getCharacterType(int type, long precision) {

        switch (type) {

            case Types.SQL_VARCHAR :
            case Types.SQL_CHAR :
            case Types.VARCHAR_IGNORECASE :
                return new CharacterType(type, (int) precision);

            case Types.SQL_CLOB :
                return new ClobType(precision);

            default :
                throw Error.runtimeError(ErrorCode.U_S0500, "CharacterType");
        }
    }
}
