/*
    Tryton Android
    Copyright (C) 2012 SARL SCOP Scil (contact@scil.coop)

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.tryton.client.tools;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.text.ParsePosition;

/** Tool to format some data to string */
public class Formatter {

    public static String formatDate(String format,
                                    int year, int month, int day) {
        String result;
        if (format != null) {
            result = format;
            result = result.replace("%d", String.format("%02d", day));
            result = result.replace("%m", String.format("%02d", month));
            result = result.replace("%Y", String.format("%04d", year));
        } else {
            result = String.format("%04d/%02d/%02d", year, month, day);
        }
        return result;
    }

    public static String formatDecimal(char decimalPoint,
                                       char thousandsSeparator,
                                       int intNum, int decNum,
                                       double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(decimalPoint);
        symbols.setGroupingSeparator(thousandsSeparator);
        // Note: there is currently no way to restrict integer numbers
        // but it is capped by value from the server
        String format = "###,##0.###";
        if (decNum != -1) {
            format = "###,##0";
            format += ".";
            for (int i = 0; i < decNum; i++) {
                format += "0";
            }
        }
        DecimalFormat formatter = new DecimalFormat(format, symbols);
        return formatter.format(value);
    }

    public static Number unformatDecimal(char decimalPoint,
                                         char thousandsSeparator,
                                         String value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols();
        symbols.setDecimalSeparator(decimalPoint);
        symbols.setGroupingSeparator(thousandsSeparator);
        String format = "###,##0.###";
        DecimalFormat formatter = new DecimalFormat(format, symbols);
        return formatter.parse(value, new ParsePosition(0));
    }
}