package com.minews1;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;

public class HistoryDb extends SQLiteOpenHelper {
    public static class Reading { public long ts; public float temp, hum; Reading(long t,float a,float h){ts=t;temp=a;hum=h;} }
    public HistoryDb(Context c){super(c,"minew_history.db",null,3);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, temp REAL, hum REAL, device_id TEXT NOT NULL)");db.execSQL("CREATE INDEX idx_ts ON readings(ts)");db.execSQL("CREATE INDEX idx_device_ts ON readings(device_id,ts)");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){
        if(oldVersion<2){db.execSQL("ALTER TABLE readings ADD COLUMN device_id TEXT NOT NULL DEFAULT '78:05:41:06:91:BA'");db.execSQL("CREATE INDEX IF NOT EXISTS idx_device_ts ON readings(device_id,ts)");}
        // Temperature-only sensors have no humidity to store, so both value columns become nullable.
        if(oldVersion<3){
            db.execSQL("ALTER TABLE readings RENAME TO readings_old");
            db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, temp REAL, hum REAL, device_id TEXT NOT NULL)");
            db.execSQL("INSERT INTO readings(id,ts,temp,hum,device_id) SELECT id,ts,temp,hum,device_id FROM readings_old");
            db.execSQL("DROP TABLE readings_old");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_ts ON readings(ts)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_device_ts ON readings(device_id,ts)");
        }
    }
    public void add(String deviceId,long ts,float temp,float hum){
        getWritableDatabase().execSQL("INSERT INTO readings(ts,temp,hum,device_id) VALUES(?,?,?,?)",
            new Object[]{ts, Float.isNaN(temp)?null:temp, Float.isNaN(hum)?null:hum, deviceId.toUpperCase()});
    }
    public List<Reading> range(String deviceId,long from,long to){
        List<Reading> r=new ArrayList<>();
        Cursor c=getReadableDatabase().query("readings",new String[]{"ts","temp","hum"},"device_id=? AND ts>=? AND ts<?",new String[]{deviceId.toUpperCase(),Long.toString(from),Long.toString(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                float temp=c.isNull(1)?Float.NaN:c.getFloat(1);
                float hum=c.isNull(2)?Float.NaN:c.getFloat(2);
                r.add(new Reading(c.getLong(0),temp,hum));
            }
        }finally{c.close();}
        return r;
    }
}
