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

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.Log;
import com.larvalabs.svgandroid.SVG;
import com.larvalabs.svgandroid.SVGParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/** Factory to create drawables from SVG source and optimize the process. */
public class SVGFactory {

    private static final Map<String, Drawable> cache = new HashMap<String, Drawable>();

    public static Drawable getDrawable(String name, String svgSource) {
        if (cache.get(name) != null) {
            return cache.get(name);
        } else {
            SVG svg = SVGParser.getSVGFromString(svgSource);
            Drawable d = svg.createPictureDrawable();
            cache.put(name, d);
            return d;
        }
    }

    public static Drawable getDrawable(String name, int resId, Context ctx) {
        if (cache.get(name) != null) {
            return cache.get(name);
        }
        try {
            InputStream stream = ctx.getResources().openRawResource(resId);
            InputStreamReader reader = new InputStreamReader(stream);
            StringWriter writer = new StringWriter();
            char[] buffer = new char[1024];
            int read = reader.read(buffer, 0, 1024);
            while (read != -1) {
                writer.write(buffer, 0, read);
                read = reader.read(buffer, 0, 1024);
            }
            String source = writer.toString();
            writer.close();
            reader.close();
            return getDrawable(name, source);
        } catch (IOException e) {
            Log.e("Tryton", "Resource error on " + name, e);
            return null;
        }
    }
}