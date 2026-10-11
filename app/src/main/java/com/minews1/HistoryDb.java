package com.minews1;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HistoryDb extends SQLiteOpenHelper {
    public static class Reading {
        public long ts;
        public float temp, hum, lux, cond;
        Reading(long t,float a,float h,float l,float c){ts=t;temp=a;hum=h;lux=l;cond=c;}
    }
    public HistoryDb(Context c){super(c,"minew_history.db",null,4);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE readings(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, temp REAL, hum REAL, lux REAL, cond REAL, device_id TEXT NOT NULL)");db.execSQL("CREATE INDEX idx_ts ON readings(ts)");db.execSQL("CREATE INDEX idx_device_ts ON readings(device_id,ts)");}
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
        // Flower Care also measures light and soil conductivity; both are nullable for everything else.
        if(oldVersion<4){
            db.execSQL("ALTER TABLE readings ADD COLUMN lux REAL");
            db.execSQL("ALTER TABLE readings ADD COLUMN cond REAL");
        }
    }
    public void add(String deviceId,long ts,float temp,float hum){add(deviceId,ts,temp,hum,Float.NaN,Float.NaN);}
    public void add(String deviceId,long ts,float temp,float hum,float lux,float cond){
        getWritableDatabase().execSQL("INSERT INTO readings(ts,temp,hum,lux,cond,device_id) VALUES(?,?,?,?,?,?)",
            new Object[]{ts, box(temp), box(hum), box(lux), box(cond), deviceId.toUpperCase()});
    }
    /** Записывает импортированный журнал одной транзакцией, пропуская уже известные моменты. */
    public int addAll(String deviceId,List<Reading> rows){
        if(rows.isEmpty())return 0;
        long from=Long.MAX_VALUE,to=Long.MIN_VALUE;
        for(Reading r:rows){from=Math.min(from,r.ts);to=Math.max(to,r.ts);}
        Set<Long> known=timestamps(deviceId,from,to+1);
        SQLiteDatabase db=getWritableDatabase();
        int written=0;
        db.beginTransaction();
        try{
            for(Reading r:rows){
                if(!known.add(r.ts))continue;
                db.execSQL("INSERT INTO readings(ts,temp,hum,lux,cond,device_id) VALUES(?,?,?,?,?,?)",
                    new Object[]{r.ts, box(r.temp), box(r.hum), box(r.lux), box(r.cond), deviceId.toUpperCase()});
                written++;
            }
            db.setTransactionSuccessful();
        }finally{db.endTransaction();}
        return written;
    }
    private Set<Long> timestamps(String deviceId,long from,long to){
        Set<Long> out=new HashSet<>();
        Cursor c=getReadableDatabase().query("readings",new String[]{"ts"},"device_id=? AND ts>=? AND ts<?",new String[]{deviceId.toUpperCase(),Long.toString(from),Long.toString(to)},null,null,null);
        try{while(c.moveToNext())out.add(c.getLong(0));}finally{c.close();}
        return out;
    }
    private static Double box(float v){return Float.isNaN(v)?null:(double)v;}
    public List<Reading> range(String deviceId,long from,long to){
        List<Reading> r=new ArrayList<>();
        Cursor c=getReadableDatabase().query("readings",new String[]{"ts","temp","hum","lux","cond"},"device_id=? AND ts>=? AND ts<?",new String[]{deviceId.toUpperCase(),Long.toString(from),Long.toString(to)},null,null,"ts ASC");
        try{
            while(c.moveToNext()){
                r.add(new Reading(c.getLong(0),val(c,1),val(c,2),val(c,3),val(c,4)));
            }
        }finally{c.close();}
        return r;
    }
    private static float val(Cursor c,int i){return c.isNull(i)?Float.NaN:c.getFloat(i);}
    /** Пустая запись журнала, которую заполняет импорт. */
    public static Reading reading(long ts,float temp,float hum,float lux,float cond){return new Reading(ts,temp,hum,lux,cond);}
}
