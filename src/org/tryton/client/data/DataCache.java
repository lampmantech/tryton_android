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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.tryton.client.models.Model;
import org.tryton.client.models.ModelViewTypes;
import org.tryton.client.models.RelField;

/** This class assumes the cache of data and is the only source
    for the application itself. When calling for data TrytonCall fills
    the cache and the requester then get the data from the cache.
    This allows fine memory management to avoid out of memory errors. */
public class DataCache extends SQLiteOpenHelper {

    private static final int CACHE_LIFETIME = 36000000; // 10 hours

    /** The database version to detect and do updates */
    private static final int DB_VERSION = 1;

    private static final String DATABASE_TABLE = "database";
    private static final String MODEL_TABLE = "models";
    private static final String COUNT_TABLE = "count";
    private static final String REL_TABLE = "relationnals";

    public DataCache (Context ctx) {
        super(ctx, "Tryton", null, DB_VERSION);
    }
    
    /** Database initialization script */
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + MODEL_TABLE + " ("
                   + "id INTEGER NOT NULL, "     // ID
                   + "className TEXT NOT NULL, " // Class name
                   + "writeTime INTEGER, "       // Date for cache age
                   + "name TEXT, "               // Shortcut for rec_name
                   + "data BLOB, "               // Binary data,
                                                 // null when relationnal
                   + "PRIMARY KEY (id, className))");
        db.execSQL("CREATE TABLE " + DATABASE_TABLE
                   + " (databasecode TEXT PRIMARY KEY)");
        db.execSQL("CREATE TABLE " + COUNT_TABLE
                   + " (className TEXT PRIMARY KEY, "
                   + " writeTime INTEGER, "
                   + " count INTEGER)");
        db.execSQL("CREATE TABLE " + REL_TABLE + " ("
                   + "className TEXT NOT NULL, "
                   + "field TEXT NOT NULL, "
                   + "writeTime INTEGER, "
                   + "type TEXT NOT NULL, "
                   + "relModel TEXT, "
                   + "PRIMARY KEY (className, field))");
    }

    /** Upgrade procedure from oldVersion (the one installed)
        to newVersion (DB_VERSION) */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Nothing to do, there is only one version
        return;
    }

    /** Check if the records present in database belongs to the given
     * host and database. */
    public boolean checkDatabase(String databaseCode) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(DATABASE_TABLE, new String[]{"databaseCode"},
                            null, null, null,
                            null, null, "1");
        if (c.moveToFirst()) {
            String dbHost = c.getString(0);
            c.close();
            db.close();
            return dbHost.equals(databaseCode);
        } else {
            // No entry, database is useable
            c.close();
            db.close();
            return true;
        }
    }

    /** Remove all entry from the database. */
    public void clear() {
        SQLiteDatabase db = this.getReadableDatabase();
        db.delete(MODEL_TABLE, null, null);
        db.delete(DATABASE_TABLE, null, null);
        db.delete(REL_TABLE, null, null);
        db.delete(COUNT_TABLE, null, null);
        db.close();
    }

    /** Bind the database to a database and host.
     * Should be called after a check and clear to
     * prevent from mixing data from multiple hosts. */
    public void setHost(String databaseCode) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("databaseCode", databaseCode);
        if (db.update(DATABASE_TABLE, v , null, null) == 0) {
            db.insert(DATABASE_TABLE, null, v);
        }
        db.close();
    }

    public int getDataCount(String className) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(COUNT_TABLE, new String[]{"count"},
                            "className = ?", new String[]{className},
                            null, null, null, "1");
        if (c.moveToNext()) {
            int count = c.getInt(0);
            c.close();
            db.close();
            return count;
        } else {
            c.close();
            db.close();
            return -1;
        }
    }

    /** Check if all data are present */
    public boolean isFullyLoaded(String className, boolean full) {
        int count = this.getDataCount(className);
        if (count == -1) {
            return false;
        }
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c;
        if (!full) {
            c = db.query(MODEL_TABLE, new String[]{"count(id)"},
                         "className = ?", new String[]{className},
                         null, null, null, null);
        } else {
            c = db.query(MODEL_TABLE, new String[]{"count(id)"},
                         "className = ? AND data NOT NULL",
                         new String[]{className}, null, null, null, null);
        }
        if (c.moveToNext()) {
            int loadedCount = c.getInt(0);
            c.close();
            db.close();
            return loadedCount == count;
        } else {
            c.close();
            db.close();
            return false;
        }
    }

    public void setDataCount(String className, int count) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("className", className);
        v.put("count", count);
        v.put("writeTime", System.currentTimeMillis());
        if (db.update(COUNT_TABLE, v, "className = ?",
                      new String[]{className}) == 0) {
            db.insert(COUNT_TABLE, null, v);
        }
        db.close();
    }

    /** Set datacount from records, assuming className is fully stored. */
    public void updateDataCount(String className) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(MODEL_TABLE, new String[]{"count(id)"},
                            "className = ?", new String[]{className},
                            null, null, null, null);
        int count = 0;
        if (c.moveToNext()) {
            count = c.getInt(0);
        }
        c.close();
        db.close();
        this.setDataCount(className, count);
    }

    /** Add one to the count of data (when creating a new record) */
    public void addOne(String className) {
        SQLiteDatabase db = this.getWritableDatabase();
        Cursor c = db.query(COUNT_TABLE, new String[]{"count"},
                            "className = ?", new String[]{className},
                            null, null, null, "1");
        if (c.moveToNext()) {
            int count = c.getInt(0);
            ContentValues v = new ContentValues();
            v.put("count", count + 1);
            db.update(COUNT_TABLE, v, "className = ?",
                      new String[]{className});
            c.close();
        } else {
            ContentValues v = new ContentValues();
            v.put("count", 1);
            db.insert(COUNT_TABLE, null, v);
            c.close();
        }
        db.close();
    }

    /** Store relationnal fields. Use null if there is no rel field on the
        model. */
    public void storeRelFields(String className, List<RelField> relations) {
        SQLiteDatabase db = this.getWritableDatabase();
        long time = System.currentTimeMillis();
        if (relations.size() == 0) {
            db.delete(REL_TABLE, "className = ?", new String[]{className});
            ContentValues v = new ContentValues();
            v.put("className", className);
            v.put("writeTime", time);
            v.putNull("field");
            v.putNull("type");
            v.putNull("relModel");
            db.insert(REL_TABLE, null, v);
        } else {
            for (int i = 0; i < relations.size(); i++) {
                RelField rf = relations.get(i);
                String field = rf.getFieldName();
                String type = rf.getType();
                String rel = rf.getRelModel();
                ContentValues v = new ContentValues();
                v.put("className", className);
                v.put("writeTime", time);
                v.put("field", field);
                v.put("type", type);
                v.put("relModel", rel);
                if (db.update(REL_TABLE, v, "className = ? AND field = ?",
                              new String[]{className, field}) == 0) {
                    db.insert(REL_TABLE, null, v);
                }
            }
        }
        db.close();
    }

    public List<RelField> getRelFields(String className) {
        SQLiteDatabase db = this.getReadableDatabase();
        List<RelField> ret = new ArrayList<RelField>();
        Cursor c = db.query(REL_TABLE, new String[]{"type", "relModel"},
                            "className = ?", new String[]{className},
                            null, null, null, null);
        boolean noFields = false;
        while (c.moveToNext()) {
            String type = c.getString(0);
            String relModel = c.getString(1);
            if (type != null) {
                RelField rel = new RelField(className, type, relModel);
                ret.add(rel);
            } else {
                noFields = true;
            }
        }
        c.close();
        db.close();
        if (ret.size() > 0) {
            if (noFields) {
                // Return an empty list
                return new ArrayList<RelField>();
            } else {
                return ret;
            }
        } else {
            return null;
        }
    }

    public RelField getRelField(String className, String fieldName) {
        SQLiteDatabase db = this.getReadableDatabase();
        RelField ret = null;
        Cursor c = db.query(REL_TABLE, new String[]{"type", "relModel"},
                            "className = ? AND field = ?",
                            new String[]{className, fieldName},
                            null, null, null, null);
        boolean noFields = false;
        if (c.moveToNext()) {
            String type = c.getString(0);
            String relModel = c.getString(1);
            ret = new RelField(className, type, relModel);
        }
        c.close();
        db.close();
        return ret;
    }

    /** Get full models from the result of a query. If the record is partial
     * it is ignored.
     * The query must set data as first field. */
    private List<Model> readModels(Cursor c, SQLiteDatabase db,
                                   String className) {
        List<Model> models = new ArrayList<Model>();
        while (c.moveToNext()) {
            if (c.isNull(0)) {
                continue;
            }
            byte[] data = c.getBlob(0);
            try {
                Model m = Model.fromByteArray(data);
                models.add(m);
                // Get relationnal fields
                long minTime = System.currentTimeMillis() - CACHE_LIFETIME;
                Cursor cf = db.query(REL_TABLE, new String[]{"field, relModel"},
                                     "className = ? AND type IN (?, ?) "
                                     + "AND writeTime > ?",
                                     new String[]{className,
                                                  "many2one", "one2one",
                                                  String.valueOf(minTime)},
                                     null, null, null, null);
                while (cf.moveToNext()) {
                    String field = cf.getString(0);
                    String relModel = cf.getString(1);
                    if (m.get(field) != null) {
                        int id = (Integer) m.get(field);
                        Model rel = this.getRelationnal(relModel, id, db);
                        m.set2One(field, rel);
                    }
                }
                cf.close();
            } catch (IOException e) {
                Log.e("Tryton", "Unable to read stored data", e);
            }
        }
        return models;
    }
   
    public List<Model> getData(String className, int offset, int count,
                               ModelViewTypes views) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(MODEL_TABLE, new String[]{"data"},
                            "className = ? AND data NOT NULL",
                            new String[]{className},
                            null, null, null, offset + "," + count);
        List<Model> models = this.readModels(c, db, className);
        c.close();
        db.close();
        // Check if the model have the required fields
        List<String> fields = null;
        if (views != null) {
            fields = views.getAllFieldNames();
        } else {
            fields = new ArrayList<String>();
        }
        if (!fields.contains("id")) { fields.add("id"); }
        if (!fields.contains("rec_name")) { fields.add("rec_name"); }
        for (int i = 0; i < models.size(); i++) {
            Model data = models.get(i);
            for (String field : fields) {
                if (!data.hasAttribute(field)) {
                    models.remove(i);
                    i--;
                    System.out.println("refused by fields");
                    break;
                }
            }
        }
        System.out.println("found " + models.size() + " valid form local cache");
        return models;
    }

    public List<Model> getData(String className, List<Integer> ids,
                               ModelViewTypes views) {
        if (ids == null || ids.size() == 0) {
            return new ArrayList<Model>();
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String in = "";
        for (int id : ids) {
            in += id + ",";
        }
        in = in.substring(0, in.length() - 1);
        Cursor c = db.query(MODEL_TABLE, new String[]{"data"},
                            "className = ? AND id IN (" + in + ")",
                            new String[]{className},
                            null, null, null, null);
        List<Model> models = this.readModels(c, db, className);
        c.close();
        db.close();
        // Check if the model have the required fields
        List<String> fields = null;
        if (views != null) {
            fields = views.getAllFieldNames();
        } else {
            fields = new ArrayList<String>();
        }
        if (!fields.contains("id")) { fields.add("id"); }
        if (!fields.contains("rec_name")) { fields.add("rec_name"); }
        for (int i = 0; i < models.size(); i++) {
            Model data = models.get(i);
            for (String field : fields) {
                if (!data.hasAttribute(field)) {
                    models.remove(i);
                    i--;
                    break;
                }
            }
        }
        return models;
    }

    /** Get the list of id/name as Models for a className */
    public List<Model> list(String className) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor c = db.query(MODEL_TABLE, new String[]{"id", "name"},
                            "className = ?",
                            new String[]{className},
                            null, null, null, null);
        List<Model> models = new ArrayList<Model>();
        while (c.moveToNext()) {
            Model m = new Model(className);
            m.set("id", c.getInt(0));
            m.set("rec_name", c.getString(1));
            models.add(m);
        }
        c.close();
        db.close();
        return models;
    }

    public List<Model> list(String className, List<Integer> ids) {
        if (ids == null || ids.size() == 0) {
            return new ArrayList<Model>();
        }
        SQLiteDatabase db = this.getReadableDatabase();
        String in = "";
        for (int id : ids) {
            in += id + ",";
        }
        in = in.substring(0, in.length() - 1);
        Cursor c = db.query(MODEL_TABLE, new String[]{"id", "name"},
                            "className = ? AND id IN (" + in + ")",
                            new String[]{className},
                            null, null, null, null);
        List<Model> models = new ArrayList<Model>();
        while (c.moveToNext()) {
            Model m = new Model(className);
            m.set("id", c.getInt(0));
            m.set("rec_name", c.getString(1));
            models.add(m);
        }
        c.close();
        db.close();
        return models;
    }
 
    /** Get a limited model (name and id) a model
        (typically for a relationnal field) */
    private Model getRelationnal(String className, int id, SQLiteDatabase db) {
        Cursor c = db.query(MODEL_TABLE, new String[]{"id", "name"},
                            "className = ? and id = ?",
                            new String[]{className, String.valueOf(id)},
                            null, null, null, null);
        if (c.moveToNext()) {
            Model m = new Model(className);
            m.set("id", c.getInt(0));
            m.set("rec_name", c.getString(1));
            c.close();
            return m;
        }
        c.close();
        return null;
    }

    /** Insert data for a whole model class. This removes previous data
     * for the model class. */
    public void storeClassData(String className, List<Model> data) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(MODEL_TABLE, "className = ?", new String[]{className});
        this.storeData(className, data);
        db.close();
        this.setDataCount(className, data.size());
    }

    private void storeRelData(List<Model> rel, long time, SQLiteDatabase db) {
        for (Model m : rel) {
            ContentValues v = new ContentValues();
            v.put("name", m.getString("rec_name"));
            v.put("id", (Integer) m.get("id"));
            v.put("className", m.getClassName());
            v.put("writeTime", time);
            // Store it only if it does not erase a full data
            if (db.update(MODEL_TABLE, v, "id = ? AND className = ? " +
                          "AND data IS NULL",
                          new String[]{m.get("id").toString(), m.getClassName()}
                          ) == 0) {
                // Try to insert, in case there is no data
                Cursor c = db.query(MODEL_TABLE,
                                    new String[]{"id"},
                                    "id = ? AND className = ?",
                                    new String[]{m.get("id").toString(),
                                                 m.getClassName()}, null,
                                    null, null, null);
                if (c.moveToNext()) {
                    // Already there, full data. Keep as is
                } else {            
                    db.insert(MODEL_TABLE, null, v);
                }
                c.close();
            }
        }
    }

    public void storeRelData(String className, List<Model> data) {
        SQLiteDatabase db = this.getWritableDatabase();
        long time = System.currentTimeMillis();
        this.storeRelData(data, time, db);
        db.close();
    }

    /** Add or update data */
    public void storeData(String className, List<Model> data) {
        SQLiteDatabase db = this.getWritableDatabase();
        long time = System.currentTimeMillis();
        for (Model m : data) {
            try {
                Model storeModel = m;
                // Check if a record is present
                long minTime = System.currentTimeMillis() - CACHE_LIFETIME;
                Cursor c = db.query(MODEL_TABLE, new String[]{"data", "id",
                                                              "writeTime"},
                    "id = ? and className = ? and writeTime > ?",
                    new String[]{m.get("id").toString(), className,
                                 String.valueOf(minTime)}, null, null,
                    null, null);
                List<Model> currentdata = this.readModels(c, db, className);
                if (currentdata.size() > 0) {
                    // Merge data with current values
                    Model d = currentdata.get(0);
                    d.merge(m);
                    storeModel = d;
                } else {
                    // Maybe some outdated data, purge them
                    db.delete(MODEL_TABLE, "id = ? and className = ?",
                              new String[]{m.get("id").toString(), className});
                }
                c.close();
                // Insert value
                ContentValues v = new ContentValues();
                v.put("name", m.getString("rec_name"));
                v.put("id", (Integer) m.get("id"));
                v.put("className", className);
                v.put("data", storeModel.toByteArray());
                v.put("writeTime", time);
                // Try to update record
                if (db.update(MODEL_TABLE, v, "id = ? and className = ?",
                              new String[]{storeModel.get("id").toString(), className}
                              ) == 0) {
                    // Record is not present, insert it
                    db.insert(MODEL_TABLE, null, v);
                }
                // Store relationnal fields
                this.storeRelData(storeModel.getRelModels(), time, db);
            } catch (IOException e) {
                Log.e("Tryton", "Unable to convert model to byte[]", e);
            }
        }
        db.close();
    }

    public void storeData(String className, Model data) {
        List<Model> dummy = new ArrayList<Model>();
        dummy.add(data);
        storeData(className, dummy);
    }

    public void deleteData(Model data) {
        // Delete main record
        String className = data.getClassName();
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(MODEL_TABLE, "id = ? AND className = ?",
                  new String[]{data.get("id").toString(), className});
        // Update count
        Cursor c = db.query(COUNT_TABLE, new String[]{"count"},
                            "className = ?", new String[]{className},
                            null, null, null, "1");
        if (c.moveToNext()) {
            int count = c.getInt(0);
            ContentValues v = new ContentValues();
            v.put("count", count - 1);
            db.update(COUNT_TABLE, v, "className = ?",
                      new String[]{className});
            c.close();
        }
        db.close();
        // TODO: mark relational model to be refreshed
    }
}