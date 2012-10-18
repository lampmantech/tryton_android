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
package org.tryton.client.data;

import android.content.Context;
import java.io.IOException;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.tryton.client.Configure;
import org.tryton.client.models.MenuEntry;

/** Saves and loads a menu in persistent storage for a limited amount of time */
public class MenuCache {

    private static final String CACHE_ID = "CACHE_CACHE_ID";
    public static final long NO_AGE = Long.MIN_VALUE;

    /** You can't instanciate this. It only uses static functions. */
    private MenuCache() {}

    public static void save(List<MenuEntry> menus, Context ctx)
        throws IOException {
        long time = System.currentTimeMillis();
        String db = Configure.getDatabaseCode(ctx);
        FileOutputStream fos = ctx.openFileOutput(CACHE_ID, ctx.MODE_PRIVATE);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(db);
        oos.writeLong(time);
        oos.writeObject(menus);
        oos.close();
    }

    /** Load the cached menu entries.
     * If the cache is not set it will return null
     * (beware of NullPointerExceptions)
     */
    public static List<MenuEntry> load(Context ctx)
        throws IOException {
        FileInputStream fis = ctx.openFileInput(CACHE_ID);
        ObjectInputStream ois = new ObjectInputStream(fis);
        List<MenuEntry> ret = null;
        try {
            String db = (String) ois.readObject();
            if (!db.equals(Configure.getDatabaseCode(ctx))) {
                // The record is not for the current database
                ois.close();
                return null;
            }
            ois.readLong();
            ret = (List) ois.readObject();
        } catch (ClassNotFoundException cnfe) {
            // Should never happen
        } catch (ClassCastException cce) {
            // Cache structure is obsolete or alterated
            // Ignore (will return null)
        }
        ois.close();
        return ret;
    }
    
    public static long cacheAge(Context ctx)
        throws IOException {
        FileInputStream fis = ctx.openFileInput(CACHE_ID);
        ObjectInputStream ois = new ObjectInputStream(fis);
        long age = NO_AGE;
        long cacheTime = 0;
        try {
            String db = (String) ois.readObject();
            if (!db.equals(Configure.getDatabaseCode(ctx))) {
                // The record is not for the current database
                ois.close();
                return age;
            }
            cacheTime = ois.readLong();
        } catch (EOFException e) {
            // The file is empty
            ois.close();
            return age;
        } catch (ClassNotFoundException cnfe) {
            // Should never happen
        } catch (ClassCastException cce) {
            // Cache structure is obsolete or alterated
            // Ignore (will return NO_AGE)
        }
        long time = System.currentTimeMillis();
        age = time - cacheTime;
        ois.close();
        return age;
    }
}