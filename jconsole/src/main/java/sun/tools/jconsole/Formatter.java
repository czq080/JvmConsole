/*
 * %W% %E%
 *
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package sun.tools.jconsole;

import java.text.DateFormat;
import java.text.DecimalFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;

import static sun.tools.jconsole.Resources.getText;

class Formatter {
    final static long SECOND = 1000;
    final static long MINUTE = 60 * SECOND;
    final static long HOUR = 60 * MINUTE;
    final static long DAY = 24 * HOUR;

    final static String cr = System.getProperty("line.separator");

    final static DateFormat timeDF = new SimpleDateFormat("HH:mm");
    private final static DateFormat timeWithSecondsDF = new SimpleDateFormat("HH:mm:ss");
    private final static DateFormat dateDF = new SimpleDateFormat("yyyy-MM-dd");
    private final static String decimalZero =
            new DecimalFormatSymbols().getDecimalSeparator() + "0";

    static String formatTime(long t) {
        String str;
        if (t < 1 * MINUTE) {
            String seconds = String.format("%.3f", t / (double) SECOND);
            str = Resources.getText("DurationSeconds", seconds);
        } else {
            long remaining = t;
            long days = remaining / DAY;
            remaining %= 1 * DAY;
            long hours = remaining / HOUR;
            remaining %= 1 * HOUR;
            long minutes = remaining / MINUTE;

            if (t >= 1 * DAY) {
                str = Resources.getText("DurationDaysHoursMinutes",
                        days, hours, minutes);
            } else if (t >= 1 * HOUR) {
                str = Resources.getText("DurationHoursMinutes",
                        hours, minutes);
            } else {
                str = Resources.getText("DurationMinutes", minutes);
            }
        }
        return str;
    }

    static String formatNanoTime(long t) {
        long ms = t / 1000000;
        return formatTime(ms);
    }


    static String formatClockTime(long time) {
        return timeDF.format(time);
    }

    static String formatDate(long time) {
        return dateDF.format(time);
    }

    static String formatDateTime(long time) {
        return dateDF.format(time) + " " + timeWithSecondsDF.format(time);
    }

    static DateFormat getDateTimeFormat(String key) {
        String dtfStr = getText(key);
        int dateStyle = -1;
        int timeStyle = -1;

        if (dtfStr.startsWith("SHORT")) {
            dateStyle = DateFormat.SHORT;
        } else if (dtfStr.startsWith("MEDIUM")) {
            dateStyle = DateFormat.MEDIUM;
        } else if (dtfStr.startsWith("LONG")) {
            dateStyle = DateFormat.LONG;
        } else if (dtfStr.startsWith("FULL")) {
            dateStyle = DateFormat.FULL;
        }

        if (dtfStr.endsWith("SHORT")) {
            timeStyle = DateFormat.SHORT;
        } else if (dtfStr.endsWith("MEDIUM")) {
            timeStyle = DateFormat.MEDIUM;
        } else if (dtfStr.endsWith("LONG")) {
            timeStyle = DateFormat.LONG;
        } else if (dtfStr.endsWith("FULL")) {
            timeStyle = DateFormat.FULL;
        }

        if (dateStyle != -1 && timeStyle != -1) {
            return DateFormat.getDateTimeInstance(dateStyle, timeStyle);
        } else if (dtfStr.length() > 0) {
            return new SimpleDateFormat(dtfStr);
        } else {
            return DateFormat.getDateTimeInstance();
        }
    }

    static double toExcelTime(long time) {
        // Excel is bug compatible with Lotus 1-2-3 and pretends
        // that 1900 was a leap year, so count from 1899-12-30.
        // Note that the month index is zero-based in Calendar.
        Calendar cal = new GregorianCalendar(1899, 11, 30);

        // Adjust for the fact that now may be DST but then wasn't
        Calendar tmpCal = new GregorianCalendar();
        tmpCal.setTimeInMillis(time);
        int dst = tmpCal.get(Calendar.DST_OFFSET);
        if (dst > 0) {
            cal.set(Calendar.DST_OFFSET, dst);
        }

        long millisSince1900 = time - cal.getTimeInMillis();
        double value = (double) millisSince1900 / (24 * 60 * 60 * 1000);

        return value;
    }


    static String[] formatKByteStrings(long... bytes) {
        int n = bytes.length;
        for (int i = 0; i < n; i++) {
            if (bytes[i] > 0) {
                bytes[i] /= 1024;
            }
        }
        String[] strings = formatLongs(bytes);
        for (int i = 0; i < n; i++) {
            strings[i] = getText("kbytes", strings[i]);
        }
        return strings;
    }

    static String formatKBytes(long bytes) {
        if (bytes == -1) {
            return getText("kbytes", "-1");
        }

        long kb = bytes / 1024;
        return getText("kbytes", justify(kb, 10));
    }


    static String formatBytes(long v, boolean html) {
        return formatBytes(v, v, html);
    }

    static String formatBytes(long v, long vMax) {
        return formatBytes(v, vMax, false);
    }

    static String formatBytes(long v, long vMax, boolean html) {
        String s;

        int exp = (int) Math.log10((double) vMax);

        if (exp < 3) {
            s = Resources.getText("Size Bytes", v);
        } else if (exp < 6) {
            s = Resources.getText("Size Kb", trimDouble(v / Math.pow(10.0, 3)));
        } else if (exp < 9) {
            s = Resources.getText("Size Mb", trimDouble(v / Math.pow(10.0, 6)));
        } else {
            s = Resources.getText("Size Gb", trimDouble(v / Math.pow(10.0, 9)));
        }
        if (html) {
            s = s.replace(" ", "&nbsp;");
        }
        return s;
    }

    /*
     * Return the input value rounded to one decimal place.  If after
     * rounding the string ends in the (locale-specific) decimal point
     * followed by a zero then trim that off as well.
     */
    private static String trimDouble(double d) {
        String s = String.format("%.1f", d);
        if (s.length() > 3 && s.endsWith(decimalZero)) {
            s = s.substring(0, s.length() - 2);
        }
        return s;
    }

    static String formatLong(long value) {
        return String.format("%,d", value);
    }

    static String[] formatLongs(long... longs) {
        int n = longs.length;
        int size = 0;
        String[] strings = new String[n];
        for (int i = 0; i < n; i++) {
            strings[i] = formatLong(longs[i]);
            size = Math.max(size, strings[i].length());
        }
        for (int i = 0; i < n; i++) {
            strings[i] = justify(strings[i], size);
        }
        return strings;
    }


    // A poor attempt at right-justifying for numerical data
    static String justify(long value, int size) {
        return justify(formatLong(value), size);
    }

    static String justify(String str, int size) {
        StringBuffer buf = new StringBuffer();
        buf.append("<TT>");
        int n = size - str.length();
        for (int i = 0; i < n; i++) {
            buf.append("&nbsp;");
        }
        buf.append(str);
        buf.append("</TT>");
        return buf.toString();
    }

    static String newRow(String label, String value) {
        return newRow(label, value, 2);
    }

    static String newRow(String label, String value, int columnPerRow) {
        if (label == null) {
            label = "";
        } else {
            label += ":&nbsp;";
        }
        label = "<th nowrap align=right valign=top>" + label;
        value = "<td colspan=" + (columnPerRow - 1) + "> <font size =-1>" + value;

        return "<tr>" + label + value + "</tr>";
    }

    static String newRow(String label1, String value1,
                         String label2, String value2) {
        label1 = "<th nowrap align=right valign=top>" + label1 + ":&nbsp;";
        value1 = "<td><font size =-1>" + value1;
        label2 = "<th nowrap align=right valign=top>" + label2 + ":&nbsp;";
        value2 = "<td><font size =-1>" + value2;

        return "<tr>" + label1 + value1 + label2 + value2 + "</tr>";
    }

}
